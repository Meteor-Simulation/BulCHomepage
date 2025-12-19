# Licensing System API Documentation v1.1

## 개요

BulC Homepage 라이선스 시스템의 REST API 문서입니다.

### v1.1 변경사항 (2025-12-18)

**필수 변경:**
- 계정 기반 API 추가 (Bearer token 인증)
- 복수 라이선스 선택 로직 (`licenseId` 파라미터, `LICENSE_SELECTION_REQUIRED` 409)
- `LicenseResponse`에서 `licenseKey` 필드 제거 (보안 강화)

**개선:**
- `productCode` 지원 추가 (UUID 대신 `"BULC_EVAC"` 문자열 코드)
- Offline Token을 JWS 서명 토큰으로 변경
- `serverTime` 필드 추가 (클라이언트 시간 조작 방어)
- 에러 포맷 정리 및 새로운 에러 코드 추가

### 아키텍처 원칙

```
┌─────────────────────────────────────────────────────────────────┐
│                        External Access                          │
├─────────────────────────────────────────────────────────────────┤
│  Client App         │  Admin UI           │  Billing Module     │
│  (라이선스 검증)      │  (플랜 관리)         │  (내부 호출)         │
└────────┬────────────┴────────┬────────────┴────────┬────────────┘
         │                     │                     │
         ▼                     ▼                     ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐
│ License API     │  │ Admin API       │  │ Internal Service    │
│ (HTTP 노출)     │  │ (HTTP 노출)     │  │ (HTTP 미노출)       │
├─────────────────┤  ├─────────────────┤  ├─────────────────────┤
│ POST /validate  │  │ GET  /plans     │  │ issueLicense()      │
│ POST /heartbeat │  │ POST /plans     │  │ revokeLicense()     │
│ DELETE /activate│  │ PUT  /plans/:id │  │ suspendLicense()    │
│ GET  /:id       │  │ ...             │  │ renewLicense()      │
└─────────────────┴──┴─────────────────┴──┴─────────────────────┘
```

**핵심 원칙:**
- 라이선스 **발급/정지/회수/갱신**은 HTTP API로 노출하지 않음
- 이러한 작업은 Billing/Admin 모듈에서 내부적으로 LicenseService 직접 호출
- 클라이언트는 검증/활성화/조회 API만 사용

---

## 1. Client License API (v1.1 계정 기반)

클라이언트 애플리케이션에서 사용하는 라이선스 검증/활성화 API입니다.

### Base URL
```
/api/licenses
```

### 인증
모든 v1.1 API는 Bearer token 인증이 필요합니다.
```http
Authorization: Bearer {jwt-token}
```

### 1.1 라이선스 검증 및 활성화 (v1.1)

인증된 사용자의 라이선스를 검증하고 기기를 활성화합니다.

```http
POST /api/licenses/validate
Authorization: Bearer {jwt-token}
Content-Type: application/json
```

**Request Body:**
```json
{
  "productCode": "BULC_EVAC",
  "deviceFingerprint": "hw-hash-abc123",
  "clientVersion": "1.0.0",
  "clientOs": "Windows 11"
}
```

| 필드 | 타입 | 필수 | 설명 |
|-----|------|-----|------|
| productCode | string | △ | 제품 코드 (예: "BULC_EVAC") - productId와 둘 중 하나 필수 |
| productId | UUID | △ | 제품 ID - productCode와 둘 중 하나 필수 |
| licenseId | UUID | X | 복수 라이선스 시 명시적 선택 |
| deviceFingerprint | string | O | 기기 고유 식별 해시 |
| clientVersion | string | X | 클라이언트 앱 버전 |
| clientOs | string | X | 운영체제 정보 |

**복수 라이선스 선택 로직:**
- `licenseId` 지정: 해당 라이선스 사용 (소유자 검증)
- `licenseId` 미지정:
  - 후보 0개: `LICENSE_NOT_FOUND_FOR_PRODUCT` (404)
  - 후보 1개: 자동 선택
  - 후보 2개 이상: `LICENSE_SELECTION_REQUIRED` (409) + `candidates` 반환

