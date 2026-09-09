#!/bin/bash
# ============================================
# BulC Homepage 배포 스크립트 (JAR 업로드 방식)
#
# 사용법: bash deploy.sh [--skip-migrations]
# 로컬에서 실행합니다.
#
# 서버 available 메모리가 300MB 미만이라 서버에서 gradle 빌드를 하면 안 됩니다.
# JAR 을 로컬에서 빌드해 업로드하는 이유입니다.
# ============================================

set -e

# ---- 설정 ----
SSH_TARGET="${BULC_SSH_TARGET:-}"                      # 설정 시 ssh config 별칭 사용 (예: bulc-prod)
SSH_KEY="${BULC_SSH_KEY:-$HOME/.ssh/oracle_homepage.pem}"
SERVER="${BULC_SERVER:-ubuntu@168.107.19.139}"
REMOTE_DIR="~/BulCHomepage"
LOCAL_BACKEND="backend"
COMPOSE="docker compose -f docker-compose.prod.yml -f docker-compose.prebuilt.yml"

SKIP_MIGRATIONS=0
if [ "${1:-}" = "--skip-migrations" ]; then
  SKIP_MIGRATIONS=1
fi

ssh_run() {
  if [ -n "$SSH_TARGET" ]; then
    ssh -o BatchMode=yes -o ConnectTimeout=15 "$SSH_TARGET" "$@"
  else
    ssh -o BatchMode=yes -o ConnectTimeout=15 -i "$SSH_KEY" "$SERVER" "$@"
  fi
}

scp_up() {
  if [ -n "$SSH_TARGET" ]; then
    scp -o BatchMode=yes "$1" "$SSH_TARGET:$2"
  else
    scp -o BatchMode=yes -i "$SSH_KEY" "$1" "$SERVER:$2"
  fi
}

# 배포 실패 시에도 서버의 .dockerignore 를 원래대로 되돌린다
restore_dockerignore() {
  ssh_run "cd $REMOTE_DIR/backend && \
    if [ -f .dockerignore.original ]; then \
      cp .dockerignore.original .dockerignore && rm -f .dockerignore.original; \
    fi" || true
}

health_check() {
  # $1 = 최대 시도 횟수. 이 서버는 기동에 약 95초(19회) 걸리는 것이 정상 범위다.
  local tries="$1"
  for i in $(seq 1 "$tries"); do
    if ssh_run "curl -sf http://localhost:8080/api/health" > /dev/null 2>&1; then
      return 0
    fi
    echo "  대기 중... ($i/$tries)"
    sleep 5
  done
  return 1
}

echo "=========================================="
echo " BulC Homepage 배포 시작"
echo "=========================================="

# ---- Step 1: yml 중복 키 검사 ----
echo ""
echo "[1/9] application.yml 검증..."
DUPES=$(grep -c "^server:" $LOCAL_BACKEND/src/main/resources/application.yml)
if [ "$DUPES" -gt 1 ]; then
  echo "ERROR: application.yml에 중복 server: 키 발견!"
  exit 1
fi
echo "  OK — 중복 키 없음"

