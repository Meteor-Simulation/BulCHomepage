# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

BulCHomepage is a full-stack web application for the METEOR fire safety simulation and training platform. It uses React (TypeScript) frontend, Spring Boot (Java 17) backend, and PostgreSQL database, all containerized with Docker.

## Development Commands

### Frontend (from /frontend)
```bash
npm run dev      # Vite dev server at localhost:3000 (HMR)
npm run build    # Production build (output: dist/)
npm run preview  # Preview production build at localhost:3000
```

### Backend (from /backend)
```bash
./gradlew bootRun   # Run Spring Boot dev server at localhost:8080
./gradlew build     # Build project
./gradlew test      # Run JUnit tests
./gradlew bootJar   # Create production JAR
```

### Docker
```bash
# Development
docker-compose up -d                              # Start all services
docker-compose up -d database                     # Start PostgreSQL only

# Production
docker-compose -f docker-compose.prod.yml up -d
```

### Environment Setup
```bash
cp .env.example .env  # Copy environment template before running docker-compose
```

## Architecture

### Tech Stack
- **Frontend**: React 19 + TypeScript, React Router 6, Vite (build tool), CSS with custom properties
- **Backend**: Spring Boot 3.2, Spring Security + JWT, Spring Data JPA
- **Database**: PostgreSQL 16
- **Infrastructure**: Docker Compose, Nginx (production proxy)

### Project Structure
```
frontend/
├── src/
│   ├── index.tsx           # Entry point with React Router config
│   ├── Login/              # Login page with OAuth UI (Google, Kakao, Naver)
│   └── CategoryPages/      # Feature pages (BulC, Meteor, VR, More)

backend/
├── src/main/java/com/bulc/homepage/
│   ├── HomepageApplication.java   # Spring Boot entry
│   ├── controller/                # REST endpoints
│   └── licensing/                 # License system module
│       ├── domain/                # License, Activation, LicensePlan entities
│       ├── repository/            # JPA repositories
│       ├── service/               # LicenseService, LicensePlanAdminService
│       ├── controller/            # LicenseController, LicensePlanAdminController
│       ├── dto/                   # Request/Response DTOs
│       └── exception/             # LicenseException, ErrorCode
└── src/main/resources/application.yml  # Spring config with profiles

database/
└── init.sql               # PostgreSQL schema (users, auth, roles, licenses tables)

Document/
├── 01_제품_설계/         # 설계·아키텍처·요건·DB·라이선스 도메인
│   ├── licensing_api.md          # Licensing API 문서
│   ├── licensing_domain_v1.md    # Licensing 도메인 설계
│   └── 테이블정의서.md           # DB 테이블 정의
├── 02_배포_가이드/       # 배포 절차·환경 설정
├── 03_배포_이슈_및_사고_이력/  # 사고 컨텍스트·대응 기록
└── 04_기타/              # 보안 감사·결제 계약 등
```