**Response (200 OK - 성공):**
```json
{
  "valid": true,
  "licenseId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "ACTIVE",
  "validUntil": "2025-12-31T23:59:59Z",
  "entitlements": ["core-simulation", "export-csv"],
  "offlineToken": "eyJhbGciOiJIUzI1NiJ9...",
  "offlineTokenExpiresAt": "2025-02-01T00:00:00Z",
  "serverTime": "2025-12-18T12:00:00Z",
  "errorCode": null,
  "errorMessage": null,
  "candidates": null
}
```

**Response (409 Conflict - 복수 라이선스 선택 필요):**
```json
{
  "valid": false,
  "serverTime": "2025-12-18T12:00:00Z",
  "errorCode": "LICENSE_SELECTION_REQUIRED",
  "errorMessage": "복수의 라이선스가 존재합니다. licenseId를 지정해주세요",
  "candidates": [
    {
      "licenseId": "550e8400-e29b-41d4-a716-446655440000",
      "planName": "Pro 연간 구독",
      "licenseType": "SUBSCRIPTION",
      "status": "ACTIVE",
      "validUntil": "2025-12-31T23:59:59Z",
      "ownerScope": "개인",
      "activeDevices": 1,
      "maxDevices": 3,
      "label": null
    },
    {
      "licenseId": "660f9500-f39c-52e5-b827-557766551111",
      "planName": "Standard 월간 구독",
      "licenseType": "SUBSCRIPTION",
      "status": "ACTIVE",
      "validUntil": "2025-01-31T23:59:59Z",
      "ownerScope": "개인",
      "activeDevices": 0,
      "maxDevices": 1,
      "label": null
    }
  ]
}
```

> **Note:** `serverTime` 필드는 클라이언트 시간 조작 방어용입니다.
> 클라이언트는 이 값을 기준으로 로컬 시간과 비교하여 시간 조작 여부를 감지할 수 있습니다.

---

### 1.2 Heartbeat (v1.1)

```http
POST /api/licenses/heartbeat
Authorization: Bearer {jwt-token}
Content-Type: application/json
```

**Request/Response:** `/validate`와 동일한 형식

---

### 1.3 (Legacy) 라이선스 검증 및 활성화

클라이언트 앱 실행 시 라이선스 유효성을 확인하고 기기를 활성화합니다.

```http
POST /api/licenses/{licenseKey}/validate
Content-Type: application/json
```

**Path Parameters:**
| 파라미터 | 타입 | 설명 |
|---------|------|------|
| licenseKey | string | 라이선스 키 (ex: `ABCD-1234-EFGH-5678`) |

**Request Body:**
```json
{
  "deviceFingerprint": "hw-hash-abc123",
  "clientVersion": "1.0.0",
  "clientOs": "Windows 11",
  "clientIp": "192.168.1.100"
}
```

| 필드 | 타입 | 필수 | 설명 |
|-----|------|-----|------|
| deviceFingerprint | string | O | 기기 고유 식별 해시 |
| clientVersion | string | X | 클라이언트 앱 버전 |
| clientOs | string | X | 운영체제 정보 |
| clientIp | string | X | 클라이언트 IP |

> **deviceFingerprint 구현 가이드:**
> - 하드웨어 정보(CPU ID, MAC, 디스크 시리얼 등)를 조합하여 해시 생성
> - 단방향 해시 사용 권장 (SHA-256 등)
> - 서버는 fingerprint 원본을 복호화하지 않음 (개인정보 보호)
> - 동일 기기에서는 항상 동일한 값이 생성되어야 함

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

> **Note:** `EXPIRED_GRACE` 상태에서도 `valid: true`를 반환합니다. 클라이언트는 `status` 필드를 확인하여 사용자에게 갱신 안내 메시지를 표시할 수 있습니다.

**Response (실패 시):**

> **⚠️ 설계 결정:** `/validate` API는 클라이언트 친화적인 응답 형식을 사용합니다.
> 다른 API들의 공통 에러 형식과 다르지만, 클라이언트 앱이 라이선스 검증 결과를
> 직관적으로 처리할 수 있도록 `valid` 필드를 포함합니다.

