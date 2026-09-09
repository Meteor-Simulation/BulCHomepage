#!/bin/bash
# ============================================
# BulC Homepage DB 마이그레이션 러너
#
# database/migrations/V*.sql 중 운영 DB에 아직 적용되지 않은 것을 파일명 순서대로 적용하고,
# 적용 이력을 운영 DB 의 schema_migrations 테이블에 기록한다.
# (Flyway/Liquibase 미도입 프로젝트라 이 스크립트가 이력 관리 주체다)
#
# 사용법 — 로컬에서 실행:
#   bash scripts/db-migrate.sh --status     # 적용/미적용 목록만 출력 (DB 변경 없음)
#   bash scripts/db-migrate.sh --baseline   # 최초 1회: 기존 파일을 '적용됨'으로 등록만 (SQL 실행 안 함)
#   bash scripts/db-migrate.sh              # 미적용분 적용
#
# 주의:
#   - 서버의 database/migrations 를 읽으므로 `git pull` 이 선행되어야 한다.
#   - deploy.sh 가 [4/9] 단계에서 이 스크립트를 자동 호출한다. 단독 실행은 점검·복구용.
#   - ddl-auto: validate 이므로 백엔드 재시작보다 반드시 먼저 실행되어야 한다.
# ============================================
set -euo pipefail

# ---- 설정 (환경변수로 덮어쓸 수 있음) ----
SSH_TARGET="${BULC_SSH_TARGET:-}"                      # 설정 시 ssh config 별칭 사용 (예: bulc-prod)
SSH_KEY="${BULC_SSH_KEY:-$HOME/.ssh/oracle_homepage.pem}"
SERVER="${BULC_SERVER:-ubuntu@168.107.19.139}"
REMOTE_DIR="${BULC_REMOTE_DIR:-BulCHomepage}"
DB_CONTAINER="${BULC_DB_CONTAINER:-bulc-db-prod}"
DB_USER="${BULC_DB_USER:-bulc_prod_user}"
DB_NAME="${BULC_DB_NAME:-bulc_homepage_db}"

MODE="apply"
case "${1:-}" in
  --status)   MODE="status" ;;
  --baseline) MODE="baseline" ;;
  "")         MODE="apply" ;;
  *)
    echo "사용법: bash scripts/db-migrate.sh [--status|--baseline]" >&2
    exit 2
    ;;
esac

ssh_run() {
  if [ -n "$SSH_TARGET" ]; then
    ssh -o BatchMode=yes -o ConnectTimeout=15 "$SSH_TARGET" "$@"
  else
    ssh -o BatchMode=yes -o ConnectTimeout=15 -i "$SSH_KEY" "$SERVER" "$@"
  fi
}

# 서버에서 실행할 스크립트: 헤더(로컬 값 주입) + 본문(로컬 확장 금지)
REMOTE_HEADER=$(cat <<EOF
REPO_DIR="\$HOME/$REMOTE_DIR"
DB_CONTAINER="$DB_CONTAINER"
DB_USER="$DB_USER"
DB_NAME="$DB_NAME"
MODE="$MODE"
FORCE_BASELINE="${FORCE_BASELINE:-0}"
EOF
)