# ---- Step 2: 로컬 빌드 ----
echo ""
echo "[2/9] 로컬에서 JAR 빌드 중..."
cd $LOCAL_BACKEND
./gradlew bootJar -x test --quiet
JAR_FILE=$(ls build/libs/*.jar 2>/dev/null | head -1)
if [ -z "$JAR_FILE" ]; then
  echo "ERROR: JAR 파일을 찾을 수 없습니다!"
  exit 1
fi
JAR_SIZE=$(du -h "$JAR_FILE" | cut -f1)
echo "  OK — $JAR_FILE ($JAR_SIZE)"
cd ..

# ---- Step 3: 서버에 코드 업데이트 (git pull) ----
# 마이그레이션 파일을 서버로 들여오는 단계이므로 Step 4 보다 반드시 먼저 와야 한다.
echo ""
echo "[3/9] 서버 코드 업데이트..."
ssh_run "cd $REMOTE_DIR && git pull origin main" 2>&1 | tail -3
echo "  OK"

# ---- Step 4: DB 마이그레이션 ----
# ddl-auto: validate 이므로 스키마가 엔티티와 어긋나면 백엔드가 기동 자체를 실패한다.
# 반드시 컨테이너 재시작(Step 7) 보다 먼저 실행한다.
echo ""
if [ "$SKIP_MIGRATIONS" -eq 1 ]; then
  echo "[4/9] DB 마이그레이션 — --skip-migrations 지정으로 건너뜀"
else
  echo "[4/9] DB 마이그레이션 확인·적용..."
  BULC_SSH_TARGET="$SSH_TARGET" BULC_SSH_KEY="$SSH_KEY" BULC_SERVER="$SERVER" \
    bash scripts/db-migrate.sh
  echo "  OK"
fi

# ---- Step 5: 기존 JAR 백업 (롤백용) ----
echo ""
echo "[5/9] 서버의 현재 JAR 백업..."
ssh_run "cd $REMOTE_DIR/backend && \
  mkdir -p .deploy-backup build/libs && \
  CUR=\$(ls -1t build/libs/*.jar 2>/dev/null | head -1); \
  if [ -n \"\$CUR\" ]; then rm -f .deploy-backup/*.jar; cp \"\$CUR\" .deploy-backup/; echo \"  백업: \$(basename \$CUR)\"; \
  else echo '  기존 JAR 없음 — 첫 배포로 간주(롤백 불가)'; fi"

# ---- Step 6: JAR 업로드 ----
echo ""
echo "[6/9] JAR 파일 서버 전송 중..."
scp_up "$LOCAL_BACKEND/$JAR_FILE" "$REMOTE_DIR/backend/$JAR_FILE"
echo "  OK — 전송 완료"

# ---- Step 7: .dockerignore 교체 + 컨테이너 재빌드 ----
echo ""
echo "[7/9] 컨테이너 재빌드·재시작..."
ssh_run "cd $REMOTE_DIR/backend && \
  if [ ! -f .dockerignore.original ]; then cp .dockerignore .dockerignore.original; fi && \
  cp .dockerignore.prebuilt .dockerignore"
ssh_run "cd $REMOTE_DIR && $COMPOSE down backend && $COMPOSE up -d --build backend"
echo "  OK — 컨테이너 시작됨"

# ---- Step 8: 헬스 체크 ----
echo ""
echo "[8/9] 헬스 체크 대기..."
if health_check 30; then
  restore_dockerignore
  echo "  OK — 서버 정상 가동!"
  echo ""
  echo "=========================================="
  echo " 배포 완료!"
  echo "=========================================="
  exit 0
fi

# ---- Step 9: 롤백 ----
echo ""
echo "[9/9] 헬스 체크 실패 — 이전 JAR 로 롤백합니다."
ssh_run "cd $REMOTE_DIR && docker logs bulc-backend-prod --tail 40" 2>&1 | sed 's/^/    /' || true

PREV=$(ssh_run "ls -1 $REMOTE_DIR/backend/.deploy-backup/*.jar 2>/dev/null | head -1" || true)
if [ -z "$PREV" ]; then
  restore_dockerignore
  echo ""
  echo "ERROR: 백업 JAR 이 없어 롤백할 수 없습니다. 수동 조치가 필요합니다."
  exit 1
fi

echo "  이전 JAR 복원 중: $(basename "$PREV")"
ssh_run "cd $REMOTE_DIR/backend && rm -f build/libs/*.jar && cp .deploy-backup/*.jar build/libs/"
ssh_run "cd $REMOTE_DIR && $COMPOSE down backend && $COMPOSE up -d --build backend"

echo "  롤백 후 헬스 체크..."
if health_check 30; then
  restore_dockerignore
  echo ""
  echo "=========================================="
  echo " 롤백 완료 — 이전 버전으로 복구되었습니다."
  echo " 배포는 실패했습니다. 원인 확인 후 재시도하세요."
  echo "=========================================="
  exit 1
fi

restore_dockerignore
echo ""
echo "ERROR: 롤백 후에도 헬스 체크 실패 — 즉시 수동 확인이 필요합니다."
echo "  ssh $SERVER 'docker logs bulc-backend-prod --tail 50'"
exit 1
