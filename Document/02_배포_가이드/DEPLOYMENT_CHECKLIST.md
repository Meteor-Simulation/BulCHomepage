# BulC Homepage 배포 체크리스트

> 서버 배포 전 반드시 확인해야 할 항목들을 정리한 문서입니다.

---

## 1. 환경 변수 설정 (필수)

### 1.1 `.env.prod` 파일 생성

```bash
cp .env.prod.example .env.prod
```

### 1.2 필수 환경 변수

| 변수명 | 설명 | 예시 | 위험도 |
|--------|------|------|--------|
| `DB_PASSWORD` | 데이터베이스 비밀번호 | 강력한 랜덤 문자열 | 🔴 높음 |
| `JWT_SECRET` | JWT 서명 키 (64자 이상) | `openssl rand -base64 64` 로 생성 | 🔴 높음 |
| `DB_USER` | 데이터베이스 사용자명 | `bulc_prod_user` | 🔴 높음 |
| `DB_NAME` | 데이터베이스 이름 | `bulc_homepage_db` | 🟡 중간 |
| `SERVER_URL` | 서버 도메인 | `https://your-domain.com` | 🟡 중간 |

### 1.3 OAuth 소셜 로그인

| 변수명 | 발급처 |
|--------|--------|
| `NAVER_CLIENT_ID` | [네이버 개발자센터](https://developers.naver.com) |
| `NAVER_CLIENT_SECRET` | 네이버 개발자센터 |
| `KAKAO_CLIENT_ID` | [카카오 개발자센터](https://developers.kakao.com) |
| `KAKAO_CLIENT_SECRET` | 카카오 개발자센터 |
| `GOOGLE_CLIENT_ID` | [구글 클라우드 콘솔](https://console.cloud.google.com) |
| `GOOGLE_CLIENT_SECRET` | 구글 클라우드 콘솔 |
| `OAUTH2_REDIRECT_URI` | `https://your-domain.com/oauth/callback` |

> ⚠️ 각 플랫폼에서 **프로덕션용 앱**을 별도로 등록하고, **Redirect URI**를 프로덕션 도메인으로 설정해야 합니다.

### 1.4 이메일 설정 (Microsoft Graph API)

| 변수명 | 설명 |
|--------|------|
| `MS_TENANT_ID` | Azure AD 테넌트 ID |
| `MS_CLIENT_ID` | 앱 클라이언트 ID |
| `MS_CLIENT_SECRET` | 앱 클라이언트 시크릿 |
| `MAIL_FROM` | 발신 이메일 주소 |
| `MAIL_FROM_ACCOUNTS` | 계정 관련 발신 주소 |
| `MAIL_FROM_BILLING` | 결제 관련 발신 주소 |
| `MAIL_REPLY_TO` | 회신 주소 |

### 1.5 라이선스 키

| 변수명 | 설명 |
|--------|------|
| `LIC_PRIVATE_KEY_PATH` | RS256 개인키 경로 (Docker secrets 사용 권장) |
| `LIC_PUBLIC_KEY_PATH` | RS256 공개키 경로 |

---

## 2. Docker 설정

### 2.1 프로덕션 빌드 테스트

```bash
# 이미지 빌드
docker compose -f docker-compose.prod.yml build

# 빌드 성공 확인
docker images | grep bulc
```

### 2.2 프로덕션 실행

```bash
# 컨테이너 시작
docker compose -f docker-compose.prod.yml up -d

# 상태 확인
docker compose -f docker-compose.prod.yml ps

# 로그 확인
docker compose -f docker-compose.prod.yml logs -f
```

### 2.3 헬스 체크

```bash
# 백엔드 헬스 체크
curl http://localhost:8080/actuator/health

# 프론트엔드 접속 확인
curl -I http://localhost
```

---

## 3. 보안 체크리스트

### 3.1 파일 보안

- [ ] `.env.prod` 파일이 `.gitignore`에 포함되어 있는지 확인
- [ ] `secrets/` 폴더가 `.gitignore`에 포함되어 있는지 확인
- [ ] 민감한 정보가 코드에 하드코딩되어 있지 않은지 확인

### 3.2 네트워크 보안

- [ ] 데이터베이스 포트(5432)가 외부에 노출되지 않는지 확인
- [ ] 백엔드 포트(8080)가 외부에 직접 노출되지 않는지 확인 (Nginx 프록시 사용)
- [ ] HTTPS/SSL 인증서 설정 완료

### 3.3 인증 보안

- [ ] JWT_SECRET이 충분히 강력한지 확인 (64자 이상 랜덤)
- [ ] OAuth Redirect URI가 프로덕션 도메인인지 확인
- [ ] 개발용 OAuth 키가 아닌 프로덕션용 키 사용

### 3.4 Docker 보안

- [ ] 백엔드 컨테이너가 non-root 사용자로 실행되는지 확인
- [ ] 불필요한 포트가 노출되지 않는지 확인

---

## 4. SSL/HTTPS 설정

### 4.1 Let's Encrypt 사용 시

```bash
# Certbot 설치
sudo apt install certbot python3-certbot-nginx

# 인증서 발급
sudo certbot --nginx -d your-domain.com -d www.your-domain.com

# 자동 갱신 테스트
sudo certbot renew --dry-run
```

### 4.2 Nginx SSL 설정 예시

```nginx
server {
    listen 443 ssl http2;
    server_name your-domain.com;

    ssl_certificate /etc/letsencrypt/live/your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;

    # SSL 보안 설정
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256;
    ssl_prefer_server_ciphers off;

    location / {
        proxy_pass http://localhost:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location /api {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}

server {
    listen 80;
    server_name your-domain.com;
    return 301 https://$server_name$request_uri;
}
```

---

## 5. 데이터베이스

### 5.1 초기 설정

- [ ] `init.sql` 스크립트가 정상 실행되는지 확인
- [ ] 기본 관리자 계정 비밀번호 변경
- [ ] 불필요한 테스트 데이터 제거

### 5.2 백업 설정

```bash
# 수동 백업
docker exec bulc-db-prod pg_dump -U $DB_USER $DB_NAME > backup_$(date +%Y%m%d).sql

# 자동 백업 (crontab)
# 매일 새벽 3시 백업
0 3 * * * docker exec bulc-db-prod pg_dump -U postgres bulc_homepage_db > /backup/db_$(date +\%Y\%m\%d).sql
```

### 5.3 복원

```bash
# 백업 복원
cat backup_20260206.sql | docker exec -i bulc-db-prod psql -U $DB_USER $DB_NAME
```

---

## 6. 모니터링 (권장)

### 6.1 로그 관리

```bash
# 로그 확인
docker compose -f docker-compose.prod.yml logs -f --tail 100

# 특정 서비스 로그
docker compose -f docker-compose.prod.yml logs -f backend
```

### 6.2 디스크 사용량 확인

```bash
# Docker 디스크 사용량
docker system df

# 미사용 리소스 정리
docker system prune -a
```

### 6.3 컨테이너 리소스 모니터링

```bash
# 실시간 리소스 사용량
docker stats
```

---

## 7. 배포 절차 요약

### 7.1 최초 배포

```bash
# 1. 저장소 클론
git clone https://github.com/Meteor-Simulation/BulCHomepage.git
cd BulCHomepage

# 2. 환경 변수 설정
cp .env.prod.example .env.prod
# .env.prod 파일 편집하여 실제 값 입력

# 3. secrets 폴더 생성 및 키 파일 배치
mkdir -p secrets
# session_token_private_key.pem, session_token_public_key.pem 파일 배치

# 4. 빌드 및 실행
docker compose -f docker-compose.prod.yml build
docker compose -f docker-compose.prod.yml up -d

# 5. 상태 확인
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f
```

### 7.2 업데이트 배포

```bash
# 1. 최신 코드 가져오기
git pull origin main

# 2. 이미지 재빌드
docker compose -f docker-compose.prod.yml build

# 3. 컨테이너 재시작 (다운타임 최소화)
docker compose -f docker-compose.prod.yml up -d --no-deps backend
docker compose -f docker-compose.prod.yml up -d --no-deps frontend

# 4. 상태 확인
docker compose -f docker-compose.prod.yml ps
```

### 7.3 롤백

```bash
# 이전 버전으로 롤백
git checkout <previous-commit-hash>
docker compose -f docker-compose.prod.yml build
docker compose -f docker-compose.prod.yml up -d
```

---

## 8. 문제 해결

### 8.1 컨테이너가 시작되지 않을 때

```bash
# 로그 확인
docker compose -f docker-compose.prod.yml logs backend

# 컨테이너 상태 확인
docker inspect bulc-backend-prod
```

### 8.2 데이터베이스 연결 실패

```bash
# DB 컨테이너 상태 확인
docker compose -f docker-compose.prod.yml logs database

# DB 접속 테스트
docker exec -it bulc-db-prod psql -U $DB_USER -d $DB_NAME
```

### 8.3 메모리 부족

```bash
# 메모리 사용량 확인
docker stats --no-stream

# docker-compose.prod.yml에 메모리 제한 추가
# deploy:
#   resources:
#     limits:
#       memory: 1G
```

---

## 9. docker-compose.prod.yml 보완 필요 항목

현재 `docker-compose.prod.yml`에 추가해야 할 환경 변수:

```yaml
backend:
  environment:
    # OAuth
    NAVER_CLIENT_ID: ${NAVER_CLIENT_ID}
    NAVER_CLIENT_SECRET: ${NAVER_CLIENT_SECRET}
    KAKAO_CLIENT_ID: ${KAKAO_CLIENT_ID}
    KAKAO_CLIENT_SECRET: ${KAKAO_CLIENT_SECRET}
    OAUTH2_CALLBACK_BASE_URL: ${SERVER_URL}
    OAUTH2_REDIRECT_URI: ${OAUTH2_REDIRECT_URI}
    # Email
    MS_TENANT_ID: ${MS_TENANT_ID}
    MS_CLIENT_ID: ${MS_CLIENT_ID}
    MS_CLIENT_SECRET: ${MS_CLIENT_SECRET}
    MAIL_FROM: ${MAIL_FROM}
    MAIL_FROM_ACCOUNTS: ${MAIL_FROM_ACCOUNTS}
    MAIL_FROM_BILLING: ${MAIL_FROM_BILLING}
    MAIL_REPLY_TO: ${MAIL_REPLY_TO}
    # Licensing
    LIC_PRIVATE_KEY_PATH: ${LIC_PRIVATE_KEY_PATH}
  volumes:
    - ./secrets/session_token_private_key.pem:/run/secrets/session_token_private_key.pem:ro
```

---

## 변경 이력

| 날짜 | 버전 | 변경 내용 |
|------|------|----------|
| 2026-02-06 | 1.0 | 최초 작성 |
