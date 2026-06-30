#!/bin/bash
# ============================================
# BulC Homepage 헬스 체크 스크립트
#
# 한국 시간 기준으로 시각 비교 (서버 TZ가 UTC라도 KST로 강제)
export TZ=Asia/Seoul
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

# ---- 4. 디스크 체크 (90% 초과 시 빌드캐시 긴급 자동 정리) ----
DISK_THRESHOLD=90
CLEANUP_SCRIPT="/home/ubuntu/BulCHomepage/scripts/docker-cache-cleanup.sh"
DISK_USAGE=$(df / | tail -1 | awk '{print $5}' | tr -d '%')
CLEANUP_NOTE=""
if [ "$DISK_USAGE" -gt "$DISK_THRESHOLD" ]; then
  DISK_BEFORE=$DISK_USAGE
  echo "[$TIMESTAMP] 디스크 ${DISK_BEFORE}% 초과 — 빌드캐시 긴급 정리(emergency) 실행" >> "$LOG_FILE"
  bash "$CLEANUP_SCRIPT" emergency >> "$LOG_FILE" 2>&1 || true
  DISK_USAGE=$(df / | tail -1 | awk '{print $5}' | tr -d '%')   # 정리 후 재측정
  echo "[$TIMESTAMP] 정리 후 디스크 ${DISK_USAGE}%" >> "$LOG_FILE"
  CLEANUP_NOTE="디스크 자동 정리: ${DISK_BEFORE}% → ${DISK_USAGE}%"
  if [ "$DISK_USAGE" -gt "$DISK_THRESHOLD" ]; then
    # 자동 정리로도 회복 못 함 → 경고 (수동 조치 필요)
    ISSUES="${ISSUES}[디스크] 사용량 ${DISK_BEFORE}% → 자동정리 후에도 ${DISK_USAGE}% (90% 초과, 추가 조치 필요)\n"
  fi
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

  # KST 10시 / 22시 정각에 정상 안내 발송 (1일 2회)
  CURRENT_HOUR=$(date '+%H')
  if [ "$CURRENT_HOUR" = "10" ] || [ "$CURRENT_HOUR" = "22" ]; then
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

    echo "[$TIMESTAMP] 정상 안내 메일+슬랙 발송 (KST ${CURRENT_HOUR}시)" >> "$LOG_FILE"
  fi
fi

# ---- 디스크 자동 정리가 실행되고 회복된 경우: 정보성 알림 1회 ----
# (회복 못 한 경우는 위 ISSUES 경고로 이미 발송되므로 중복 발송하지 않음)
if [ -n "$CLEANUP_NOTE" ] && [ "$DISK_USAGE" -le "$DISK_THRESHOLD" ]; then
  INFO_SUBJECT="[BulC] 디스크 자동 정리 수행 - $TIMESTAMP"
  INFO_BODY="디스크 사용량이 임계치(${DISK_THRESHOLD}%)를 초과하여 빌드캐시를 자동 정리했습니다.\n\n${CLEANUP_NOTE}\n체크 시각: $TIMESTAMP\n서버: $(hostname)"
  curl -sf -X POST "$BACKEND_URL/api/health/alert" \
    -H "Content-Type: application/json" \
    -d "{\"to\":\"$ALERT_EMAIL\",\"subject\":\"$INFO_SUBJECT\",\"body\":\"$INFO_BODY\"}" \
    >> "$LOG_FILE" 2>&1 || true
  curl -sf -X POST "$SLACK_WEBHOOK" \
    -H "Content-Type: application/json" \
    -d "{\"text\":\":broom: *[BulC] 디스크 자동 정리 수행*\n\n${CLEANUP_NOTE}\n체크 시각: $TIMESTAMP\"}" \
    >> "$LOG_FILE" 2>&1 || true
  echo "[$TIMESTAMP] 디스크 자동정리 정보 알림 발송 ($CLEANUP_NOTE)" >> "$LOG_FILE"
fi
