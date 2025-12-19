# Licensing System API Documentation v1.1

## 변경 이력

| 버전 | 날짜 | 변경 내용 |
|-----|------|----------|
| v1.0 | 2025-12-08 | 최초 작성 (M1, M2 구현) |
| v1.1 | 2025-12-17 | M1.5 보안 개선 - 계정 기반 인증으로 전환 |

### v1.1 주요 변경사항

1. **계정 토큰 기반 인증으로 전환**: 키 기반 공개 API → Bearer token 인증 필수
2. **`/api/me/licenses` 추가**: 사용자 본인의 라이선스 목록 조회
3. **`/api/licenses/validate`, `/heartbeat` 변경**: path에서 licenseKey 제거, 토큰 기반으로 전환
4. **공개 API 제거**: `/api/licenses/key/*`, `/api/licenses/*/validate`, `/api/licenses/*/heartbeat` 비인증 접근 제거

> **Note:** Claim 기능(라이선스 키 귀속)은 v1.1에서 제외되었습니다. 추후 Redeem 기능으로 별도 구현 예정입니다.

---

## 개요

BulC Homepage 라이선스 시스템의 REST API 문서입니다.

### 아키텍처 원칙

```
┌─────────────────────────────────────────────────────────────────┐
│                        External Access                          │
├─────────────────────────────────────────────────────────────────┤
│  Client App         │  Admin UI           │  Billing Module     │
│  (라이선스 검증)      │  (플랜 관리)         │  (내부 호출)         │
│  [Bearer Token]     │  [Bearer Token]     │  [직접 호출]         │
└────────┬────────────┴────────┬────────────┴────────┬────────────┘
         │                     │                     │
         ▼                     ▼                     ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐
│ User API        │  │ Admin API       │  │ Internal Service    │
│ (HTTP 노출)     │  │ (HTTP 노출)     │  │ (HTTP 미노출)       │
├─────────────────┤  ├─────────────────┤  ├─────────────────────┤
│ GET  /me/licenses│  │ GET  /plans     │  │ issueLicense()      │
│ POST /validate   │  │ POST /plans     │  │ revokeLicense()     │
│ POST /heartbeat  │  │ PUT  /plans/:id │  │ suspendLicense()    │
│ GET  /{id}       │  │ ...             │  │ renewLicense()      │
└─────────────────┴──┴─────────────────┴──┴─────────────────────┘
```

**핵심 원칙:**
- **모든 클라이언트 API는 Bearer token 인증 필수** (v1.1 변경)
- 라이선스 **발급/정지/회수/갱신**은 HTTP API로 노출하지 않음
- 이러한 작업은 Billing/Admin 모듈에서 내부적으로 LicenseService 직접 호출

---

## 1. User License API

사용자가 자신의 라이선스를 관리하고 검증하는 API입니다. **모든 엔드포인트는 인증 필수입니다.**

### Base URL
```
/api/licenses
/api/me/licenses
```

### 인증
모든 엔드포인트는 `Authorization: Bearer {accessToken}` 헤더 필수

---

### 1.1 내 라이선스 목록 조회 (v1.1 신규)

현재 로그인한 사용자의 라이선스 목록을 조회합니다.

```http
GET /api/me/licenses
GET /api/me/licenses?productId={uuid}
GET /api/me/licenses?status=ACTIVE
Authorization: Bearer {accessToken}
```

**Query Parameters:**
| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|-------|------|
| productId | UUID | - | 특정 제품의 라이선스만 조회 |
| status | enum | - | 특정 상태의 라이선스만 조회 |

**Response (200 OK):**
```json
{
  "licenses": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "productId": "product-uuid-here",
      "productName": "METEOR Pro",
      "planName": "Pro 연간 구독",
      "licenseType": "SUBSCRIPTION",
      "status": "ACTIVE",
      "validFrom": "2024-12-05T10:00:00Z",
      "validUntil": "2025-12-05T10:00:00Z",
      "entitlements": ["core-simulation", "export-csv"],
      "usedActivations": 1,
      "maxActivations": 3
    }
  ]
}
```

> **런처 UX:** 런처는 이 API로 사용자의 라이선스 목록을 조회하고, `productId` 기준으로 자동 선택합니다.
> 예: ACTIVE 상태 우선, 없으면 EXPIRED_GRACE 선택

---

### 1.2 라이선스 검증 및 활성화 (v1.1 변경)

