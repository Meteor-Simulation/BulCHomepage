#!/bin/bash
# ============================================
# BulC Homepage 헬스 체크 스크립트
#
# cron 설정: 매 시간 00분 실행
#   0 * * * * /home/ubuntu/BulCHomepage/scripts/health-check.sh
#
# 체크 항목:
#   1. 백엔드 (Spring Boot /api/health)
#   2. DB (PostgreSQL pg_isready)
#   3. 프론트엔드 (Cloudflare Pages)
#   4. 디스크 사용량 (90% 초과 시 경고)
#   5. 메모리 사용량 (Swap 80% 초과 시 경고)
#
# 이상 감지 시 백엔드 이메일 API로 알림 발송
# ============================================

LOG_FILE="/var/log/bulc-health-check.log"
BACKEND_URL="http://localhost:8080"
FRONTEND_URL="https://bulc.msimul.com"
ALERT_EMAIL="juwon@msimul.com"
TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

ISSUES=""

# ---- 1. 백엔드 체크 ----
BACKEND_STATUS=$(curl -sf -o /dev/null -w "%{http_code}" "$BACKEND_URL/api/health" 2>/dev/null)
if [ "$BACKEND_STATUS" != "200" ]; then
  ISSUES="${ISSUES}[백엔드] 응답 없음 (HTTP $BACKEND_STATUS)\n"
fi

# ---- 2. DB 체크 ----
DB_STATUS=$(docker exec bulc-db-prod pg_isready -U bulc_prod_user -d bulc_homepage_db 2>/dev/null)
if [ $? -ne 0 ]; then
  ISSUES="${ISSUES}[DB] PostgreSQL 응답 없음\n"
fi

# ---- 3. 프론트엔드 체크 ----
FRONTEND_STATUS=$(curl -sf -o /dev/null -w "%{http_code}" "$FRONTEND_URL" --max-time 10 2>/dev/null)
if [ "$FRONTEND_STATUS" != "200" ]; then
  ISSUES="${ISSUES}[프론트엔드] 응답 없음 (HTTP $FRONTEND_STATUS)\n"
fi

# ---- 4. 디스크 체크 ----
DISK_USAGE=$(df / | tail -1 | awk '{print $5}' | tr -d '%')
if [ "$DISK_USAGE" -gt 90 ]; then
  ISSUES="${ISSUES}[디스크] 사용량 ${DISK_USAGE}% (90% 초과)\n"
fi

# ---- 5. 메모리(Swap) 체크 ----
SWAP_TOTAL=$(free | grep Swap | awk '{print $2}')
SWAP_USED=$(free | grep Swap | awk '{print $3}')
if [ "$SWAP_TOTAL" -gt 0 ]; then
  SWAP_PCT=$((SWAP_USED * 100 / SWAP_TOTAL))
  if [ "$SWAP_PCT" -gt 80 ]; then
    ISSUES="${ISSUES}[메모리] Swap 사용량 ${SWAP_PCT}% (80% 초과)\n"
  fi
fi

# ---- 결과 처리 ----
if [ -n "$ISSUES" ]; then
  # 이상 감지 — 이메일 발송
  echo "[$TIMESTAMP] 이상 감지:" >> "$LOG_FILE"
  echo -e "$ISSUES" >> "$LOG_FILE"

  # 백엔드 이메일 API로 알림 발송
  SUBJECT="[BulC] 서버 이상 감지 - $TIMESTAMP"
  BODY="BulC Homepage 헬스 체크에서 이상이 감지되었습니다.\n\n$(echo -e "$ISSUES")\n체크 시각: $TIMESTAMP\n서버: $(hostname)"

  curl -sf -X POST "$BACKEND_URL/api/health/alert" \
    -H "Content-Type: application/json" \
    -d "{\"to\":\"$ALERT_EMAIL\",\"subject\":\"$SUBJECT\",\"body\":\"$BODY\"}" \
    >> "$LOG_FILE" 2>&1 || true

  # 백엔드가 죽어서 API 호출 실패하는 경우 로그에 기록
  if [ "$BACKEND_STATUS" != "200" ]; then
    echo "[$TIMESTAMP] 백엔드 다운으로 이메일 API 호출 불가 — 로그에만 기록" >> "$LOG_FILE"
  fi
else
  echo "[$TIMESTAMP] OK" >> "$LOG_FILE"
fi
