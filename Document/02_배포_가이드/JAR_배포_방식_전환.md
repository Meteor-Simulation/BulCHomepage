# JAR 배포 방식 전환

> 작성일: 2026-04-25

---

## 1. 배경

### 기존 문제

2026-04-25 운영서버 배포 시 다음 문제가 발생했습니다.

| 항목 | 내용 |
|------|------|
| 서버 | Oracle Cloud Free Tier (ARM CPU, RAM 956MB) |
| 빌드 시간 | **1시간 6분** (compileJava 20분 + bootJar 20분 + Docker 20분) |
| 원인 | RAM 부족으로 Swap(디스크) 1GB 사용, 디스크 I/O가 병목 |
| 추가 문제 | 빌드 중 컨테이너 이름 충돌, application.yml 중복 키 등 부수 오류 발생 |

### 근본 원인

서버에서 **소스 코드 컴파일 + JAR 패키징 + Docker 이미지 빌드**를 모두 수행하는 구조.
956MB RAM으로는 Gradle 빌드(JVM)가 메모리를 초과하여 Swap을 사용하게 되고,
디스크 기반 Swap은 RAM 대비 수십 배 느려서 빌드 시간이 급격히 증가합니다.

---

## 2. 개선 방향

### 기존 방식: 서버에서 전체 빌드

```
로컬 PC                     운영 서버 (RAM 956MB)

git push                    
                            git pull
                            Gradle 컴파일     ← 20분 (RAM 부족, Swap 사용)
                            JAR 패키징        ← 20분
                            Docker 이미지 빌드 ← 20분
                            컨테이너 시작

                            총 1시간+
```

### 변경 방식: 로컬에서 빌드, 서버는 실행만

```
로컬 PC (RAM 16GB+)         운영 서버

Gradle 빌드     ← 1~2분     
JAR 생성 (약 80MB)           

JAR 파일 전송 ──────────→   JAR 수신
                            Docker 이미지 빌드 ← 1분 (JAR 복사만)
                            컨테이너 시작

총 3~5분                    총 2~3분
```

### 핵심 변경점

- Gradle 컴파일을 **로컬 PC에서** 수행 (메모리 충분)
- 서버에는 **빌드된 JAR 파일만** 전송
- 서버의 Dockerfile은 JAR을 복사하여 실행하는 단순 구조로 변경
- DB, 로그, 설정 등 **데이터에는 영향 없음** (JAR은 코드만 포함)

---

## 3. 변경 파일

| 파일 | 용도 | 변경 |
|------|------|------|
| `backend/Dockerfile` | 기존 (서버 빌드) | 유지 (필요 시 사용) |
| `backend/Dockerfile.prebuilt` | JAR 전용 (신규) | JAR 복사 + 실행만 |
| `docker-compose.prebuilt.yml` | override 설정 (신규) | Dockerfile.prebuilt 지정 |
| `deploy.sh` | 배포 스크립트 (신규) | 로컬 빌드 → 전송 → 재시작 자동화 |

### Dockerfile 비교

**기존 (`Dockerfile`) — 서버에서 전체 빌드:**
```dockerfile
FROM gradle:8.5-jdk17 AS builder     ← 빌드 스테이지
COPY . .
RUN gradle bootJar                    ← 서버에서 컴파일 (1시간)

FROM eclipse-temurin:17-jre
COPY --from=builder /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**변경 (`Dockerfile.prebuilt`) — JAR만 복사:**
```dockerfile
FROM eclipse-temurin:17-jre           ← 빌드 스테이지 없음
COPY build/libs/*.jar app.jar         ← 이미 만들어진 JAR 복사
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## 4. 배포 절차

### 사전 준비 (최초 1회)

- 로컬 PC에 Java 17 + Gradle 설치 확인
- `deploy.sh` 실행 권한 부여: `chmod +x deploy.sh`

### 배포 실행

```bash
# 프로젝트 루트에서 실행
bash deploy.sh
```

### 스크립트 내부 동작 (6단계)

```
[1/6] application.yml 검증
      → 중복 키 검사 (server: 등), 실패 시 중단

[2/6] 로컬에서 JAR 빌드
      → ./gradlew bootJar -x test
      → build/libs/*.jar 생성 (1~2분)

[3/6] 서버 코드 업데이트
      → ssh + git pull origin main
      → docker-compose, Dockerfile 등 설정 파일 동기화

[4/6] JAR 파일 서버 전송
      → scp로 JAR 업로드 (약 80MB, 1~3분)

[5/6] 서버에서 Docker 재시작
      → docker compose down backend (기존 컨테이너 정리)
      → docker compose up -d --build backend (JAR 복사 + 실행)

[6/6] 헬스 체크
      → curl http://localhost:8080/api/health
      → 최대 150초 대기, 실패 시 로그 확인 안내
```

### DB 마이그레이션이 필요한 경우

배포 스크립트 실행 **전에** 수동으로 실행:

```bash
ssh -i ~/.ssh/oracle_homepage.pem ubuntu@168.107.19.139 \
  "cd ~/BulCHomepage && docker compose -f docker-compose.prod.yml exec -T database \
   sh -c 'psql -U bulc_prod_user -d bulc_homepage_db'" < database/migrations/V날짜__이름.sql
```

---

## 5. 기존 방식과의 호환

기존 서버 빌드 방식도 그대로 사용 가능합니다.

```bash
# 기존 방식 (서버에서 전체 빌드 — 1시간+)
docker compose -f docker-compose.prod.yml up -d --build backend

# 새 방식 (로컬 빌드 JAR — 5~10분)
docker compose -f docker-compose.prod.yml -f docker-compose.prebuilt.yml up -d --build backend
```

`-f docker-compose.prebuilt.yml`을 추가하면 JAR 방식, 빼면 기존 방식입니다.

---

## 6. 데이터 영향

| 항목 | 영향 |
|------|------|
| DB 데이터 | **없음** — Docker 볼륨(`postgres_data_prod`)에 유지 |
| DB 스키마 | **없음** — 마이그레이션은 별도 수동 실행 |
| .env 설정 | **없음** — 서버에 유지, JAR에 포함되지 않음 |
| 업로드 파일 | **없음** — Docker 볼륨에 유지 |
| 로그 | 컨테이너 재시작 시 리셋 (기존과 동일) |

---

## 7. 향후 발전 방향

| 단계 | 방식 | 시기 |
|------|------|------|
| 현재 | 서버 빌드 (1시간+) | ~ 2026-04 |
| **전환** | **로컬 JAR 빌드 + 업로드 (5~10분)** | **2026-04~** |
| 미래 | GitHub Actions CI/CD (자동 빌드 + 배포) | 팀 규모 확대 시 |