클라이언트 앱 실행 시 라이선스 유효성을 확인하고 기기를 활성화합니다.

> **v1.1 변경:** Path에서 licenseKey 제거, Bearer token 기반으로 변경

```http
POST /api/licenses/validate
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Request Body:**
```json
{
  "productId": "550e8400-e29b-41d4-a716-446655440000",
  "deviceFingerprint": "hw-hash-abc123",
  "clientVersion": "1.0.0",
  "clientOs": "Windows 11"
}
```

| 필드 | 타입 | 필수 | 설명 |
|-----|------|-----|------|
| productId | UUID | O | 검증할 제품 ID |
| deviceFingerprint | string | O | 기기 고유 식별 해시 |
| clientVersion | string | X | 클라이언트 앱 버전 |
| clientOs | string | X | 운영체제 정보 |

> **라이선스 선택 로직 (서버):**
> 1. `token.userId` + `productId`로 사용자의 해당 제품 라이선스 조회
> 2. 여러 개인 경우 우선순위: ACTIVE > EXPIRED_GRACE > 최신 발급순
> 3. 선택된 라이선스로 검증 및 Activation 처리

**Response (200 OK - 성공):**
```json
{
  "valid": true,
  "licenseId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "ACTIVE",
  "validUntil": "2025-12-31T23:59:59Z",
  "entitlements": ["core-simulation", "export-csv"],
  "offlineToken": "abc123-opaque-token-xyz789",
  "offlineTokenExpiresAt": "2025-02-01T00:00:00Z"
}
```

**Status → Valid 매핑:**
| License Status | valid | 설명 |
|----------------|-------|------|
| ACTIVE | true | 정상 사용 가능 |
| EXPIRED_GRACE | true | 유예 기간 (경고 표시 권장) |
| EXPIRED_HARD | false | 완전 만료 |
| SUSPENDED | false | 관리자 정지 |
| REVOKED | false | 회수됨 |
| PENDING | false | 발급 대기 |

**Response (실패 시):**
```json
{
  "valid": false,
  "errorCode": "LICENSE_NOT_FOUND",
  "errorMessage": "해당 제품의 라이선스가 없습니다"
}
```

**Error Codes:**
| 코드 | HTTP Status | 설명 |
|-----|-------------|------|
| LICENSE_NOT_FOUND | 404 | 해당 제품의 라이선스가 없음 |
| LICENSE_EXPIRED | 403 | 라이선스 만료 |
| LICENSE_SUSPENDED | 403 | 라이선스 정지됨 |
| LICENSE_REVOKED | 403 | 라이선스 회수됨 |
| ACTIVATION_LIMIT_EXCEEDED | 403 | 최대 기기 수 초과 |
| CONCURRENT_SESSION_LIMIT_EXCEEDED | 403 | 동시 세션 제한 초과 |

---

### 1.3 Heartbeat (주기적 검증) (v1.1 변경)

클라이언트가 주기적으로 세션 상태를 갱신합니다.

> **v1.1 변경:** Path에서 licenseKey 제거, Bearer token 기반으로 변경

```http
POST /api/licenses/heartbeat
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Request Body:**
```json
{
  "productId": "550e8400-e29b-41d4-a716-446655440000",
  "deviceFingerprint": "hw-hash-abc123",
  "clientVersion": "1.0.0",
  "clientOs": "Windows 11"
}
```

**Response:** `/validate`와 동일한 형식

#### Validate vs Heartbeat 차이점

| 구분 | Validate | Heartbeat |
|-----|----------|-----------|
| **용도** | 앱 시작 시 라이선스 검증 및 기기 활성화 | 실행 중 주기적 상태 갱신 |
| **새 Activation 생성** | O (미등록 기기 시 생성) | X (기존 활성화만 허용) |
| **lastSeenAt 갱신** | O | O |
| **호출 시점** | 앱 시작, 재인증 필요 시 | 5~15분 주기 권장 |
| **미등록 기기 응답** | 새 Activation 생성 | `ACTIVATION_NOT_FOUND` 에러 |

---

### 1.4 라이선스 상세 조회 (v1.1 변경)

본인 소유의 라이선스 상세 정보를 조회합니다.

> **v1.1 변경:** 인증 필수, 본인 소유 라이선스만 조회 가능

```http
GET /api/licenses/{licenseId}
Authorization: Bearer {accessToken}
```

