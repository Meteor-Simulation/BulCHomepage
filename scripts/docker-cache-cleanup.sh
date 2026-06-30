#!/usr/bin/env bash
#
# 도커 빌드 캐시 주기 정리 (MDP-629 — 운영 디스크 관리)
#
# 반복 `docker compose up --build` 배포로 BuildKit 빌드 캐시가 누적되어
# 45GB VM 디스크를 압박하는 문제를 막기 위한 정기 정리 스크립트.
#
# 정책: 최근 7일(168h) 캐시는 보존하여 정상 배포의 빌드 속도 이점은 유지하고,
#       그보다 오래된 누적 캐시만 제거한다.
#
# 설치: 서버 crontab 에 매주 월요일 06:00 실행으로 등록
#   0 6 * * 1 /bin/bash /home/ubuntu/BulCHomepage/scripts/docker-cache-cleanup.sh
#
# 영향: 실행 중 컨테이너 / 사용 중 이미지 / DB 볼륨 / 업로드에 영향 없음(무중단).
#       단, 정리 직후 첫 배포는 콜드 빌드로 다소 느려질 수 있음.
#
# 모드:
#   (인자 없음)   weekly  — `--filter until=168h` (7일 보존). 정기 cron 용 기본값.
#   emergency     긴급    — `-af` (사용 중이 아닌 모든 빌드캐시 제거). 디스크 임계 초과 시
#                          health-check.sh 가 호출하여 최대한 공간 확보.
#
set -uo pipefail

# cron 의 최소 PATH 에서도 docker 를 찾도록 보강
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

LOG="${HOME}/docker-cache-cleanup.log"

MODE="${1:-weekly}"
if [ "$MODE" = "emergency" ]; then
  PRUNE_ARGS="-af"
  LABEL="emergency (-af, 전체 미사용 빌드캐시)"
else
  PRUNE_ARGS="-f --filter until=168h"
  LABEL="weekly (until=168h, 7일 보존)"
fi

{
  echo "==== $(date '+%Y-%m-%d %H:%M:%S') docker builder prune [${LABEL}] 시작 ===="
  echo "[before] $(df -h / | tail -1)"
  docker builder prune $PRUNE_ARGS
  echo "[after]  $(df -h / | tail -1)"
  echo "==== 완료 ===="
  echo ""
} >> "$LOG" 2>&1
