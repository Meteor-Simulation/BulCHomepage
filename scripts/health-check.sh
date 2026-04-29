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
ALERT_EMAIL="${HEALTH_ALERT_EMAIL:-juwon@msimul.com}"
SLACK_WEBHOOK="${HEALTH_SLACK_WEBHOOK}"
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

  # 슬랙 알림 (백엔드 상태와 무관하게 항상 발송 가능)
  SLACK_MSG=":rotating_light: *[BulC] 서버 이상 감지*\n\n$(echo -e "$ISSUES")\n체크 시각: $TIMESTAMP"
  curl -sf -X POST "$SLACK_WEBHOOK" \
    -H "Content-Type: application/json" \
    -d "{\"text\":\"$SLACK_MSG\"}" >> "$LOG_FILE" 2>&1 || true

  # 백엔드가 죽어서 이메일 API 호출 실패하는 경우
  if [ "$BACKEND_STATUS" != "200" ]; then
    echo "[$TIMESTAMP] 백엔드 다운 — 슬랙으로만 알림 발송" >> "$LOG_FILE"
  fi
else
  echo "[$TIMESTAMP] OK" >> "$LOG_FILE"

  # 오전 10시 30분에 정상 안내 발송
  CURRENT_HOUR=$(date '+%H')
  CURRENT_MIN=$(date '+%M')
  if [ "$CURRENT_HOUR" = "10" ] && [ "$CURRENT_MIN" -ge "25" ]; then
    SUBJECT="[BulC] 서버 정상 가동 중 - $TIMESTAMP"
    BODY="BulC Homepage 헬스 체크 결과 모든 항목이 정상입니다.\n\n- 백엔드: OK\n- DB: OK\n- 프론트엔드: OK\n- 디스크: ${DISK_USAGE}%\n- Swap: ${SWAP_PCT:-0}%\n\n체크 시각: $TIMESTAMP"

    curl -sf -X POST "$BACKEND_URL/api/health/alert" \
      -H "Content-Type: application/json" \
      -d "{\"to\":\"$ALERT_EMAIL\",\"subject\":\"$SUBJECT\",\"body\":\"$BODY\"}" \
      >> "$LOG_FILE" 2>&1 || true

    # 슬랙에도 정상 안내
    SLACK_OK=":white_check_mark: *[BulC] 서버 정상 가동 중*\n\n백엔드: OK / DB: OK / 프론트: OK\n디스크: ${DISK_USAGE}% / Swap: ${SWAP_PCT:-0}%\n체크 시각: $TIMESTAMP"
    curl -sf -X POST "$SLACK_WEBHOOK" \
      -H "Content-Type: application/json" \
      -d "{\"text\":\"$SLACK_OK\"}" >> "$LOG_FILE" 2>&1 || true

    echo "[$TIMESTAMP] 정상 안내 메일+슬랙 발송" >> "$LOG_FILE"
  fi
fi