REMOTE_BODY=$(cat <<'REMOTE'
set -euo pipefail
cd "$REPO_DIR"
MIG_DIR="database/migrations"

# 이 스크립트 자체가 `bash -s` 의 stdin 으로 들어오므로, stdin 을 읽는 명령이 있으면
# 나머지 스크립트를 먹어버린다. 조회용 psql 은 -i 없이 실행하고 stdin 도 끊는다.
# (SQL 파일 적용만 stdin 리다이렉트를 명시적으로 쓴다)
psqlq() {
  docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -tAq "$@" < /dev/null
}

# ---- 이력 테이블 보장 ----
# CHAR(1) 금지 규칙에 따라 전부 VARCHAR. JPA 엔티티가 아니므로 validate 대상은 아니다.
psqlq -c "SET client_min_messages = warning;
CREATE TABLE IF NOT EXISTS schema_migrations (
  version      VARCHAR(255) PRIMARY KEY,
  checksum     VARCHAR(64)  NOT NULL,
  applied_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  applied_by   VARCHAR(100) NOT NULL DEFAULT current_user,
  execution_ms INTEGER
);" > /dev/null

LEDGER_COUNT=$(psqlq -c "SELECT count(*) FROM schema_migrations;")

FILES=$(ls -1 "$MIG_DIR"/V*.sql 2>/dev/null | LC_ALL=C sort || true)
if [ -z "$FILES" ]; then
  echo "  마이그레이션 파일 없음: $MIG_DIR"
  exit 0
fi

# ---- 적용 여부 · 체크섬 드리프트 판정 ----
PENDING=()
DRIFT=0
while IFS= read -r f; do
  [ -n "$f" ] || continue
  ver=$(basename "$f")
  sum=$(sha256sum "$f" | cut -d' ' -f1)
  dbsum=$(psqlq -c "SELECT checksum FROM schema_migrations WHERE version = '$ver';")
  if [ -z "$dbsum" ]; then
    PENDING+=("$ver")
  elif [ "$dbsum" != "$sum" ]; then
    echo "  ! $ver — 적용 후 파일이 변경됨 (checksum 불일치)"
    DRIFT=1
  fi
done <<< "$FILES"

TOTAL=$(echo "$FILES" | wc -l | tr -d ' ')
echo "  파일 $TOTAL개 · 적용됨 $LEDGER_COUNT건 · 미적용 ${#PENDING[@]}건"

if [ "$DRIFT" -ne 0 ]; then
  echo "  ERROR: 이미 적용된 마이그레이션 파일이 수정되었습니다."
  echo "         적용된 SQL 과 레포 내용이 어긋난 상태이므로 자동 진행하지 않습니다."
  exit 1
fi

# ---- status ----
if [ "$MODE" = "status" ]; then
  if [ "${#PENDING[@]}" -gt 0 ]; then
    echo "  미적용 목록:"
    for v in "${PENDING[@]}"; do echo "    - $v"; done
  fi
  exit 0
fi

# ---- baseline ----
if [ "$MODE" = "baseline" ]; then
  if [ "$LEDGER_COUNT" != "0" ] && [ "$FORCE_BASELINE" != "1" ]; then
    echo "  ERROR: schema_migrations 에 이미 이력이 있습니다 ($LEDGER_COUNT건)."
    echo "         baseline 은 최초 1회만 사용합니다. 강제하려면 FORCE_BASELINE=1 로 실행하세요."
    exit 1
  fi
  if [ "${#PENDING[@]}" -eq 0 ]; then
    echo "  등록할 항목이 없습니다."
    exit 0
  fi
  echo "  baseline 등록 (SQL 실행 없이 '적용됨' 으로만 기록):"
  for v in "${PENDING[@]}"; do
    sum=$(sha256sum "$MIG_DIR/$v" | cut -d' ' -f1)
    psqlq -c "INSERT INTO schema_migrations(version, checksum, execution_ms) VALUES ('$v', '$sum', 0);" > /dev/null
    echo "    + $v"
  done
  echo "  baseline 완료 — 이후 신규 파일만 적용됩니다."
  exit 0
fi

# ---- apply ----
if [ "${#PENDING[@]}" -eq 0 ]; then
  echo "  적용할 마이그레이션 없음 — 건너뜁니다."
  exit 0
fi

if [ "$LEDGER_COUNT" = "0" ]; then
  echo "  ERROR: 이력 테이블이 비어 있는데 미적용 파일이 ${#PENDING[@]}건입니다."
  echo "         이미 운영에 반영된 파일을 재실행하면 실패하거나 데이터가 손상됩니다."
  echo "         최초 1회 'bash scripts/db-migrate.sh --baseline' 을 먼저 실행하세요."
  exit 1
fi

for v in "${PENDING[@]}"; do
  echo "  적용 중: $v"
  sum=$(sha256sum "$MIG_DIR/$v" | cut -d' ' -f1)
  # CREATE INDEX CONCURRENTLY 등은 트랜잭션 안에서 실행할 수 없다
  if grep -qi "CONCURRENTLY" "$MIG_DIR/$v"; then
    TXFLAG=""
    echo "    (CONCURRENTLY 포함 → 단일 트랜잭션 미적용)"
  else
    TXFLAG="--single-transaction"
  fi
  START=$(date +%s%N)
  docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" \
    -v ON_ERROR_STOP=1 $TXFLAG -q < "$MIG_DIR/$v"
  END=$(date +%s%N)
  MS=$(( (END - START) / 1000000 ))
  psqlq -c "INSERT INTO schema_migrations(version, checksum, execution_ms) VALUES ('$v', '$sum', $MS);" > /dev/null
  echo "    OK — ${MS}ms"
done

echo "  마이그레이션 ${#PENDING[@]}건 적용 완료"
REMOTE
)

printf '%s\n%s\n' "$REMOTE_HEADER" "$REMOTE_BODY" | ssh_run 'bash -s'
