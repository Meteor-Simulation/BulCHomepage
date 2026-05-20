# AWS 백엔드 배포 가이드

## 목차
1. [개요](#1-개요)
2. [AWS 사전 준비](#2-aws-사전-준비)
3. [RDS PostgreSQL 생성](#3-rds-postgresql-생성)
4. [EC2 인스턴스 생성 및 설정](#4-ec2-인스턴스-생성-및-설정)
5. [Docker 환경 구성](#5-docker-환경-구성)
6. [백엔드 배포](#6-백엔드-배포)
7. [Cloudflare 프록시 연동](#7-cloudflare-프록시-연동)
8. [보안 설정](#8-보안-설정)
9. [모니터링 및 로그](#9-모니터링-및-로그)
10. [GitHub Actions CI/CD](#10-github-actions-cicd)
11. [트러블슈팅](#11-트러블슈팅)
12. [비용 관리](#12-비용-관리)

---

## 1. 개요

### 1.1 배포 구성도

```
[Cloudflare Pages]  ←──  프론트엔드 (React)
        │
        │ /api/* 요청
        ▼
[Cloudflare Proxy]  ←──  DDoS 방어, IP 숨김
        │
        ▼
[AWS EC2]           ←──  Spring Boot 백엔드
        │
        ▼
[AWS RDS]           ←──  PostgreSQL 16
```

### 1.2 사용할 AWS 서비스

| 서비스 | 용도 | 프리티어 |
|--------|------|----------|
| EC2 | Spring Boot 백엔드 | t2.micro/t3.micro 1년 무료 |
| RDS | PostgreSQL DB | db.t3.micro 1년 무료 |
| VPC | 네트워크 격리 | 무료 |
| Security Group | 방화벽 | 무료 |
| Elastic IP | 고정 IP | 사용 중인 인스턴스에 연결 시 무료 |

### 1.3 프리티어 조건 (가입 후 12개월)

| 항목 | 무료 제공량 |
|------|-------------|
| EC2 t2.micro | 월 750시간 |
| RDS db.t3.micro | 월 750시간 |
| RDS 스토리지 | 20GB SSD |
| 데이터 전송 | 월 15GB 아웃바운드 |
| Elastic IP | 1개 (실행 중인 인스턴스 연결 시) |

---

## 2. AWS 사전 준비

### 2.1 AWS 계정 생성

1. [AWS 공식 사이트](https://aws.amazon.com/ko/) 접속
2. **"무료 계정 만들기"** 클릭
3. 이메일, 비밀번호, 계정 이름 입력
4. 결제 정보 등록 (프리티어 내 사용 시 과금 없음)
5. 전화번호 인증 완료

### 2.2 리전 선택

서울 리전 사용 권장:
```
리전: Asia Pacific (Seoul)
코드: ap-northeast-2
```

### 2.3 IAM 사용자 생성 (권장)

루트 계정 대신 IAM 사용자로 작업합니다:

1. **IAM** 서비스 접속
2. **"Users"** → **"Create user"**
3. 사용자 이름 입력 (예: `bulc-admin`)
4. **"Attach policies directly"** → `AdministratorAccess` 선택
5. Access Key 생성 (CLI 사용 시 필요)

---

## 3. RDS PostgreSQL 생성

### 3.1 RDS 인스턴스 생성

1. AWS 콘솔 → **RDS** 서비스
2. **"Create database"** 클릭

### 3.2 설정 값

| 설정 | 값 |
|------|-----|
| Engine | PostgreSQL |
| Version | 16.x |
| Template | **Free tier** |
| DB instance identifier | `bulc-homepage-db` |
| Master username | `postgres` |
| Master password | (강력한 비밀번호 설정) |
| DB instance class | `db.t3.micro` |
| Storage | 20 GiB (General Purpose SSD) |
| Storage autoscaling | 비활성화 (비용 방지) |
| VPC | Default VPC |
| Public access | **No** (EC2에서만 접근) |
| Database name | `bulc_homepage_db` |

### 3.3 보안 그룹 설정

RDS 보안 그룹에서 EC2로부터의 접근만 허용:

```
Type:        PostgreSQL
Protocol:    TCP
Port:        5432
Source:      EC2 보안 그룹 ID (sg-xxxxx)
```

### 3.4 RDS 엔드포인트 확인

생성 완료 후 엔드포인트를 메모합니다:
```
bulc-homepage-db.xxxxxxxxx.ap-northeast-2.rds.amazonaws.com
```

### 3.5 초기 DB 스키마 적용

EC2에서 RDS에 접속하여 스키마 적용:
```bash
# EC2 인스턴스에서 실행
psql -h <RDS_ENDPOINT> -U postgres -d bulc_homepage_db -f database/init.sql
```

---

## 4. EC2 인스턴스 생성 및 설정

### 4.1 EC2 인스턴스 생성

1. AWS 콘솔 → **EC2** → **"Launch Instance"**

### 4.2 설정 값

| 설정 | 값 |
|------|-----|
| Name | `bulc-homepage-backend` |
| AMI | Amazon Linux 2023 |
| Instance type | `t2.micro` (프리티어) |
| Key pair | 새로 생성 또는 기존 키 선택 |
| Network | Default VPC |
| Auto-assign public IP | Enable |
| Storage | 20 GiB gp3 (프리티어 30GiB 이내) |

### 4.3 보안 그룹 설정

| Type | Port | Source | 설명 |
|------|------|--------|------|
| SSH | 22 | 내 IP | SSH 접속용 |
| HTTP | 80 | Cloudflare IP 범위 | Cloudflare 프록시 |
| HTTPS | 443 | Cloudflare IP 범위 | Cloudflare 프록시 |
| Custom TCP | 8080 | Cloudflare IP 범위 | Spring Boot (직접 접근 시) |

> Cloudflare IP 범위: https://www.cloudflare.com/ips/

### 4.4 Elastic IP 할당 (선택)

EC2 재시작 시에도 IP가 변경되지 않도록 고정 IP 할당:

1. **EC2** → **Elastic IPs** → **"Allocate Elastic IP address"**
2. 할당 후 **"Associate Elastic IP address"** 클릭
3. 생성한 EC2 인스턴스 선택

### 4.5 EC2 접속

```bash
# 키 파일 권한 설정 (Linux/Mac)
chmod 400 bulc-homepage-key.pem

# SSH 접속
ssh -i bulc-homepage-key.pem ec2-user@<EC2_PUBLIC_IP>

# Windows의 경우 PuTTY 또는 PowerShell 사용
ssh -i bulc-homepage-key.pem ec2-user@<EC2_PUBLIC_IP>
```

---

## 5. Docker 환경 구성

### 5.1 EC2에 Docker 설치

```bash
# 시스템 업데이트
sudo yum update -y

# Docker 설치
sudo yum install -y docker

# Docker 서비스 시작 및 자동 시작 설정
sudo systemctl start docker
sudo systemctl enable docker

# ec2-user를 docker 그룹에 추가 (sudo 없이 docker 사용)
sudo usermod -aG docker ec2-user

# 재접속하여 그룹 적용
exit
ssh -i bulc-homepage-key.pem ec2-user@<EC2_PUBLIC_IP>

# Docker 확인
docker --version
```

### 5.2 Docker Compose 설치

```bash
# Docker Compose 설치
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# 확인
docker-compose --version
```

### 5.3 Git 설치 및 프로젝트 클론

```bash
# Git 설치
sudo yum install -y git

# 프로젝트 클론
cd /home/ec2-user
git clone https://github.com/<your-username>/BulC_Homepage.git
cd BulC_Homepage
```

---

## 6. 백엔드 배포

### 6.1 환경변수 파일 생성

`.env` 파일을 프로젝트 루트에 생성합니다:

```bash
nano .env
```

```env
# ==========================================
# Database (AWS RDS)
# ==========================================
DB_HOST=bulc-homepage-db.xxxxxxxxx.ap-northeast-2.rds.amazonaws.com
DB_PORT=5432
DB_NAME=bulc_homepage_db
DB_USER=postgres
DB_PASSWORD=<RDS에서 설정한 비밀번호>

# ==========================================
# Server
# ==========================================
BACKEND_PORT=8080

# ==========================================
# JWT
# ==========================================
JWT_SECRET=<최소 64자 이상의 랜덤 문자열>
JWT_ACCESS_EXPIRATION=3600000
JWT_REFRESH_EXPIRATION=2592000000

# ==========================================
# Server URL
# ==========================================
SERVER_URL=https://api.bulc.co.kr

# ==========================================
# OAuth2
# ==========================================
NAVER_CLIENT_ID=<네이버 클라이언트 ID>
NAVER_CLIENT_SECRET=<네이버 시크릿>
KAKAO_CLIENT_ID=<카카오 클라이언트 ID>
KAKAO_CLIENT_SECRET=<카카오 시크릿>
GOOGLE_CLIENT_ID=<구글 클라이언트 ID>
GOOGLE_CLIENT_SECRET=<구글 시크릿>

OAUTH2_CALLBACK_BASE_URL=https://api.bulc.co.kr
OAUTH2_REDIRECT_URI=https://www.bulc.co.kr

# ==========================================
# Microsoft Graph (이메일)
# ==========================================
AZURE_TENANT_ID=<Azure 테넌트 ID>
AZURE_CLIENT_ID=<Azure 클라이언트 ID>
AZURE_CLIENT_SECRET=<Azure 시크릿>
MAIL_FROM_ADDRESS=<발신 이메일>
MAIL_FROM_NAME=BulC

# ==========================================
# Toss Payments
# ==========================================
TOSS_CLIENT_KEY=<토스 클라이언트 키>
TOSS_SECRET_KEY=<토스 시크릿 키>
TOSS_SUCCESS_URL=https://www.bulc.co.kr/payment/success
TOSS_FAIL_URL=https://www.bulc.co.kr/payment/fail

# ==========================================
# Licensing
# ==========================================
LIC_PRIVATE_KEY_PATH=/app/keys/session_token_private_key.pem
LIC_PUBLIC_KEY_PATH=/app/keys/session_token_public_key.pem
LIC_TOKEN_TTL_MINUTES=15
LIC_TOKEN_ISSUER=bulc-homepage
LIC_STALE_THRESHOLD_MINUTES=30
```

### 6.2 백엔드 전용 docker-compose 구성

EC2에서 백엔드만 실행하므로 `docker-compose.backend.yml`을 생성합니다:

```yaml
version: '3.8'

services:
  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    container_name: bulc-backend
    ports:
      - "${BACKEND_PORT:-8080}:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_DATASOURCE_URL=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
      - SPRING_DATASOURCE_USERNAME=${DB_USER}
      - SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}
      - JWT_SECRET=${JWT_SECRET}
      - JWT_ACCESS_TOKEN_EXPIRATION=${JWT_ACCESS_EXPIRATION}
      - JWT_REFRESH_TOKEN_EXPIRATION=${JWT_REFRESH_EXPIRATION}
      - SERVER_URL=${SERVER_URL}
      - NAVER_CLIENT_ID=${NAVER_CLIENT_ID}
      - NAVER_CLIENT_SECRET=${NAVER_CLIENT_SECRET}
      - KAKAO_CLIENT_ID=${KAKAO_CLIENT_ID}
      - KAKAO_CLIENT_SECRET=${KAKAO_CLIENT_SECRET}
      - GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID}
      - GOOGLE_CLIENT_SECRET=${GOOGLE_CLIENT_SECRET}
      - OAUTH2_CALLBACK_BASE_URL=${OAUTH2_CALLBACK_BASE_URL}
      - OAUTH2_REDIRECT_URI=${OAUTH2_REDIRECT_URI}
      - AZURE_TENANT_ID=${AZURE_TENANT_ID}
      - AZURE_CLIENT_ID=${AZURE_CLIENT_ID}
      - AZURE_CLIENT_SECRET=${AZURE_CLIENT_SECRET}
      - MAIL_FROM_ADDRESS=${MAIL_FROM_ADDRESS}
      - MAIL_FROM_NAME=${MAIL_FROM_NAME}
      - TOSS_CLIENT_KEY=${TOSS_CLIENT_KEY}
      - TOSS_SECRET_KEY=${TOSS_SECRET_KEY}
      - TOSS_SUCCESS_URL=${TOSS_SUCCESS_URL}
      - TOSS_FAIL_URL=${TOSS_FAIL_URL}
      - LIC_PRIVATE_KEY_PATH=${LIC_PRIVATE_KEY_PATH}
      - LIC_PUBLIC_KEY_PATH=${LIC_PUBLIC_KEY_PATH}
      - LIC_TOKEN_TTL_MINUTES=${LIC_TOKEN_TTL_MINUTES}
      - LIC_TOKEN_ISSUER=${LIC_TOKEN_ISSUER}
      - LIC_STALE_THRESHOLD_MINUTES=${LIC_STALE_THRESHOLD_MINUTES}
      - JAVA_OPTS=-Xms512m -Xmx1024m
    volumes:
      - ./keys:/app/keys:ro
    restart: always
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/api/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s

networks:
  default:
    name: bulc-network-prod
```

### 6.3 라이선스 키 파일 준비

```bash
# 키 디렉토리 생성
mkdir -p /home/ec2-user/BulC_Homepage/keys

# RSA 키 생성 (없는 경우)
openssl genrsa -out keys/session_token_private_key.pem 2048
openssl rsa -in keys/session_token_private_key.pem -pubout -out keys/session_token_public_key.pem

# 권한 설정
chmod 600 keys/session_token_private_key.pem
chmod 644 keys/session_token_public_key.pem
```

### 6.4 빌드 및 실행

```bash
cd /home/ec2-user/BulC_Homepage

# 빌드 및 실행
docker-compose -f docker-compose.backend.yml up -d --build

# 로그 확인
docker-compose -f docker-compose.backend.yml logs -f backend

# 상태 확인
docker-compose -f docker-compose.backend.yml ps
```

### 6.5 동작 확인

```bash
# Health Check
curl http://localhost:8080/api/health

# 응답 예시
# {"status":"UP"}
```

---

## 7. Cloudflare 프록시 연동

### 7.1 Cloudflare에 도메인 추가

1. Cloudflare 대시보드 → **"Add a site"**
2. 도메인 입력 (예: `bulc.co.kr`)
3. **Free** 플랜 선택
4. 기존 DNS 레코드 확인 및 가져오기
5. 도메인 등록기관에서 Cloudflare 네임서버로 변경

### 7.2 백엔드 DNS 레코드 추가

```
Type:    A
Name:    api
Content: <EC2 Elastic IP>
Proxy:   Proxied (주황색 구름 아이콘 ON)
TTL:     Auto
```

이렇게 설정하면:
- `api.bulc.co.kr` → EC2 서버로 라우팅
- Cloudflare 프록시를 통해 실제 EC2 IP 숨김
- DDoS 방어 적용

### 7.3 SSL 설정

Cloudflare 대시보드 → **SSL/TLS**:

| 설정 | 값 |
|------|-----|
| SSL mode | **Full (strict)** 권장 |
| Always Use HTTPS | ON |
| Minimum TLS Version | TLS 1.2 |

### 7.4 Cloudflare 방화벽 규칙 (선택)

**Security** → **WAF** → **Custom rules**에서 추가 보호:

```
# API 요청에 Rate Limiting 적용
URI Path contains "/api/"
Rate: 100 requests per 1 minute
Action: Block
```

---

## 8. 보안 설정

### 8.1 EC2 보안 그룹 최종 설정

Cloudflare 프록시 사용 시 EC2에는 Cloudflare IP만 허용합니다:

```bash
# Cloudflare IPv4 범위 (2026년 기준, 최신 목록 확인 필요)
# https://www.cloudflare.com/ips-v4
173.245.48.0/20
103.21.244.0/22
103.22.200.0/22
103.31.4.0/22
141.101.64.0/18
108.162.192.0/18
190.93.240.0/20
188.114.96.0/20
197.234.240.0/22
198.41.128.0/17
162.158.0.0/15
104.16.0.0/13
104.24.0.0/14
172.64.0.0/13
131.0.72.0/22
```

### 8.2 SSH 접근 제한

```bash
# SSH 키 인증만 허용 (비밀번호 로그인 비활성화)
sudo nano /etc/ssh/sshd_config

# 다음 설정 확인/변경
PasswordAuthentication no
PermitRootLogin no

# SSH 재시작
sudo systemctl restart sshd
```

### 8.3 자동 보안 업데이트

```bash
# Amazon Linux 2023 자동 업데이트 설정
sudo yum install -y yum-cron
sudo systemctl enable yum-cron
sudo systemctl start yum-cron
```

### 8.4 .env 파일 보안

```bash
# .env 파일 권한 제한
chmod 600 /home/ec2-user/BulC_Homepage/.env

# Git에 .env가 포함되지 않도록 확인
# .gitignore에 .env가 포함되어 있는지 확인
```

---

## 9. 모니터링 및 로그

### 9.1 Docker 로그 확인

```bash
# 실시간 로그 확인
docker logs -f bulc-backend

# 최근 100줄 확인
docker logs --tail 100 bulc-backend

# 특정 시간 이후 로그
docker logs --since 1h bulc-backend
```

### 9.2 로그 로테이션 설정

`/etc/docker/daemon.json`:
```json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  }
}
```

```bash
sudo systemctl restart docker
```

### 9.3 디스크 용량 모니터링

```bash
# 디스크 사용량 확인
df -h

# Docker 이미지/컨테이너 용량
docker system df

# 미사용 리소스 정리
docker system prune -f
```

### 9.4 서비스 자동 재시작 확인

`docker-compose.backend.yml`에 `restart: always`가 설정되어 있으므로
EC2 재시작 시에도 Docker 서비스가 자동으로 컨테이너를 재시작합니다.

EC2 부팅 시 Docker 자동 시작 확인:
```bash
sudo systemctl is-enabled docker
# enabled 가 출력되면 정상
```

---

## 10. GitHub Actions CI/CD

### 10.1 자동 배포 워크플로우

`.github/workflows/deploy-backend.yml`:

```yaml
name: Deploy Backend to AWS EC2

on:
  push:
    branches:
      - main
    paths:
      - 'backend/**'

jobs:
  deploy:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Deploy to EC2 via SSH
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.EC2_HOST }}
          username: ec2-user
          key: ${{ secrets.EC2_SSH_KEY }}
          script: |
            cd /home/ec2-user/BulC_Homepage

            # 최신 코드 가져오기
            git pull origin main

            # 백엔드 재빌드 및 재시작
            docker-compose -f docker-compose.backend.yml up -d --build backend

            # 이전 이미지 정리
            docker image prune -f

            # 헬스체크 대기
            echo "Waiting for backend to start..."
            sleep 30
            curl -f http://localhost:8080/api/health || echo "Health check failed"
```

### 10.2 GitHub Secrets 설정

GitHub 저장소 → **Settings** → **Secrets and variables** → **Actions**:

| Secret 이름 | 값 |
|-------------|-----|
| `EC2_HOST` | EC2 퍼블릭 IP 또는 Elastic IP |
| `EC2_SSH_KEY` | EC2 키 페어의 프라이빗 키 (.pem 파일 내용 전체) |

### 10.3 배포 흐름

```
[개발자 Push (main 브랜치)]
    ↓
[GitHub Actions 트리거]
    ↓ (backend/** 경로 변경 감지)
[SSH로 EC2 접속]
    ↓
[git pull → docker-compose build → restart]
    ↓
[Health Check 확인]
    ↓
[배포 완료]
```

---

## 11. 트러블슈팅

### 11.1 EC2 메모리 부족

t2.micro는 1GB 메모리이므로 Spring Boot 빌드 시 메모리 부족이 발생할 수 있습니다.

**해결 1: Swap 메모리 추가**
```bash
# 2GB Swap 파일 생성
sudo dd if=/dev/zero of=/swapfile bs=128M count=16
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile

# 영구 적용
echo '/swapfile swap swap defaults 0 0' | sudo tee -a /etc/fstab

# 확인
free -h
```

**해결 2: JVM 메모리 조정**
```yaml
# docker-compose.backend.yml
environment:
  - JAVA_OPTS=-Xms256m -Xmx512m
```

### 11.2 Docker 빌드 실패

```bash
# Gradle 캐시 정리
cd backend
./gradlew clean

# Docker 빌드 캐시 삭제 후 재빌드
docker-compose -f docker-compose.backend.yml build --no-cache
```

### 11.3 RDS 연결 실패

```bash
# EC2에서 RDS 접근 테스트
sudo yum install -y postgresql16
psql -h <RDS_ENDPOINT> -U postgres -d bulc_homepage_db

# 연결 실패 시 확인사항:
# 1. RDS 보안 그룹에 EC2 보안 그룹이 허용되어 있는지
# 2. RDS가 같은 VPC에 있는지
# 3. RDS의 Publicly Accessible이 No인지 확인
```

### 11.4 Cloudflare 502 Bad Gateway

```
원인: 백엔드 서버가 응답하지 않음

확인 순서:
1. docker ps → 컨테이너 실행 중인지 확인
2. docker logs bulc-backend → 에러 로그 확인
3. curl localhost:8080/api/health → 로컬 응답 확인
4. 보안 그룹에서 포트가 열려 있는지 확인
```

### 11.5 OAuth 콜백 오류

```
OAuth2 콜백 URL을 각 제공자 개발자 콘솔에서 업데이트:

네이버: https://developers.naver.com/apps
카카오: https://developers.kakao.com/console
구글:   https://console.cloud.google.com

콜백 URL 예시:
https://api.bulc.co.kr/login/oauth2/code/naver
https://api.bulc.co.kr/login/oauth2/code/kakao
https://api.bulc.co.kr/login/oauth2/code/google
```

---

## 12. 비용 관리

### 12.1 프리티어 사용량 모니터링

1. AWS 콘솔 → **Billing and Cost Management**
2. **Free Tier** 메뉴에서 사용량 확인
3. **Budgets**에서 예산 알림 설정 (예: $1 초과 시 이메일 알림)

### 12.2 비용 알림 설정

```
AWS Budgets 설정:
1. Billing → Budgets → Create budget
2. Cost budget 선택
3. Monthly, $5 설정
4. 80% 도달 시 이메일 알림
```

### 12.3 프리티어 만료 후 예상 비용

| 서비스 | 월 예상 비용 |
|--------|-------------|
| EC2 t3.micro | ~$8 |
| RDS db.t3.micro | ~$15 |
| Elastic IP | $0 (사용 중) |
| 데이터 전송 | ~$1 |
| **합계** | **~$24/월** |

### 12.4 비용 절약 팁

- **EC2 예약 인스턴스**: 1년 약정 시 30-40% 할인
- **RDS 대안**: EC2에 직접 PostgreSQL 설치 (RDS 비용 절약, 관리 복잡도 증가)
- **Spot 인스턴스**: 개발/테스트 환경에서 최대 90% 할인 (중단 가능성 있음)
- **Aurora Serverless v2**: 사용하지 않을 때 자동 스케일 다운

---

## 부록: 전체 배포 체크리스트

### AWS 인프라 구축

- [ ] AWS 계정 생성 및 프리티어 확인
- [ ] IAM 사용자 생성
- [ ] VPC 및 서브넷 확인
- [ ] RDS PostgreSQL 인스턴스 생성
- [ ] EC2 인스턴스 생성
- [ ] Elastic IP 할당 및 연결
- [ ] 보안 그룹 설정 (SSH, HTTP, HTTPS)

### 서버 설정

- [ ] EC2에 Docker / Docker Compose 설치
- [ ] 프로젝트 Git Clone
- [ ] .env 파일 생성
- [ ] 라이선스 RSA 키 생성
- [ ] Swap 메모리 설정 (t2.micro)
- [ ] DB 초기 스키마 적용 (init.sql)

### 배포 및 연동

- [ ] Docker 빌드 및 실행
- [ ] Health Check 확인
- [ ] Cloudflare DNS 레코드 추가 (api 서브도메인)
- [ ] SSL 설정 확인
- [ ] CORS 설정 확인
- [ ] OAuth2 콜백 URL 업데이트

### 운영 준비

- [ ] GitHub Actions CI/CD 설정
- [ ] 로그 로테이션 설정
- [ ] AWS 비용 알림 설정
- [ ] SSH 보안 강화
- [ ] 백업 전략 수립

---

*문서 작성일: 2026-02-04*
*작성자: Claude Code*