```json
{
  "valid": false,
  "errorCode": "LICENSE_EXPIRED",
  "errorMessage": "라이선스가 만료되었습니다"
}
```

**Error Codes:**
| 코드 | HTTP Status | 설명 |
|-----|-------------|------|
| LICENSE_NOT_FOUND | 404 | 라이선스 키가 존재하지 않음 |
| LICENSE_EXPIRED | 403 | 라이선스 만료 |
| LICENSE_SUSPENDED | 403 | 라이선스 정지됨 |
| LICENSE_REVOKED | 403 | 라이선스 회수됨 |
| ACTIVATION_LIMIT_EXCEEDED | 403 | 최대 기기 수 초과 |
| CONCURRENT_SESSION_LIMIT_EXCEEDED | 403 | 동시 세션 제한 초과 |

---

### 1.2 Heartbeat (주기적 검증)

클라이언트가 주기적으로 세션 상태를 갱신합니다.

```http
POST /api/licenses/{licenseKey}/heartbeat
Content-Type: application/json
```

**Request/Response:** `/validate`와 동일한 형식

#### Validate vs Heartbeat 차이점

| 구분 | Validate | Heartbeat |
|-----|----------|-----------|
| **용도** | 앱 시작 시 라이선스 검증 및 기기 활성화 | 실행 중 주기적 상태 갱신 |
| **새 Activation 생성** | O (미등록 기기 시 생성) | X (기존 활성화만 허용) |
| **lastSeenAt 갱신** | O | O |
| **호출 시점** | 앱 시작, 재인증 필요 시 | 5~15분 주기 권장 |
| **미등록 기기 응답** | 새 Activation 생성 | `ACTIVATION_NOT_FOUND` 에러 |

> **Note:** Heartbeat은 이미 활성화된 `deviceFingerprint`에 대해서만 동작합니다.
> 새로운 기기를 등록하려면 반드시 `/validate` API를 사용해야 합니다.

**Heartbeat 실패 케이스:**
- 해당 기기가 활성화되지 않은 경우 → `ACTIVATION_NOT_FOUND`
- 라이선스 만료/정지/회수 상태 → 해당 에러 코드

> **에러 응답 포맷:** `/heartbeat` 에러 응답도 `/validate`와 동일하게
> `valid: false` + `errorCode` + `errorMessage` 형식을 사용합니다.

---

### 1.3 기기 비활성화

사용자가 특정 기기에서 라이선스를 해제합니다.

```http
DELETE /api/licenses/{licenseId}/activations/{deviceFingerprint}
```

**Path Parameters:**
| 파라미터 | 타입 | 설명 |
|---------|------|------|
| licenseId | UUID | 라이선스 ID |
| deviceFingerprint | string | 비활성화할 기기 fingerprint |

**Response:**
- `204 No Content`: 성공
- `404 Not Found`: 활성화 정보 없음

---

### 1.4 라이선스 조회 (ID)

```http
GET /api/licenses/{licenseId}
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

---

### 1.5 라이선스 조회 (키)

```http
GET /api/licenses/key/{licenseKey}
```

**Response:** 위와 동일

#### 조회 API 사용 용도

| 엔드포인트 | 주요 사용처 | 설명 |
|-----------|-----------|------|
| `GET /{licenseId}` | 서버/관리자 | UUID로 직접 조회. Admin UI, 내부 시스템에서 사용 |
| `GET /key/{licenseKey}` | 클라이언트 | 라이선스 키로 조회. 사용자가 자신의 라이선스 정보 확인 시 |

> **권한 설계 시 참고:** 추후 ACL 적용 시 `/{licenseId}`는 관리자 전용,
> `/key/{licenseKey}`는 해당 라이선스 소유자만 접근 가능하도록 설정할 수 있습니다.

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
```

**Response:** 단일 플랜 객체

---

### 2.3 플랜 생성

```http
POST /api/admin/license-plans
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
```

