#!/bin/bash
# ============================================
# BulC Homepage 배포 스크립트 (JAR 업로드 방식)
#
# 사용법: bash deploy.sh
# 로컬에서 실행합니다.
# ============================================

set -e

# ---- 설정 ----
SERVER="ubuntu@168.107.19.139"
SSH_KEY="$HOME/.ssh/oracle_homepage.pem"
REMOTE_DIR="~/BulCHomepage"
LOCAL_BACKEND="backend"

echo "=========================================="
echo " BulC Homepage 배포 시작"
echo "=========================================="

# ---- Step 1: yml 중복 키 검사 ----
echo ""
echo "[1/7] application.yml 검증..."
DUPES=$(grep -c "^server:" $LOCAL_BACKEND/src/main/resources/application.yml)
if [ "$DUPES" -gt 1 ]; then
  echo "ERROR: application.yml에 중복 server: 키 발견!"
  exit 1
fi
echo "  OK — 중복 키 없음"

# ---- Step 2: 로컬 빌드 ----
echo ""
echo "[2/7] 로컬에서 JAR 빌드 중..."
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
echo ""
echo "[3/7] 서버 코드 업데이트..."
ssh -i $SSH_KEY $SERVER "cd $REMOTE_DIR && git pull origin main" 2>&1 | tail -3
echo "  OK"

# ---- Step 4: JAR 업로드 ----
echo ""
echo "[4/7] JAR 파일 서버 전송 중..."
ssh -i $SSH_KEY $SERVER "mkdir -p $REMOTE_DIR/backend/build/libs"
scp -i $SSH_KEY $LOCAL_BACKEND/$JAR_FILE $SERVER:$REMOTE_DIR/backend/$JAR_FILE
echo "  OK — 전송 완료"

# ---- Step 5: .dockerignore 교체 (prebuilt용) ----
echo ""
echo "[5/7] .dockerignore 교체 (prebuilt용)..."
ssh -i $SSH_KEY $SERVER "cd $REMOTE_DIR/backend && \
  cp .dockerignore .dockerignore.original && \
  cp .dockerignore.prebuilt .dockerignore"
echo "  OK"

# ---- Step 6: 서버에서 Docker 빌드 + 재시작 ----
echo ""
echo "[6/7] 서버에서 컨테이너 재시작..."
ssh -i $SSH_KEY $SERVER "cd $REMOTE_DIR && \
  docker compose -f docker-compose.prod.yml -f docker-compose.prebuilt.yml down backend && \
  docker compose -f docker-compose.prod.yml -f docker-compose.prebuilt.yml up -d --build backend"

# .dockerignore 원복
ssh -i $SSH_KEY $SERVER "cd $REMOTE_DIR/backend && \
  cp .dockerignore.original .dockerignore && \
  rm -f .dockerignore.original"
echo "  OK — 컨테이너 시작됨, .dockerignore 원복 완료"

# ---- Step 7: 헬스 체크 ----
echo ""
echo "[7/7] 헬스 체크 대기..."
for i in $(seq 1 30); do
  if ssh -i $SSH_KEY $SERVER "curl -sf http://localhost:8080/api/health" > /dev/null 2>&1; then
    echo "  OK — 서버 정상 가동!"
    echo ""
    echo "=========================================="
    echo " 배포 완료!"
    echo "=========================================="
    exit 0
  fi
  echo "  대기 중... ($i/30)"
  sleep 5
done

echo "ERROR: 헬스 체크 실패 — 서버 로그를 확인하세요"
echo "  ssh -i $SSH_KEY $SERVER 'docker logs bulc-backend-prod --tail 30'"
exit 1