**Response (200 OK):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "ownerType": "USER",
  "ownerId": "user-uuid-here",
  "productId": "product-uuid-here",
  "planId": "plan-uuid-here",
  "licenseType": "SUBSCRIPTION",
  "usageCategory": "COMMERCIAL",
  "status": "ACTIVE",
  "issuedAt": "2024-12-05T10:00:00Z",
  "validFrom": "2024-12-05T10:00:00Z",
  "validUntil": "2025-12-05T10:00:00Z",
  "licenseKey": "ABCD-1234-EFGH-5678",
  "policySnapshot": {
    "maxActivations": 3,
    "maxConcurrentSessions": 2,
    "gracePeriodDays": 7,
    "allowOfflineDays": 30,
    "entitlements": ["core-simulation", "export-csv"]
  },
  "activations": [
    {
      "id": "activation-uuid",
      "deviceFingerprint": "device-hash",
      "status": "ACTIVE",
      "activatedAt": "2024-12-05T11:00:00Z",
      "lastSeenAt": "2024-12-05T12:00:00Z",
      "clientVersion": "1.0.0",
      "clientOs": "Windows 11"
    }
  ],
  "createdAt": "2024-12-05T10:00:00Z",
  "updatedAt": "2024-12-05T12:00:00Z"
}
```

**Error Response:**
| 상황 | HTTP Status | Error Code |
|-----|-------------|-----------|
| 라이선스 없음 | 404 | LICENSE_NOT_FOUND |
| 권한 없음 (타인 소유) | 403 | ACCESS_DENIED |

---

### 1.5 기기 비활성화 (v1.1 변경)

사용자가 특정 기기에서 라이선스를 해제합니다.

> **v1.1 변경:** 인증 필수, 본인 소유 라이선스의 기기만 비활성화 가능

```http
DELETE /api/licenses/{licenseId}/activations/{deviceFingerprint}
Authorization: Bearer {accessToken}
```

**Path Parameters:**
| 파라미터 | 타입 | 설명 |
|---------|------|------|
| licenseId | UUID | 라이선스 ID |
| deviceFingerprint | string | 비활성화할 기기 fingerprint |

**Response:**
- `204 No Content`: 성공
- `404 Not Found`: 활성화 정보 없음
- `403 Forbidden`: 권한 없음

---

### 1.6 (Deprecated) 라이선스 키로 조회

> **v1.1 Deprecated:** 보안상 이 엔드포인트는 제거됩니다.
> 대신 `/api/me/licenses` 또는 `/api/licenses/{licenseId}`를 사용하세요.

```http
# v1.0 (Deprecated - v1.2에서 제거 예정)
GET /api/licenses/key/{licenseKey}
```

---

## 2. Admin License Plan API

관리자가 라이선스 플랜(정책 템플릿)을 관리하는 API입니다.

### Base URL
```
/api/admin/license-plans
```

### 인증/권한
모든 엔드포인트는 `ROLE_ADMIN` 권한 필요

---

### 2.1 플랜 목록 조회

```http
GET /api/admin/license-plans
GET /api/admin/license-plans?activeOnly=true
GET /api/admin/license-plans?productId={uuid}
GET /api/admin/license-plans?page=0&size=20&sort=createdAt,desc
Authorization: Bearer {adminToken}
```

**Query Parameters:**
| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|-------|------|
| activeOnly | boolean | false | 활성화된 플랜만 조회 |
| productId | UUID | - | 특정 제품의 플랜만 조회 |
| page | int | 0 | 페이지 번호 |
| size | int | 20 | 페이지 크기 |
| sort | string | createdAt,desc | 정렬 기준 |

**Response (200 OK):**
```json
{
  "content": [
    {
      "id": "plan-uuid",
      "productId": "product-uuid",
      "code": "PRO_SUB_1Y",
      "name": "Pro 연간 구독",
      "description": "전체 기능 포함 연간 구독",
      "licenseType": "SUBSCRIPTION",
      "durationDays": 365,
      "graceDays": 7,
      "maxActivations": 3,
      "maxConcurrentSessions": 2,
      "allowOfflineDays": 30,
      "active": true,
      "deleted": false,
      "entitlements": ["core-simulation", "advanced-visualization", "export-csv"],
      "createdAt": "2024-12-01T00:00:00Z",
      "updatedAt": "2024-12-05T10:00:00Z"
    }
  ],
  "pageable": { ... },
  "totalElements": 10,
  "totalPages": 1
}
```

---

### 2.2 플랜 상세 조회

```http
GET /api/admin/license-plans/{id}
Authorization: Bearer {adminToken}
```

**Response:** 단일 플랜 객체

---

### 2.3 플랜 생성

```http
POST /api/admin/license-plans
Authorization: Bearer {adminToken}
Content-Type: application/json
```

**Request Body:**
```json
{
  "productId": "550e8400-e29b-41d4-a716-446655440000",
  "code": "PRO_SUB_1Y",
  "name": "Pro 연간 구독",
  "description": "전체 기능 포함 연간 구독",
  "licenseType": "SUBSCRIPTION",
  "durationDays": 365,
  "graceDays": 7,
  "maxActivations": 3,
  "maxConcurrentSessions": 2,
  "allowOfflineDays": 30,
  "entitlements": ["core-simulation", "advanced-visualization", "export-csv"]
}
```

| 필드 | 타입 | 필수 | 검증 | 설명 |
|-----|------|-----|------|------|
| productId | UUID | O | - | 제품 ID |
| code | string | O | NotBlank | 플랜 코드 (unique) |
| name | string | O | NotBlank | 플랜 이름 |
| description | string | X | - | 설명 |
| licenseType | enum | O | - | TRIAL, SUBSCRIPTION, PERPETUAL |
| durationDays | int | O | >= 0 | 유효 기간 (일) |
| graceDays | int | O | >= 0 | 유예 기간 (일) |
| maxActivations | int | O | >= 1 | 최대 기기 수 |
| maxConcurrentSessions | int | O | >= 1 | 동시 세션 제한 |
| allowOfflineDays | int | O | >= 0 | 오프라인 허용 일수 |
| entitlements | string[] | X | - | 활성화 기능 목록 |

**Response:** `201 Created` + 생성된 플랜 객체

**Errors:**
- `409 Conflict`: 플랜 코드 중복 (`PLAN_CODE_DUPLICATE`)

---

### 2.4 플랜 수정

```http
PUT /api/admin/license-plans/{id}
Authorization: Bearer {adminToken}
Content-Type: application/json
```

**Request Body:** 생성과 동일

**Response:** `200 OK` + 수정된 플랜 객체

> **주의:** 플랜 수정 시 기존에 발급된 라이선스는 영향받지 않습니다.
> 라이선스는 발급 시점의 PolicySnapshot을 저장하기 때문입니다.

---

### 2.5 플랜 활성화

```http
PATCH /api/admin/license-plans/{id}/activate
Authorization: Bearer {adminToken}
```

**Response:** `200 OK` + 수정된 플랜 객체

---

### 2.6 플랜 비활성화

```http
PATCH /api/admin/license-plans/{id}/deactivate
Authorization: Bearer {adminToken}
```

비활성화된 플랜으로는 새 라이선스를 발급할 수 없습니다.

**Response:** `200 OK` + 수정된 플랜 객체

---

### 2.7 플랜 삭제

```http
DELETE /api/admin/license-plans/{id}
Authorization: Bearer {adminToken}
```

**Soft Delete 동작:**
- 플랜은 물리적으로 삭제되지 않고 `is_deleted = true`로 표시
- 삭제된 플랜은 목록 조회에 표시되지 않음
- 삭제된 플랜으로는 새 라이선스 발급 불가
- **기존 발급된 라이선스는 영향받지 않음** (PolicySnapshot 사용)

| 상태 | 목록 조회 | 새 라이선스 발급 | 기존 라이선스 |
|-----|---------|----------------|--------------|
| active=true, deleted=false | O | O | 정상 동작 |
| active=false, deleted=false | O | X | 정상 동작 |
| deleted=true | X | X | 정상 동작 |

**Response:** `204 No Content`

---

## 3. Admin License Management API

관리자가 라이선스를 조회하고 검색하는 API입니다.

### Base URL
```
/api/admin/licenses
```

### 인증/권한
모든 엔드포인트는 `ROLE_ADMIN` 권한 필요

---

### 3.1 라이선스 검색

다양한 조건으로 라이선스를 검색합니다.

```http
GET /api/admin/licenses
GET /api/admin/licenses?status=ACTIVE
GET /api/admin/licenses?ownerType=USER&ownerId={uuid}
GET /api/admin/licenses?licenseKey=TEST
GET /api/admin/licenses?page=0&size=20
Authorization: Bearer {adminToken}
```

**Query Parameters:**
| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|-------|------|
| ownerType | enum | - | 소유자 유형 (USER, ORG) |
| ownerId | UUID | - | 소유자 ID |
| productId | UUID | - | 제품 ID |
| status | enum | - | 라이선스 상태 |
| licenseType | enum | - | 라이선스 유형 |
| licenseKey | string | - | 라이선스 키 (부분 일치 검색) |
| page | int | 0 | 페이지 번호 |
| size | int | 20 | 페이지 크기 |

**Response (200 OK):**
```json
{
  "content": [
    {
      "id": "b4080bd4-3d55-46ba-bc1f-eaa8af0a3c64",
      "ownerType": "USER",
      "ownerId": "45c5b947-088e-40f3-bf3f-07e19b701c8a",
      "productId": "1da850db-68db-4fe9-aa04-e1ee673f5f44",
      "licenseType": "SUBSCRIPTION",
      "usageCategory": "COMMERCIAL",
      "status": "ACTIVE",
      "validFrom": "2025-12-08T01:41:49.019242Z",
      "validUntil": "2026-12-08T01:41:49.019242Z",
      "licenseKey": "TEST-KEY-1234-ABCD",
      "usedActivations": 1,
      "maxActivations": 3,
      "createdAt": "2025-12-08T01:41:49.019242Z"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20
  },
  "totalElements": 1,
  "totalPages": 1
}
```

---

### 3.2 소유자별 라이선스 목록

특정 소유자(유저/조직)의 모든 라이선스를 조회합니다.

```http
GET /api/admin/licenses/owner/{ownerType}/{ownerId}
Authorization: Bearer {adminToken}
```

**Path Parameters:**
| 파라미터 | 타입 | 설명 |
|---------|------|------|
| ownerType | enum | USER 또는 ORG |
| ownerId | UUID | 소유자 ID |

**Response (200 OK):**
```json
[
  {
    "id": "b4080bd4-3d55-46ba-bc1f-eaa8af0a3c64",
    "ownerType": "USER",
    "ownerId": "45c5b947-088e-40f3-bf3f-07e19b701c8a",
    "productId": "1da850db-68db-4fe9-aa04-e1ee673f5f44",
    "licenseType": "SUBSCRIPTION",
    "usageCategory": "COMMERCIAL",
    "status": "ACTIVE",
    "validFrom": "2025-12-08T01:41:49.019242Z",
    "validUntil": "2026-12-08T01:41:49.019242Z",
    "licenseKey": "TEST-KEY-1234-ABCD",
    "usedActivations": 1,
    "maxActivations": 3,
    "createdAt": "2025-12-08T01:41:49.019242Z"
  }
]
```

---

## 4. Internal Service Methods (HTTP 미노출)

아래 메서드들은 HTTP API로 노출되지 않으며, Billing/Admin 모듈에서 직접 호출합니다.

### 4.1 라이선스 발급

**Method:** `LicenseService.issueLicenseWithPlan()`

```java
// Billing 모듈에서 호출 예시
@Transactional
public void completePayment(PaymentResult result) {
    Order order = orderRepository.findById(result.orderId());
    order.markPaid(result.paidAt());

    licenseService.issueLicenseWithPlan(
        OwnerType.USER,
        order.getUserId(),      // 구매자에게 자동 귀속
        order.getPlanId(),
        order.getId(),
        UsageCategory.COMMERCIAL
    );
}
```

Plan 기반 발급 시:
1. Plan 조회 (활성화 + 삭제되지 않은 플랜만)
2. Plan에서 PolicySnapshot 자동 생성
3. License 생성 및 ACTIVE 상태로 설정
4. **ownerId 자동 설정** → Claim 불필요

---

### 4.2 라이선스 회수 (환불)

**Method:** `LicenseService.revokeLicenseByOrderId()`

```java
// Billing 모듈에서 환불 시 호출
@Transactional
public void processRefund(RefundResult result) {
    Order order = orderRepository.findById(result.orderId());
    order.markRefunded();

    licenseService.revokeLicenseByOrderId(order.getId(), "REFUNDED");
}
```

회수 시:
- License 상태 → REVOKED
- 모든 기기 활성화 해제
- 이후 검증 시도 시 `LICENSE_REVOKED` 에러

---

### 4.3 라이선스 정지

**Method:** `LicenseService.suspendLicense()`

```java
// Admin 모듈에서 관리자가 정지
licenseService.suspendLicense(licenseId, "약관 위반");
```

정지 시:
- License 상태 → SUSPENDED
- 검증 시도 시 `LICENSE_SUSPENDED` 에러
- REVOKED와 달리 복구 가능

---

### 4.4 구독 갱신

**Method:** `LicenseService.renewLicense()`

```java
// Billing 모듈에서 구독 갱신 결제 완료 시
@Transactional
public void processRenewal(RenewalResult result) {
    License license = findByOrderId(result.originalOrderId());

    Instant newValidUntil = license.getValidUntil()
        .plus(365, ChronoUnit.DAYS);

    licenseService.renewLicense(license.getId(), newValidUntil);
}
```

---

## 5. Data Models

### 5.1 License Status

```
PENDING → ACTIVE → EXPIRED_GRACE → EXPIRED_HARD
                ↓
            SUSPENDED (복구 가능)
                ↓
            REVOKED (복구 불가)
```

| 상태 | 설명 | 검증 결과 |
|-----|------|----------|
| PENDING | 발급 대기 | 실패 |
| ACTIVE | 정상 사용 가능 | 성공 |
| EXPIRED_GRACE | 유예 기간 (제한적 사용) | 성공 (경고) |
| EXPIRED_HARD | 완전 만료 | 실패 |
| SUSPENDED | 관리자 정지 | 실패 |
| REVOKED | 회수됨 (환불 등) | 실패 |

### 5.2 License Type

| 타입 | 설명 |
|-----|------|
| TRIAL | 체험판 (기간 제한, 기능 제한) |
| SUBSCRIPTION | 구독형 (기간 제한, 갱신 가능) |
| PERPETUAL | 영구 라이선스 (기간 무제한) |

### 5.3 Usage Category

| 카테고리 | 설명 | 특징 |
|---------|------|------|
| PERSONAL | 개인 사용 | 비상업적 용도 |
| COMMERCIAL | 상업적 사용 | 기업/비즈니스 용도 |
| EDUCATIONAL | 교육용 | 학교/교육기관 |
| NFR | 비매품 | 데모/파트너/내부 테스트용 |

### 5.4 Activation Status

| 상태 | 설명 |
|-----|------|
| ACTIVE | 활성 상태 |
| STALE | 장기 미접속 (자동 전환) |
| DEACTIVATED | 사용자 비활성화 |
| EXPIRED | 만료됨 |

---

## 6. Error Response Format

대부분의 API는 다음 공통 에러 형식을 사용합니다.
(`/validate`, `/heartbeat`는 클라이언트 편의를 위해 별도 포맷 사용)

```json
{
  "error": "ERROR_CODE",
  "message": "사람이 읽을 수 있는 에러 메시지",
  "timestamp": "2024-12-05T10:00:00Z"
}
```

### Error Codes

| 코드 | HTTP | 설명 |
|-----|------|------|
| LICENSE_NOT_FOUND | 404 | 라이선스 없음 |
| LICENSE_EXPIRED | 403 | 라이선스 만료 |
| LICENSE_SUSPENDED | 403 | 라이선스 정지 |
| LICENSE_REVOKED | 403 | 라이선스 회수 |
| ACCESS_DENIED | 403 | 권한 없음 |
| ACTIVATION_NOT_FOUND | 404 | 활성화 정보 없음 |
| ACTIVATION_LIMIT_EXCEEDED | 403 | 기기 수 초과 |
| CONCURRENT_SESSION_LIMIT_EXCEEDED | 403 | 세션 수 초과 |
| INVALID_LICENSE_STATE | 400 | 잘못된 상태 |
| PLAN_NOT_FOUND | 404 | 플랜 없음 |
| PLAN_CODE_DUPLICATE | 409 | 플랜 코드 중복 |
| PLAN_NOT_AVAILABLE | 400 | 사용 불가 플랜 |

---

## 7. Policy Snapshot

라이선스 발급 시 Plan의 정책이 snapshot으로 저장됩니다.

```json
{
  "maxActivations": 3,
  "maxConcurrentSessions": 2,
  "gracePeriodDays": 7,
  "allowOfflineDays": 30,
  "entitlements": ["core-simulation", "export-csv"]
}
```

**Snapshot 원칙:**
- 플랜 수정 시 기존 라이선스는 영향받지 않음
- 라이선스는 발급 시점의 정책을 기준으로 동작
- 새 정책은 새로 발급되는 라이선스에만 적용

---

## 8. Offline Token

네트워크 연결 없이도 일정 기간 라이선스를 사용할 수 있도록 하는 토큰입니다.

### 토큰 형식

Offline Token은 **Opaque Token**입니다. 서버에서 생성한 고유 문자열이며,
클라이언트는 토큰 내용을 해석하지 않고 만료 시간만 확인합니다.

| 구분 | 설명 |
|-----|------|
| 형식 | Opaque Token (서버에서 생성한 고유 문자열) |
| 서버 저장 | 토큰 값을 DB에 저장 (온라인 복귀 시 검증용) |
| 오프라인 검증 | 클라이언트가 `offlineTokenExpiresAt` 기준으로 만료 여부만 판단 |
| 갱신 | `/validate` 성공 시 새 토큰 발급 |

### 발급 조건
- `/validate` API 성공 시 자동 발급
- `allowOfflineDays > 0` 인 경우에만 발급
- 유효 기간: `allowOfflineDays` 일

### 클라이언트 사용 흐름

```
┌─────────────────────────────────────────────────────────────┐
│ 1. 온라인 상태                                               │
│    └─ /validate 호출 → offlineToken + offlineTokenExpiresAt │
│                        수신 후 로컬 저장                     │
├─────────────────────────────────────────────────────────────┤
│ 2. 오프라인 상태                                             │
│    └─ 현재시간 < offlineTokenExpiresAt → 앱 사용 허용        │
│    └─ 현재시간 >= offlineTokenExpiresAt → 앱 사용 차단       │
├─────────────────────────────────────────────────────────────┤
│ 3. 온라인 복귀                                               │
│    └─ /validate 재호출 → 서버가 토큰 유효성 최종 확인        │
│    └─ 새 offlineToken 발급                                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 9. 인증/권한 설정 (v1.1 변경)

### v1.1 Security 설정

| 엔드포인트 패턴 | 인증 | 권한 | 설명 |
|---------------|-----|------|------|
| `POST /api/licenses/validate` | **필요** | USER | 라이선스 검증 |
| `POST /api/licenses/heartbeat` | **필요** | USER | Heartbeat |
| `GET /api/me/licenses` | **필요** | USER | 내 라이선스 목록 |
| `GET /api/licenses/{id}` | **필요** | USER (본인 소유) | 상세 조회 |
| `DELETE /api/licenses/{id}/activations/*` | **필요** | USER (본인 소유) | 기기 비활성화 |
| `GET /api/licenses/key/*` | **제거** | - | v1.1에서 제거 |
| `/api/admin/licenses/**` | **필요** | ADMIN | 관리자 검색 API |
| `/api/admin/license-plans/**` | **필요** | ADMIN | 플랜 관리 API |

### v1.0 → v1.1 마이그레이션 가이드

**제거되는 엔드포인트:**
```
# v1.0 (제거)
POST /api/licenses/{licenseKey}/validate    → POST /api/licenses/validate
POST /api/licenses/{licenseKey}/heartbeat   → POST /api/licenses/heartbeat
GET  /api/licenses/key/{licenseKey}         → GET  /api/me/licenses 또는 /api/licenses/{id}
```

**클라이언트 변경사항:**
1. 모든 API 호출에 `Authorization: Bearer {accessToken}` 헤더 추가
2. validate/heartbeat 요청에 `productId` 필드 추가
3. 라이선스 키 입력 UX는 Claim API로 대체

---

## 10. UX 플로우 (v1.1)

### 기본 플로우: Sign in → Launch

```
┌──────────────────────────────────────────────────────────────────┐
│ 1. 사용자 로그인                                                   │
│    └─ POST /api/auth/login → accessToken 획득                    │
├──────────────────────────────────────────────────────────────────┤
│ 2. 내 라이선스 조회                                                │
│    └─ GET /api/me/licenses?productId=xxx                         │
│    └─ 서버가 해당 제품의 라이선스 목록 반환                          │
├──────────────────────────────────────────────────────────────────┤
│ 3. 라이선스 검증 및 기기 활성화                                     │
│    └─ POST /api/licenses/validate (productId, deviceFingerprint) │
│    └─ offlineToken 저장                                          │
├──────────────────────────────────────────────────────────────────┤
│ 4. 앱 실행                                                        │
│    └─ 주기적으로 POST /api/licenses/heartbeat                     │
└──────────────────────────────────────────────────────────────────┘
```

---

## Appendix A: cURL Examples (v1.1)

### 내 라이선스 목록 조회
```bash
curl http://localhost:8080/api/me/licenses \
  -H "Authorization: Bearer {accessToken}"
```

### 라이선스 검증 (v1.1)
```bash
curl -X POST http://localhost:8080/api/licenses/validate \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{
    "productId": "550e8400-e29b-41d4-a716-446655440000",
    "deviceFingerprint": "device-hash-123",
    "clientVersion": "1.0.0",
    "clientOs": "Windows 11"
  }'
```

### Heartbeat (v1.1)
```bash
curl -X POST http://localhost:8080/api/licenses/heartbeat \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{
    "productId": "550e8400-e29b-41d4-a716-446655440000",
    "deviceFingerprint": "device-hash-123",
    "clientVersion": "1.0.0",
    "clientOs": "Windows 11"
  }'
```

### 플랜 생성 (Admin)
```bash
curl -X POST http://localhost:8080/api/admin/license-plans \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {adminToken}" \
  -d '{
    "productId": "550e8400-e29b-41d4-a716-446655440000",
    "code": "TRIAL_14D",
    "name": "14일 체험판",
    "licenseType": "TRIAL",
    "durationDays": 14,
    "graceDays": 0,
    "maxActivations": 1,
    "maxConcurrentSessions": 1,
    "allowOfflineDays": 0,
    "entitlements": ["core-simulation"]
  }'
```

---

## Appendix B: 구현 현황

### M1 - 도메인 레이어 (완료)
- License Aggregate (Entity, Value Objects)
- License Repository
- License Service (Command 로직)
- Exception Handling

### M1.5 - 보안 개선 (완료)
- [x] `/api/me/licenses` 엔드포인트 추가
- [x] validate/heartbeat Bearer token 인증 적용
- [x] `/api/licenses/key/*` 공개 접근 제거
- [x] Security 설정 변경

> **Note:** Claim 기능은 추후 Redeem 기능으로 별도 구현 예정

### M2 - Read 레이어 (완료)
- Query Service (CQRS 패턴)
- View DTOs (LicenseDetailView, LicenseSummaryView)
- QueryDSL 기반 동적 검색
- Admin Controller

### 향후 계획
- M3: License Plan Admin API
- M4: Billing 연동
- M5: 오프라인 토큰 고도화

---

## Appendix C: 구현 파일 구조

```
backend/src/main/java/com/bulc/homepage/licensing/
├── controller/
│   ├── LicenseController.java        # 사용자 API (v1.1 변경)
│   └── LicenseAdminController.java   # 관리자 API
├── domain/
│   ├── License.java                  # Aggregate Root
│   ├── LicenseActivation.java        # 기기 활성화 Entity
│   ├── LicenseStatus.java            # 상태 Enum
│   ├── LicenseType.java              # 타입 Enum
│   ├── UsageCategory.java            # 용도 Enum
│   ├── OwnerType.java                # 소유자 유형 Enum
│   └── ActivationStatus.java         # 활성화 상태 Enum
├── dto/
│   ├── ActivationRequest.java        # 검증 요청 DTO
│   ├── ValidationResponse.java       # 검증 응답 DTO
│   ├── ValidateRequest.java          # v1.1 검증 요청 DTO
│   └── ApiResponse.java              # 공통 응답 DTO
├── exception/
│   ├── LicenseException.java         # 커스텀 예외
│   └── LicenseExceptionHandler.java  # 예외 핸들러
├── query/
│   ├── LicenseQueryService.java      # Query 인터페이스
│   ├── LicenseQueryServiceImpl.java  # Query 구현
│   ├── LicenseQueryRepository.java   # Query Repository
│   ├── LicenseQueryRepositoryImpl.java
│   ├── LicenseSearchCond.java        # 검색 조건 DTO
│   └── view/
│       ├── LicenseDetailView.java    # 상세 조회 View
│       ├── LicenseSummaryView.java   # 목록 조회 View
│       ├── PolicySnapshotView.java   # 정책 스냅샷 View
│       └── ActivationView.java       # 활성화 정보 View
├── repository/
│   └── LicenseRepository.java        # JPA Repository
└── service/
    └── LicenseService.java           # Command Service
```

---

*Last Updated: 2025-12-17 (M1.5 API 보안 개선 문서화)*