**Response:** `200 OK` + 수정된 플랜 객체

---

### 2.6 플랜 비활성화

```http
PATCH /api/admin/license-plans/{id}/deactivate
```

비활성화된 플랜으로는 새 라이선스를 발급할 수 없습니다.

**Response:** `200 OK` + 수정된 플랜 객체

---

### 2.7 플랜 삭제

```http
DELETE /api/admin/license-plans/{id}
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

## 3. Internal Service Methods (HTTP 미노출)

아래 메서드들은 HTTP API로 노출되지 않으며, Billing/Admin 모듈에서 직접 호출합니다.

### 3.1 라이선스 발급

**Method:** `LicenseService.issueLicenseWithPlan()`

```java
// Billing 모듈에서 호출 예시
@Transactional
public void completePayment(PaymentResult result) {
    Order order = orderRepository.findById(result.orderId());
    order.markPaid(result.paidAt());

    licenseService.issueLicenseWithPlan(
        OwnerType.USER,
        order.getUserId(),
        order.getPlanId(),      // Plan ID
        order.getId(),          // Order ID (source)
        UsageCategory.COMMERCIAL
    );
}
```

Plan 기반 발급 시:
1. Plan 조회 (활성화 + 삭제되지 않은 플랜만)
2. Plan에서 PolicySnapshot 자동 생성
3. License 생성 및 ACTIVE 상태로 설정

---

### 3.2 라이선스 회수 (환불)

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

### 3.3 라이선스 정지

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

### 3.4 구독 갱신

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

## 4. Data Models

### 4.1 License Status

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

### 4.2 License Type

| 타입 | 설명 |
|-----|------|
| TRIAL | 체험판 (기간 제한, 기능 제한) |
| SUBSCRIPTION | 구독형 (기간 제한, 갱신 가능) |
| PERPETUAL | 영구 라이선스 (기간 무제한) |

### 4.3 Usage Category

라이선스의 사용 용도를 구분합니다.

| 카테고리 | 설명 | 특징 |
|---------|------|------|
| PERSONAL | 개인 사용 | 비상업적 용도, 가격 할인 적용 가능 |
| COMMERCIAL | 상업적 사용 | 기업/비즈니스 용도 |
| EDUCATIONAL | 교육용 | 학교/교육기관, 할인 적용 |
| NFR (Not For Resale) | 비매품 | 데모/파트너/내부 테스트용 |

> **Note:** UsageCategory는 라이선스 발급 시 Billing 모듈에서 결정하며,
> 발급 이후 변경되지 않습니다.

### 4.4 Activation Status

| 상태 | 설명 |
|-----|------|
| ACTIVE | 활성 상태 |
| STALE | 장기 미접속 (자동 전환) |
| DEACTIVATED | 사용자 비활성화 |
| EXPIRED | 만료됨 |

---

## 5. Error Response Format

대부분의 API는 다음 공통 에러 형식을 사용합니다.
(`/validate`, `/heartbeat`는 클라이언트 편의를 위해 별도 포맷 사용 - 섹션 1.1 참조)

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
| LICENSE_NOT_FOUND_FOR_PRODUCT | 404 | 해당 제품의 라이선스 없음 (v1.1) |
| LICENSE_SELECTION_REQUIRED | 409 | 복수 라이선스 선택 필요 (v1.1) |
| LICENSE_EXPIRED | 403 | 라이선스 만료 |
| LICENSE_SUSPENDED | 403 | 라이선스 정지 |
| LICENSE_REVOKED | 403 | 라이선스 회수 |
| LICENSE_ALREADY_EXISTS | 409 | 중복 라이선스 |
| ACCESS_DENIED | 403 | 접근 권한 없음 (v1.1 - 타인 소유 라이선스 접근 시) |
| ACTIVATION_NOT_FOUND | 404 | 활성화 정보 없음 |
| ACTIVATION_LIMIT_EXCEEDED | 403 | 기기 수 초과 |
| CONCURRENT_SESSION_LIMIT_EXCEEDED | 403 | 세션 수 초과 |
| INVALID_LICENSE_STATE | 400 | 잘못된 상태 |
| PLAN_NOT_FOUND | 404 | 플랜 없음 |
| PLAN_CODE_DUPLICATE | 409 | 플랜 코드 중복 |
| PLAN_NOT_AVAILABLE | 400 | 사용 불가 플랜 |

---

## 6. Policy Snapshot

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

## 7. Offline Token

네트워크 연결 없이도 일정 기간 라이선스를 사용할 수 있도록 하는 토큰입니다.

### 토큰 형식 (v1.1 JWS)

v1.1부터 Offline Token은 **JWS (JSON Web Signature)** 형식입니다.
클라이언트는 공개키/공유키로 서명을 검증하여 토큰의 무결성을 확인합니다.

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI1NTBlODQwMC1lMjliLTQxZDQtYTcxNi00NDY2NTU0NDAwMDAiLCJkZXZpY2VGaW5nZXJwcmludCI6ImRldmljZS0xMjMiLCJ2YWxpZFVudGlsIjoxNzM1Njg5NTk5MDAwLCJtYXhBY3RpdmF0aW9ucyI6MywiZW50aXRsZW1lbnRzIjpbImNvcmUtc2ltdWxhdGlvbiJdLCJpYXQiOjE3MDI0NTI4MDAsImV4cCI6MTcwNTA0NDgwMH0.xxxsignaturexxx
```

