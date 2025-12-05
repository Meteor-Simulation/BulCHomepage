# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

BulCHomepage is a full-stack web application for the METEOR fire safety simulation and training platform. It uses React (TypeScript) frontend, Spring Boot (Java 17) backend, and PostgreSQL database, all containerized with Docker.

## Development Commands

### Frontend (from /frontend)
```bash
npm start       # Dev server at localhost:3000 (hot reload)
npm run build   # Production build
npm test        # Run tests in watch mode
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
- **Frontend**: React 19 + TypeScript, React Router 6, CSS with custom properties
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
├── licensing_domain_v1.md # Licensing system domain design
├── licensing_api_v1.md    # Licensing API documentation
└── 테이블정의서.md         # Database table definitions
```

### Key Patterns
- **Frontend**: Functional components, co-located CSS files, CSS variables for theming (--accent: #C4320A)
- **Backend**: Layered architecture (controller/service/repository), Spring profiles (dev/prod)
- **Database**: snake_case naming, BIGINT identity PKs, created_at/updated_at timestamps

### API Communication
- Frontend API calls use `REACT_APP_API_URL` environment variable
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

## Licensing System

소프트웨어 라이선스 관리 시스템입니다. 자세한 내용은 `Document/licensing_api_v1.md` 참조.

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

### Client API Endpoints
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/licenses/{key}/validate` | 라이선스 검증 및 기기 활성화 |
| POST | `/api/licenses/{key}/heartbeat` | 세션 갱신 (기존 활성화만) |
| DELETE | `/api/licenses/{id}/activations/{fingerprint}` | 기기 비활성화 |
| GET | `/api/licenses/{id}` | 라이선스 조회 (ID) |
| GET | `/api/licenses/key/{key}` | 라이선스 조회 (Key) |

### Admin API Endpoints
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/license-plans` | 플랜 목록 |
| POST | `/api/admin/license-plans` | 플랜 생성 |
| PUT | `/api/admin/license-plans/{id}` | 플랜 수정 |
| DELETE | `/api/admin/license-plans/{id}` | 플랜 삭제 (soft) |

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
