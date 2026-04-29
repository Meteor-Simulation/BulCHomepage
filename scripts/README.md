# 서버 스크립트

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