**JWS 페이로드:**
```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",  // licenseId
  "deviceFingerprint": "device-123",
  "validUntil": 1735689599000,  // 라이선스 만료일 (epoch ms)
  "maxActivations": 3,
  "entitlements": ["core-simulation"],
  "iat": 1702452800,  // 발급 시각
  "exp": 1705044800   // 토큰 만료 시각
}
```

| 구분 | 설명 |
|-----|------|
| 형식 | JWS (HS256 서명) |
| 서명 검증 | 클라이언트가 서명 검증으로 토큰 위변조 감지 |
| 만료 확인 | `exp` 클레임 또는 `offlineTokenExpiresAt` 확인 |
| serverTime | 서버 시간 기준으로 클라이언트 시간 조작 감지 |
| 갱신 | `/validate` 성공 시 새 토큰 발급 |

> **v1.0 vs v1.1:**
> - v1.0: Opaque Token (서버에서만 검증 가능)
> - v1.1: JWS Token (클라이언트에서도 서명 검증 가능, 시간 조작 방어 강화)

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

> **Note:** 오프라인 모드에서는 `offlineTokenExpiresAt` 만료시간만 검증합니다.
> 서버 측 검증은 온라인 복귀 후 `/validate` 호출 시 수행됩니다.

### 보안 고려사항
- 토큰은 기기 로컬에 안전하게 저장 (암호화 권장)
- 오프라인 기간(`allowOfflineDays`)이 지나면 반드시 온라인 재인증 필요
- 토큰 유출 시 서버에서 즉시 무효화 가능 (온라인 복귀 시 차단)
- 토큰당 1개 기기에만 바인딩

---

## Appendix: cURL Examples

### 라이선스 검증
```bash
curl -X POST http://localhost:8080/api/licenses/ABCD-1234-EFGH-5678/validate \
  -H "Content-Type: application/json" \
  -d '{
    "deviceFingerprint": "device-hash-123",
    "clientVersion": "1.0.0",
    "clientOs": "Windows 11"
  }'
```

### 플랜 생성 (Admin)
```bash
curl -X POST http://localhost:8080/api/admin/license-plans \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {admin-token}" \
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

### 플랜 목록 조회 (Admin)
```bash
curl http://localhost:8080/api/admin/license-plans?activeOnly=true \
  -H "Authorization: Bearer {admin-token}"
