#!/usr/bin/env bash
#
# systemd journal 월 단위 정리 (MDP-845 — 운영 로그 관리)
#
# 운영 서버 /var/log 1.9GB 중 1.8GB(95%)가 systemd journal 이었다.
# journald.conf 가 전 항목 기본값이라 상한이 없어 2026-03 부터 6개월치가 그대로 쌓였고,
# 내용의 91%는 SSH 무차별 대입 시도 기록이다(7일 기준 sshd 36,256건 중 공격 12,421줄).
#
# 정책: 한 달치만 남기고 그보다 오래된 journal 파일을 매달 1일에 제거한다.
#
# 전제: journald.conf 에 MaxFileSec=1day 가 설정되어 있어야 한다.
#       journal vacuum 은 개별 로그 항목이 아니라 **파일 단위**로 삭제하므로,
#       파일 하나가 여러 주에 걸치면 그 파일의 마지막 항목이 늙을 때까지 지워지지 않는다.
#       날짜별로 파일을 끊어야 월 단위 정리가 의도대로 떨어진다.
#
# 설치: 서버 crontab 에 매달 1일 05:00 실행으로 등록
#   0 5 1 * * /bin/bash /home/ubuntu/BulCHomepage/scripts/log-cleanup.sh
#
# 영향: 삭제되는 것은 보존 기간을 넘긴 시스템 로그뿐이다. 서비스 중단 없음.
#       nginx 로그(logrotate, 14일) · auth.log · btmp 는 각자 이미 관리되므로 건드리지 않는다.
#
# 모드:
#   (인자 없음)   1개월 보존으로 정리. 정기 cron 용 기본값
#   --dry-run     삭제 없이 현재 상태와 정리 대상만 출력
#   --keep <기간> 보존 기간 지정 (journalctl 형식: 1month, 2weeks, 90d ...)
#
set -uo pipefail

# cron 의 최소 PATH 에서도 journalctl 을 찾도록 보강
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
export TZ=Asia/Seoul

LOG="${HOME}/log-cleanup.log"
KEEP="1month"
DRY_RUN=0

while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run) DRY_RUN=1; shift ;;
    --keep)    KEEP="${2:?--keep 뒤에 보존 기간이 필요합니다 (예: 1month)}"; shift 2 ;;
    *)
      echo "사용법: bash scripts/log-cleanup.sh [--dry-run] [--keep <기간>]" >&2
      exit 2
      ;;
  esac
done

say() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "$LOG"
}

say "===== journal 정리 시작 (보존=$KEEP, dry-run=$DRY_RUN) ====="

BEFORE=$(journalctl --disk-usage 2>/dev/null | grep -oE '[0-9.]+[KMG]' | tail -1)
FILES_BEFORE=$(find /var/log/journal -name '*.journal' 2>/dev/null | wc -l)
say "정리 전: ${BEFORE:-?} / 파일 ${FILES_BEFORE}개"

if [ "$DRY_RUN" -eq 1 ]; then
  # vacuum 의 판정 로직을 흉내내지 않는다. 파일별 최종 기록 시각과 크기를 그대로 보여주고
  # 사람이 판단하게 한다 (vacuum 은 아카이브 파일의 최신 항목이 보존 기간보다 오래된 것을 지운다).
  say "--dry-run — 삭제하지 않습니다. journal 파일 목록(최종 기록 시각 순):"
  find /var/log/journal -name '*.journal' -printf '%TY-%Tm-%Td  %10s  %p\n' 2>/dev/null \
    | sort | awk '{printf "  %s  %6.1fMB  %s\n", $1, $2/1048576, $3}' | tee -a "$LOG"
  say "위 목록에서 '$KEEP' 보다 오래된 아카이브 파일이 제거 대상입니다."
  say "===== dry-run 종료 ====="
  exit 0
fi

if ! journalctl --vacuum-time="$KEEP" >>"$LOG" 2>&1; then
  say "ERROR: journalctl --vacuum-time 실패. 위 로그를 확인하세요."
  exit 1
fi

AFTER=$(journalctl --disk-usage 2>/dev/null | grep -oE '[0-9.]+[KMG]' | tail -1)
FILES_AFTER=$(find /var/log/journal -name '*.journal' 2>/dev/null | wc -l)
say "정리 후: ${AFTER:-?} / 파일 ${FILES_AFTER}개 (파일 $((FILES_BEFORE - FILES_AFTER))개 제거)"

# 디스크 여유도 함께 남겨 추이를 본다
say "디스크: $(df -h / | awk 'NR==2 {print $3 " / " $2 " (" $5 " 사용)"}')"
say "===== journal 정리 완료 ====="
