# 서버 스크립트

## db-migrate.sh — DB 마이그레이션 러너

`database/migrations/V*.sql` 중 운영 DB에 아직 적용되지 않은 것을 파일명 순서대로 적용하고,
이력을 운영 DB의 `schema_migrations` 테이블에 기록합니다.
이 프로젝트는 Flyway/Liquibase를 쓰지 않으므로 이 스크립트가 이력 관리 주체입니다.

**로컬에서 실행합니다** (서버에 ssh로 붙어 동작).

```bash
bash scripts/db-migrate.sh --status     # 적용/미적용 목록만 출력 (DB 변경 없음)
bash scripts/db-migrate.sh              # 미적용분 적용
bash scripts/db-migrate.sh --baseline   # 최초 1회만: 기존 파일을 '적용됨'으로 등록 (SQL 실행 안 함)
```

`deploy.sh` 가 [4/9] 단계에서 자동 호출하므로, 단독 실행은 사전 점검·복구용입니다.

| 동작 | 설명 |
|------|------|
| 순서 | `git pull` 로 서버에 파일이 들어온 뒤, 백엔드 재시작 **전에** 실행 |
| 트랜잭션 | 파일별 `--single-transaction`. `CONCURRENTLY` 포함 시 자동 해제 |
| 체크섬 | 적용 후 파일이 수정되면 불일치로 판정하고 배포를 중단 |
| 안전장치 | 이력이 비었는데 미적용 파일이 있으면 재실행을 거부 (`--baseline` 안내) |

`ddl-auto: validate` 이므로 스키마가 엔티티와 어긋나면 백엔드가 기동 자체를 실패합니다.
순서를 뒤집지 마세요.

환경변수로 접속 대상을 바꿀 수 있습니다: `BULC_SSH_TARGET`(ssh config 별칭), `BULC_SSH_KEY`, `BULC_SERVER`.

---

## log-cleanup.sh — systemd journal 월 단위 정리

`/var/log` 1.9GB 중 1.8GB(95%)가 systemd journal 이었습니다. `journald.conf` 가
전 항목 기본값이라 상한이 없어 6개월치가 그대로 쌓였고, 내용의 91%는 SSH 무차별 대입 시도
기록입니다(7일 기준 sshd 36,256건 중 공격 12,421줄).

**정책**: 한 달치만 남기고, 매달 1일에 그보다 오래된 journal 파일을 제거합니다.

```bash
bash scripts/log-cleanup.sh              # 1개월 보존으로 정리
bash scripts/log-cleanup.sh --dry-run    # 삭제 없이 파일 목록·크기만 확인
bash scripts/log-cleanup.sh --keep 2month
```

### 전제 — journald.conf

```
[Journal]
MaxFileSec=1day     # 날짜별로 파일을 끊는다
SystemMaxUse=4G     # 안전망 (월 단위 정리를 방해하지 않는 상한)
```

`MaxFileSec=1day` 가 **월 단위 정리의 전제조건**입니다. journal vacuum 은 개별 로그 항목이
아니라 **파일 단위**로 삭제하므로, 파일 하나가 여러 주에 걸치면 그 파일의 마지막 항목이
늙을 때까지 지워지지 않습니다.

### 서버에 설치

```bash
# 1. journald 설정
sudo tee -a /etc/systemd/journald.conf >/dev/null <<'EOF'
MaxFileSec=1day
SystemMaxUse=4G
EOF
sudo systemctl restart systemd-journald

# 2. cron 등록 (매달 1일 05:00)
crontab -e
0 5 1 * * /bin/bash /home/ubuntu/BulCHomepage/scripts/log-cleanup.sh
```

실행 기록은 `~/log-cleanup.log` 에 남습니다.

### 건드리지 않는 것

nginx 로그(logrotate daily·14일·gzip), `auth.log`(rsyslog), `btmp`(monthly·rotate 1)는
각자 이미 관리되고 있어 이 스크립트의 대상이 아닙니다.

---

## health-check.sh — 헬스 체크 + 이메일 알림

매 시간 서버 상태를 확인하고, 이상 시 이메일 알림을 발송합니다.

### 체크 항목

| 항목 | 체크 방법 | 알림 조건 |
|------|----------|----------|
| 백엔드 | `curl /api/health` | HTTP 200 아닌 경우 |
| DB | `docker exec pg_isready` | 응답 없는 경우 |
| 프론트엔드 | `curl https://bulc.msimul.com` | HTTP 200 아닌 경우 |
| 디스크 | `df /` | 90% 초과 시 |
| Swap 메모리 | `free` | 80% 초과 시 |

### 서버에 설치

```bash
# 1. 스크립트 실행 권한 부여
chmod +x ~/BulCHomepage/scripts/health-check.sh

# 2. 로그 파일 생성
sudo touch /var/log/bulc-health-check.log
sudo chown ubuntu:ubuntu /var/log/bulc-health-check.log

# 3. cron 등록 (매 시간 00분)
crontab -e
# 아래 줄 추가:
0 * * * * /home/ubuntu/BulCHomepage/scripts/health-check.sh

# 4. cron 등록 확인
crontab -l
```

### 수동 실행 (테스트)

```bash
bash ~/BulCHomepage/scripts/health-check.sh
cat /var/log/bulc-health-check.log
```

### 알림 이메일 수신 주소 변경

`health-check.sh` 내 `ALERT_EMAIL` 변수 수정:
```bash
ALERT_EMAIL="juwon@msimul.com"
```