```

---

## 8. Admin License Management API (M2 추가)

관리자가 라이선스를 조회하고 검색하는 API입니다.

### Base URL
```
/api/admin/licenses
```

### 인증/권한
모든 엔드포인트는 인증 필요 (추후 `ROLE_ADMIN` 권한 적용 예정)

---

### 8.1 라이선스 검색

다양한 조건으로 라이선스를 검색합니다.

```http
GET /api/admin/licenses
GET /api/admin/licenses?status=ACTIVE
GET /api/admin/licenses?ownerType=USER&ownerId={uuid}
GET /api/admin/licenses?licenseKey=TEST
GET /api/admin/licenses?page=0&size=20
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

### 8.2 소유자별 라이선스 목록

특정 소유자(유저/조직)의 모든 라이선스를 조회합니다.

```http
GET /api/admin/licenses/owner/{ownerType}/{ownerId}
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

## 9. 실제 테스트 결과 (M2 구현 검증)

### 9.1 정상 검증 시나리오

**요청:**
```bash
curl -X POST http://localhost:8080/api/licenses/TEST-KEY-1234-ABCD/validate \
  -H "Content-Type: application/json" \
  -d '{
    "deviceFingerprint": "device-test-001",
    "clientVersion": "1.0.0",
    "clientOs": "Windows 11",
    "lastIp": "192.168.1.100"
  }'