### Key Patterns
- **Frontend**: Functional components, co-located CSS files, CSS variables for theming (--accent: #C4320A)
- **Backend**: Layered architecture (controller/service/repository), Spring profiles (dev/prod)
- **Database**: snake_case naming, BIGINT identity PKs, created_at/updated_at timestamps

### API Communication
- Frontend API calls use `VITE_API_URL` environment variable (`import.meta.env.VITE_API_URL`)
- Nginx proxies `/api/*` to backend at `http://backend:8080`
- Health check endpoint: `GET /api/health`

## Configuration

### Backend (application.yml)
- Spring profiles: `dev` (default), `prod`
- JPA ddl-auto: `validate` (no auto-migration, use init.sql)
- JWT configured with secret, access token (1h), refresh token (7d)

### Ports
- Frontend dev: 3000
- Backend: 8080
- PostgreSQL: 5432 (configurable via DB_PORT)
- Production Nginx: 80

## Agent 사용 규칙

- Git 관련 요청(커밋, 브랜치, PR 등) 및 이슈 관련 요청(Jira 티켓 생성, 이슈 추적 등)은 반드시 `task-orchestrator` agent를 사용하여 처리할 것.

## Licensing System

소프트웨어 라이선스 관리 시스템입니다. 자세한 내용은 `Document/01_제품_설계/licensing_api.md` 참조.

### Architecture
```
Client App → License API (validate/heartbeat) ─┐
Admin UI → Admin API (plan CRUD) ──────────────┼→ LicenseService → DB
Billing Module → Internal Service (직접 호출) ─┘
```

### Key Concepts
- **PolicySnapshot**: 플랜 수정 시 기존 라이선스에 영향 없음 (발급 시점 정책 스냅샷 저장)
- **Soft Delete**: 플랜 삭제 시 `is_deleted=true`로 표시, 기존 라이선스 유지
- **Offline Token**: Opaque 토큰, 클라이언트는 `offlineTokenExpiresAt`만 로컬 검증

### Client API Endpoints (v0.2.0+, 계정 기반 인증)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/me/licenses` | 내 라이선스 목록 조회 |
| POST | `/api/v1/licenses/validate` | 라이선스 검증 및 기기 활성화 |
| POST | `/api/v1/licenses/heartbeat` | 세션 갱신 (기존 활성화만) |
| POST | `/api/v1/licenses/validate/force` | 강제 활성화 (세션 충돌 시) |
| DELETE | `/api/v1/licenses/{id}/activations/{fingerprint}` | 기기 비활성화 |
| GET | `/api/v1/licenses/{id}` | 라이선스 조회 (ID) |

### Admin API Endpoints
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/admin/license-plans` | 플랜 목록 |
| POST | `/api/v1/admin/license-plans` | 플랜 생성 |
| PUT | `/api/v1/admin/license-plans/{id}` | 플랜 수정 |
| DELETE | `/api/v1/admin/license-plans/{id}` | 플랜 삭제 (soft) |
| GET | `/api/v1/admin/licenses` | 라이선스 검색 (관리자) |
| GET | `/api/v1/admin/licenses/{id}` | 라이선스 상세 조회 (관리자) |

### Redeem API Endpoints (v0.4.0)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/redeem` | 리딤 코드 등록 (Claim) |
| GET | `/api/v1/admin/redeem-campaigns` | 리딤 캠페인 목록 |
| GET | `/api/v1/admin/redeem-campaigns/{id}` | 캠페인 상세 |
| POST | `/api/v1/admin/redeem-campaigns` | 캠페인 생성 |
| PUT | `/api/v1/admin/redeem-campaigns/{id}` | 캠페인 수정 |
| PATCH | `/api/v1/admin/redeem-campaigns/{id}/pause` | 캠페인 일시정지 |
| PATCH | `/api/v1/admin/redeem-campaigns/{id}/end` | 캠페인 종료 |
| PATCH | `/api/v1/admin/redeem-campaigns/{id}/resume` | 캠페인 재개 |
| POST | `/api/v1/admin/redeem-campaigns/codes` | 코드 생성 |
| GET | `/api/v1/admin/redeem-campaigns/{campaignId}/codes` | 코드 목록 |
| DELETE | `/api/v1/admin/redeem-campaigns/codes/{codeId}` | 코드 비활성화 |

### Internal Service Methods (HTTP 미노출)
```java
// Billing 모듈에서 직접 호출
licenseService.issueLicenseWithPlan(ownerType, ownerId, planId, orderId, usageCategory);
licenseService.revokeLicenseByOrderId(orderId, reason);  // 환불 시
licenseService.suspendLicense(licenseId, reason);        // 정지
licenseService.renewLicense(licenseId, newValidUntil);   // 갱신
```

### License Status Flow
```
PENDING → ACTIVE → EXPIRED_GRACE → EXPIRED_HARD
              ↓
          SUSPENDED (복구 가능)
              ↓
          REVOKED (복구 불가)
```

### Testing
```bash
./gradlew test  # 102개 테스트 (Domain, Service, Controller, Integration)
```
테스트는 H2 인메모리 DB 사용 (`application-test.yml`)

---

## ⚠️ 병행 개발 조정 — 라이선싱 v1.2.0 × 웹 payment 트랙 (2026-09-09 갱신)

두 트랙이 본 리포에서 **단독 병행 개발 + PR 리뷰 최종 게이트** 방식으로 진행 중이다 (2026-09-02 합의). 본 절은 그 조정 정본이며, 트랙 상태가 바뀌면 이 절을 갱신한다. 이력 = Jira Epic **MDP-332** 코멘트.

### 기준선 — main (웹 확인 2026-09-09)

결제→라이선스 **의존성 분리가 main 에 안착**했고, 이후 개발은 main 을 기준으로 한다:

| 커밋 | 내용 |
|---|---|
| `b00101a` MDP-831 (#236) | **`LicenseIssuePort` 도입** — `payment/port/` 에 소비자 포트 정의, `licensing/adapter/PaymentLicenseIssueAdapter` 가 `LicenseService` 를 감싸 구현. `PaymentService`·`SubscriptionBillingService` 의 licensing 직접 import **제거** (실측) |
| `af0e51f` MDP-832 (#238) | 라이선스 발급 실패 자동 복구 — `payment/recovery/` 재시도 큐 + 마이그레이션 `V20260908__create_license_issue_retries_table.sql` |

> ⚠️ `refactor/payment-module`(수직 슬라이스 분리, `66dd77b`)는 **미머지이며 기준이 아니다** — main 의 `PaymentService` 재작성과 충돌하는 stale 상태. 부활 시 웹 트랙이 main 위로 rebase 한다.

### 인터럽트 맵 — 현행

| 이슈 | 상태 | 비고 |
|---|---|---|
| **MDP-791** (`products.code` 폭 + FK 3곳) | ✅ **차단 해제** (2026-09-09) | 종전 편집 vs 이동 충돌은 수직 슬라이스 *브랜치* 기준이었다. main 기준으로 `entity/PricePlan`·`Promotion`·`Subscription` 이 제자리에 있어 `@Column` 편집 가능 |
| **MDP-793** (OAuth code store Redis) | 조건부 진행 (유지) | **프로필 게이트 + Redis 미설정 시 인메모리 fallback** 필수 — 머지가 인프라 변경을 강제하면 안 된다 |
| MDP-787 · 788 · 789 · 790 · 792 | ✅ 자유 진행 | `licensing/`·`oauth/`·설정 국소 |

### 공유 접점 — 변경 시 상호 PR 리뷰 명시

- **`licensing/adapter/PaymentLicenseIssueAdapter.java`** — 웹 트랙(MDP-831)이 licensing 패키지 안에 둔 결제↔라이선스 결합 1점. 라이선싱 v1.2.0 이슈(MDP-787~793)는 발급 경로 무접촉이나, 이 파일을 고치는 PR 은 상대 트랙 리뷰를 받는다.
- 공유 컨트롤러: `AdminController`·`ProductController` (licensing import 보유).
- DB 마이그레이션: `V{yyyyMMdd}__*.sql` — 제출 전 동일 날짜 충돌 확인. **최신 = `V20260908__`** (웹).

### 병행 안전 규약 (갱신)

1. **경계**: 라이선싱 축 PR 은 `backend/…/licensing/`·`oauth/`·`resources/application*.yml`·`database/migrations/` 한정. 위 공유 접점 파일은 상호 리뷰 조건으로만.
2. **마이그레이션 파일명**: 동일 날짜 충돌 확인 (위).
3. **교집합 검사**: 상대 트랙의 **활성 브랜치가 존재할 때** 그 브랜치를 대상으로 수행 — 현재 웹 활성 브랜치 없음(main 직전 fetch 확인으로 갈음):
   ```bash
   git fetch origin && comm -12 \
     <(git diff --name-only origin/main...HEAD | sort) \
     <(git diff --name-only origin/main...origin/<상대-트랙-브랜치> | sort)
   ```
4. ~~MDP-791 순서 고정~~ — **해제** (2026-09-09).

### 웹 개발자 판단 대기

- **`products`(카탈로그) 소유권** — 이관 후 licensing vs payment/카탈로그 서비스. (`Product` 는 `entity/` 공유 커널 잔류)
- **Redis 배치** — 계정(홈페이지) 서비스 소유 vs 공용 인프라.
- (관찰) `PaymentLicenseIssueAdapter` 의 거처 — 현재 licensing 패키지가 `payment/port` 타입에 의존하는 형태라, 서비스 분리 시 이 파일은 payment 측 또는 조립 계층으로 이동이 필요하다.

> **갱신 규칙**: 트랙 기준선·차단 상태가 바뀌면 본 절을 갱신하고 MDP-332 에 코멘트를 남길 것.