```

**응답:**
```json
{
  "valid": true,
  "licenseId": "b4080bd4-3d55-46ba-bc1f-eaa8af0a3c64",
  "status": "ACTIVE",
  "validUntil": "2026-12-08T01:41:49.019242Z",
  "entitlements": ["core-simulation", "export-csv"],
  "offlineToken": "YjQwODBiZDQtM2Q1NS00NmJhLWJjMWYtZWFhOGFmMGEzYzY0...",
  "offlineTokenExpiresAt": "2026-01-07T01:42:18.251496600Z",
  "errorCode": null,
  "errorMessage": null
}
```

### 9.2 정지된 라이선스 검증

**응답:**
```json
{
  "valid": false,
  "licenseId": null,
  "status": null,
  "validUntil": null,
  "entitlements": null,
  "offlineToken": null,
  "offlineTokenExpiresAt": null,
  "errorCode": "LICENSE_SUSPENDED",
  "errorMessage": "라이선스가 정지되었습니다"
}
```

### 9.3 최대 활성화 초과

**응답:**
```json
{
  "valid": false,
  "licenseId": null,
  "status": null,
  "validUntil": null,
  "entitlements": null,
  "offlineToken": null,
  "offlineTokenExpiresAt": null,
  "errorCode": "ACTIVATION_LIMIT_EXCEEDED",
  "errorMessage": "최대 활성화 수에 도달했습니다"
}
```

### 9.4 라이선스 키로 상세 조회

**요청:**
```bash
curl http://localhost:8080/api/licenses/key/TEST-KEY-1234-ABCD
```

**응답:**
```json
{
  "id": "b4080bd4-3d55-46ba-bc1f-eaa8af0a3c64",
  "ownerType": "USER",
  "ownerId": "45c5b947-088e-40f3-bf3f-07e19b701c8a",
  "productId": "1da850db-68db-4fe9-aa04-e1ee673f5f44",
  "planId": null,
  "licenseType": "SUBSCRIPTION",
  "usageCategory": "COMMERCIAL",
  "status": "ACTIVE",
  "issuedAt": "2025-12-08T01:41:49.019242Z",
  "validFrom": "2025-12-08T01:41:49.019242Z",
  "validUntil": "2026-12-08T01:41:49.019242Z",
  "licenseKey": "TEST-KEY-1234-ABCD",
  "policySnapshot": {
    "maxActivations": 3,
    "maxConcurrentSessions": 2,
    "gracePeriodDays": 7,
    "allowOfflineDays": 30,
    "entitlements": ["core-simulation", "export-csv"]
  },
  "activations": [
    {
      "id": "ced50844-3453-4a46-9c72-0588594c0c9d",
      "deviceFingerprint": "device-test-001",
      "status": "ACTIVE",
      "activatedAt": "2025-12-08T01:42:18.254750Z",
      "lastSeenAt": "2025-12-08T01:42:24.226154Z",
      "clientVersion": "1.0.0",
      "clientOs": "Windows 11"
    }
  ],
  "createdAt": "2025-12-08T01:41:49.019242Z",
  "updatedAt": "2025-12-08T10:42:18.242197Z"
}
```

---

## 10. 인증/권한 설정

### 현재 구현된 Security 설정

| 엔드포인트 패턴 | 인증 | 설명 |
|---------------|-----|------|
| `/api/licenses/*/validate` | 불필요 | 클라이언트 앱 검증 |
| `/api/licenses/*/heartbeat` | 불필요 | 클라이언트 앱 heartbeat |
| `/api/licenses/key/*` | 불필요 | 라이선스 키로 조회 |
| `/api/licenses/{id}` | **필요** | ID로 상세 조회 |
| `/api/licenses/{id}/activations/*` | **필요** | 기기 비활성화 |
| `/api/admin/licenses/**` | **필요** | 관리자 검색 API |
| `/api/admin/license-plans/**` | **필요** | 플랜 관리 API |

> **Note:** `/api/licenses/key/*`는 라이선스 키를 아는 클라이언트만 접근 가능하므로
> 인증 없이 허용합니다. 키 자체가 인증 역할을 합니다.

---

## 11. 구현 현황

### M1 - 도메인 레이어 (완료)
- License Aggregate (Entity, Value Objects)
- License Repository
- License Service (Command 로직)
- Exception Handling

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

## Appendix B: 구현 파일 구조

```
backend/src/main/java/com/bulc/homepage/licensing/
├── controller/
│   ├── LicenseController.java        # 클라이언트 API
│   └── LicenseAdminController.java   # 관리자 API (M2)
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
│   └── ApiResponse.java              # 공통 응답 DTO
├── exception/
│   ├── LicenseException.java         # 커스텀 예외
│   └── LicenseExceptionHandler.java  # 예외 핸들러
├── query/                            # (M2 추가)
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

---

## Appendix C: 보안 운영 체크리스트

### JWT Secret 관리
- [ ] 운영 환경에서 `JWT_SECRET` 환경변수로 강력한 비밀키 설정 (최소 32자)
- [ ] 개발/운영 환경 간 다른 비밀키 사용
- [ ] 비밀키 주기적 로테이션 계획 수립

### API 보안
- [ ] HTTPS 필수 적용 (TLS 1.2+)
- [ ] Rate Limiting 설정 (`/validate`, `/heartbeat` 엔드포인트)
- [ ] CORS 정책 적용 (운영 도메인만 허용)

### 라이선스 키 보안
- [ ] `LicenseResponse`에서 `licenseKey` 제거 확인 (v1.1)
- [ ] 라이선스 키는 Billing 모듈에서만 발급 시점에 전달
- [ ] Admin API에서만 라이선스 키 조회 가능

### Offline Token 보안
- [ ] JWS 서명 검증 로직 클라이언트 구현
- [ ] `serverTime`과 클라이언트 시간 비교 (시간 조작 감지)
- [ ] 오프라인 토큰 로컬 저장 시 암호화

### 복수 라이선스 선택
- [ ] `LICENSE_SELECTION_REQUIRED` (409) 응답 처리 구현
- [ ] 클라이언트 UI에서 라이선스 선택 화면 구현
- [ ] `licenseId` 파라미터 전달 로직 구현

### 로깅 및 모니터링
- [ ] 검증 실패 로그 모니터링 (LICENSE_NOT_FOUND, ACCESS_DENIED 등)
- [ ] 비정상 패턴 감지 (동일 fingerprint 다수 계정, 급격한 요청 증가)
- [ ] 라이선스 상태 변경 이력 감사 로그

### 운영 시 주의사항
- [ ] 기존 v1.0 클라이언트 호환성 확인 (key 기반 API 유지)
- [ ] 신규 클라이언트는 v1.1 계정 기반 API 사용 권장
- [ ] 라이선스 만료 전 갱신 안내 이메일 설정

---

*Last Updated: 2025-12-18 (M1.5 보안 개선 - 계정 기반 API, 복수 라이선스 선택, JWS Offline Token)*
