# Licensing System API Documentation

> **API Base URL:** `/api/v1`
> **Document Version:** `0.3.0`
> **Release Target:** `1.0.0`

---

## 변경 이력

| 버전 | 날짜 | 변경 내용 |
|-----|------|----------|
| v0.1.0 | 2025-12-08 | 최초 작성 (M1, M2 구현) |
| v0.2.0 | 2025-12-17 | M1.5 보안 개선 - 계정 기반 인증으로 전환 |
| v0.2.1 | 2025-12-23 | 동시 세션 관리 UX 개선 - force-validate, 세션 선택 UI 지원 |
| v0.2.2 | 2025-12-30 | sessionToken (RS256 JWS) 추가 - CLI 위/변조 방어 |
| v0.2.3 | 2026-01-07 | 토큰 구조 명확화 (sessionToken + offlineToken), 문서 정비 |
| v0.3.0 | 2026-01-08 | Auto-Resolve + Global Session Kick UX |

### v0.3.0 주요 변경사항

1. **UX 원칙 변경**: "사용자 선택은 정말 막혔을 때만" - 자동 해결 우선
2. **Global Session Kick**: 라이선스 선택 + 세션 종료를 단일 UI로 통합
3. **`strategy` 필드 제거**: 서버가 항상 최적 라이선스 자동 선택
4. **Stale 자동 정리**: 서버가 stale session 자동 종료 후 재시도
5. **새로운 응답 필드**: `resolution` (OK, AUTO_RECOVERED, USER_ACTION_REQUIRED)
6. **통합 세션 목록**: 409 시 모든 후보 라이선스의 세션을 한 번에 반환
7. **에러 코드 통합**: `MULTIPLE_LICENSES_FOUND`, `CONCURRENT_SESSION_LIMIT_EXCEEDED` → `ALL_LICENSES_FULL`

### v0.2.3 주요 변경사항

1. **토큰 구조 통일**: sessionToken/offlineToken 모두 RS256 JWS로 통일 (오프라인 검증 가능)
2. **offlineToken 스펙 정비**: claims 통일(iss, aud, typ, dfp, ent), absolute cap(`exp ≤ validUntil`)
3. **오프라인 보안 강화**: 시스템 시간 조작 방어 가이드라인 추가
4. **ValidateRequest 필드 추가**: `productCode`, `licenseId`, `deviceDisplayName` 문서화
5. **409 Conflict 응답 문서화**: 다중 라이선스/동시 세션 초과 시 응답 형식
6. **force-validate 정교화**: DEACTIVATED 마킹 및 경쟁 조건 방어 명시
7. **운영 가이드 추가**: Heartbeat write-behind, 개인정보 처리 정책

### v0.2.1 주요 변경사항

1. **force-validate 엔드포인트 추가**: 동시 세션 초과 시 기존 세션 강제 해제 후 활성화
2. **세션 선택 UI 지원**: 동시 세션 초과 시 활성 세션 목록 반환 (409 Conflict)
3. **라이선스 선택 UI 지원**: 다중 라이선스 존재 시 후보 목록 반환 (409 Conflict)
4. **deviceDisplayName 필드**: 기기 표시명 지원 (UX 개선)

### v0.2.2 주요 변경사항

1. **sessionToken 필드 추가**: validate/heartbeat 응답에 RS256 서명된 JWS 토큰 포함
2. **클라이언트 검증 강화**: sessionToken 서명 검증 필수 (CLI 바꿔치기/session.json 조작 방어)
3. **기기 바인딩**: deviceFingerprint가 토큰에 포함되어 다른 기기 복사 방지
4. **짧은 TTL**: 기본 15분 만료로 재사용 공격 제한

### v0.2.0 주요 변경사항

1. **계정 토큰 기반 인증으로 전환**: 키 기반 공개 API → Bearer token 인증 필수
2. **`/api/v1/me/licenses` 추가**: 사용자 본인의 라이선스 목록 조회
3. **`/api/v1/licenses/validate`, `/heartbeat` 변경**: path에서 licenseKey 제거, 토큰 기반으로 전환
4. **공개 API 제거**: `/api/v1/licenses/key/*`, `/api/v1/licenses/*/validate`, `/api/v1/licenses/*/heartbeat` 비인증 접근 제거

> **Note:** Claim 기능(라이선스 키 귀속)은 v0.2.0에서 제외되었습니다. 추후 Redeem 기능으로 별도 구현 예정입니다.

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
- **모든 클라이언트 API는 Bearer token 인증 필수** (v0.2.0 변경)
- 라이선스 **발급/정지/회수/갱신**은 HTTP API로 노출하지 않음
- 이러한 작업은 Billing/Admin 모듈에서 내부적으로 LicenseService 직접 호출

### 응답 신뢰 모델 (SOT 규칙)

> **⚠️ 필수 준수사항** - 클라이언트 구현 시 반드시 따라야 하는 핵심 규칙입니다.

- **기능 unlock의 SOT(Source of Truth)는 `sessionToken`(및 오프라인 시 `offlineToken`)이다.**
- 클라이언트는 **토큰 서명/클레임 검증이 성공한 경우에만** 기능을 unlock한다.
- 응답 JSON 본체의 `valid`, `status`, `entitlements` 등은 **표시/디버깅/호환성 목적의 참고 값**이며,
  **권한 판단에 사용하지 않는다.**
- 응답 본체와 토큰의 값이 불일치하면 **무조건 토큰을 기준으로 동작**한다.

**기대 동작 요약:**
| 상황 | 클라이언트 동작 |
|-----|---------------|
| 토큰 검증 실패/없음 | **무조건 invalid** - 기능 unlock 금지 |
| 토큰 검증 성공 | **unlock**, 응답 본체는 UI/로그에만 사용 |
| 본체와 토큰 값 불일치 | **토큰 기준으로 동작**, 본체는 폐기 (경고 로그 권장) |

---

## 1. User License API

사용자가 자신의 라이선스를 관리하고 검증하는 API입니다. **모든 엔드포인트는 인증 필수입니다.**

### Base URL
```
/api/v1/licenses
/api/v1/me/licenses
```

### 인증
모든 엔드포인트는 `Authorization: Bearer {accessToken}` 헤더 필수

---

### 1.1 내 라이선스 목록 조회 (v0.2.0 신규)

현재 로그인한 사용자의 라이선스 목록을 조회합니다.

```http
GET /api/v1/me/licenses
GET /api/v1/me/licenses?productId={uuid}
GET /api/v1/me/licenses?status=ACTIVE
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

### 1.2 라이선스 검증 및 활성화 (v0.2.0 변경)

클라이언트 앱 실행 시 라이선스 유효성을 확인하고 기기를 활성화합니다.

> **v0.2.0 변경:** Path에서 licenseKey 제거, Bearer token 기반으로 변경

```http
POST /api/v1/licenses/validate
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Request Body:**
```json
{
  "productCode": "BULC_EVAC",
  "deviceFingerprint": "hw-hash-abc123",
  "clientVersion": "1.0.0",
  "clientOs": "Windows 11",
  "deviceDisplayName": "John's Work PC"
}
```

| 필드 | 타입 | 필수 | 설명 |
|-----|------|-----|------|
| productCode | string | △ | 제품 코드 (예: "BULC_EVAC"). productId 또는 productCode 중 하나 필수 |
| productId | UUID | △ | 제품 ID (UUID). productCode 권장 |
| licenseId | UUID | X | 명시적 라이선스 지정 (자동 선택 bypass용, 설정 메뉴 등) |
| deviceFingerprint | string | O | 기기 고유 식별 해시 |
| clientVersion | string | X | 클라이언트 앱 버전 |
| clientOs | string | X | 운영체제 정보 |
| deviceDisplayName | string | X | 기기 표시명 (UX용, 예: "John's Work PC") |

> **v0.3.0 UX 원칙**: "사용자 선택은 정말 막혔을 때만"
> - 서버가 항상 최적 라이선스를 자동 선택 (capacity 기반)
> - stale session 자동 종료 후 재시도
> - 모든 라이선스가 Full 상태일 때만 사용자 개입 요청

> **서버 라이선스 선택 로직 (v0.3.0):**
> 1. `licenseId`가 있으면 해당 라이선스 직접 사용 (override)
> 2. 없으면 `token.userId` + `productCode/productId`로 후보 라이선스 조회
> 3. 후보별 capacity 평가 (슬롯 여유, stale 세션 존재 여부 등)
> 4. 자동 해결 시도:
>    - 빈 슬롯 있는 라이선스 선택 → OK
>    - stale session 자동 종료 → 재평가 → AUTO_RECOVERED
>    - 모두 Full & Not Stale → USER_ACTION_REQUIRED (KICK_REQUIRED)

**Response (200 OK - 성공):**
```json
{
  "valid": true,
  "resolution": "OK",
  "licenseId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "ACTIVE",
  "validUntil": "2026-12-31T23:59:59Z",
  "entitlements": ["core-simulation", "export-csv"],
  "sessionToken": "eyJ...",
  "offlineToken": "eyJ...",
  "offlineTokenExpiresAt": "2026-02-06T00:00:00Z",
  "serverTime": "2026-01-07T10:00:00Z"
}
```

**응답 필드 설명:**
| 필드 | 용도 | 비고 |
|-----|------|-----|
| `valid` | UI 표시용 | ⚠️ **권한 판단 Deprecated** - 토큰 검증 사용 |
| `status` | UI 표시용 | ⚠️ **권한 판단 Deprecated** - 토큰 검증 사용 |
| `entitlements` | UI 표시용 | ⚠️ **권한 판단 Deprecated** - 토큰 `ent` 클레임 사용 |
| `sessionToken` | **SOT (온라인)** | RS256 서명 검증 후 `ent` 기준 기능 unlock |
| `offlineToken` | **SOT (오프라인)** | RS256 서명 검증 후 `ent` 기준 기능 unlock |
| `resolution` | UX 처리 | OK, AUTO_RECOVERED, USER_ACTION_REQUIRED |
| `serverTime` | 시간 동기화 | 클라이언트 시계 보정용 |

> **⚠️ SOT 주의사항**: 응답 본체의 `valid`, `status`, `entitlements` 필드는 **UI 표시/디버깅 목적의 참고 값**입니다.
> 기능 unlock 판단은 반드시 `sessionToken`(온라인) 또는 `offlineToken`(오프라인)의 **서명 검증 및 클레임**을 기준으로 하세요.
> 자세한 내용은 상단의 "응답 신뢰 모델 (SOT 규칙)" 섹션을 참조하세요.

**Response (200 OK - AUTO_RECOVERED):** *(v0.3.0)*

서버가 stale session을 자동 종료 후 성공한 경우:

```json
{
  "valid": true,
  "resolution": "AUTO_RECOVERED",
  "recoveryAction": "STALE_SESSION_TERMINATED",
  "recoveryDetails": {
    "terminatedCount": 1,
    "terminatedDevice": "Office Desktop",
    "reason": "30분 이상 비활성 세션 자동 종료"
  },
  "licenseId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "ACTIVE",
  "sessionToken": "eyJ...",
  "offlineToken": "eyJ...",
  "serverTime": "2026-01-07T10:00:00Z"
}
```

> **클라이언트 UX**: Toast/Snackbar로 "다른 기기(Office Desktop)의 비활성 세션을 종료하고 시작했습니다." 표시 (3초)

**Status → Valid 매핑 (참고용/UI 표시용):**

> ⚠️ 이 테이블은 응답 본체의 `valid` 필드 참고용입니다. **권한 판단의 SOT는 토큰**입니다.

| License Status | valid (참고) | 설명 |
|----------------|-------------|------|
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

**Response (409 Conflict - USER_ACTION_REQUIRED):** *(v0.3.0 Global Session Kick)*

모든 후보 라이선스가 Full 상태이고 stale session도 없을 때, 사용자가 세션을 선택해야 합니다.
**핵심 변경**: `activeSessions`에 `licenseId`, `productName` 포함 (어떤 라이선스의 세션인지 구분).

```json
{
  "valid": false,
  "resolution": "USER_ACTION_REQUIRED",
  "actionRequired": "KICK_REQUIRED",
  "errorCode": "ALL_LICENSES_FULL",
  "errorMessage": "사용 가능한 라이선스가 없습니다. 접속을 위해 종료할 세션을 선택해주세요.",
  "serverTime": "2026-01-07T10:00:00Z",
  "activeSessions": [
    {
      "licenseId": "550e8400-e29b-41d4-a716-446655440000",
      "productName": "METEOR Pro",
      "planName": "Pro 연간 구독",
      "activationId": "act-uuid-1",
      "deviceDisplayName": "Office Desktop",
      "deviceFingerprint": "abc***xyz",
      "lastSeenAt": "2026-01-07T10:30:00Z",
      "clientOs": "Windows 11",
      "isStale": false
    },
    {
      "licenseId": "b4080bd4-3d55-46ba-bc1f-eaa8af0a3c64",
      "productName": "METEOR Basic",
      "planName": "Basic 월간 구독",
      "activationId": "act-uuid-2",
      "deviceDisplayName": "Home Laptop",
      "deviceFingerprint": "def***uvw",
      "lastSeenAt": "2026-01-07T09:00:00Z",
      "clientOs": "macOS 14",
      "isStale": true
    }
  ]
}
```

> **클라이언트 UX (Global Session Selector):**
> 1. 모든 후보 라이선스의 세션을 통합 UI로 표시
> 2. 각 세션에 `productName`, `planName` 표시 (어떤 라이선스인지 구분)
> 3. `isStale: true` 세션은 "비활성" 배지 표시
> 4. 사용자가 선택한 세션의 `licenseId`, `activationId`로 `/validate/force` 호출

**Error Codes:**
| 코드 | HTTP Status | 설명 |
|-----|-------------|------|
| LICENSE_NOT_FOUND | 404 | 해당 제품의 라이선스가 없음 |
| LICENSE_EXPIRED | 403 | 라이선스 만료 |
| LICENSE_SUSPENDED | 403 | 라이선스 정지됨 |
| LICENSE_REVOKED | 403 | 라이선스 회수됨 |
| ACTIVATION_LIMIT_EXCEEDED | 403 | 최대 기기 수 초과 |
| ALL_LICENSES_FULL | 409 | 모든 후보 라이선스 Full (v0.3.0: Global Session Kick 필요) |

---

### 1.3 Heartbeat (주기적 검증) (v0.2.0 변경)

클라이언트가 주기적으로 세션 상태를 갱신합니다.

> **v0.2.0 변경:** Path에서 licenseKey 제거, Bearer token 기반으로 변경

```http
POST /api/v1/licenses/heartbeat
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Request Body:** `/validate`와 동일한 형식

**Response:** `/validate`와 동일한 형식

> **⚠️ SOT 주의사항**: Heartbeat 응답도 validate와 동일하게, `valid`, `status`, `entitlements`는 **참고용**입니다.
> 기능 unlock 판단은 `sessionToken`/`offlineToken` 토큰 검증 기준입니다.

#### Validate vs Heartbeat 차이점

| 구분 | Validate | Heartbeat |
|-----|----------|-----------|
| **용도** | 앱 시작 시 라이선스 검증 및 기기 활성화 | 실행 중 주기적 상태 갱신 |
| **새 Activation 생성** | O (미등록 기기 시 생성) | X (기존 활성화만 허용) |
| **lastSeenAt 갱신** | O | O |
| **호출 시점** | 앱 시작, 재인증 필요 시 | 5~15분 주기 권장 |
| **미등록 기기 응답** | 새 Activation 생성 | `ACTIVATION_NOT_FOUND` 에러 |

#### 운영/성능 참고 (Write-behind)

Heartbeat는 `lastSeenAt` UPDATE를 빈번하게 발생시켜 DB 부하를 유발할 수 있습니다.

**초기 (소규모):** RDB에 직접 UPDATE OK

**트래픽 증가 시 (권장 아키텍처):**
```
┌─────────────┐      ┌─────────────┐      ┌─────────────┐
│  Client     │ ───> │  Redis      │ ───> │  PostgreSQL │
│  Heartbeat  │      │  lastSeenAt │      │  Bulk UPDATE│
└─────────────┘      └─────────────┘      └─────────────┘
                           │
                     배치 스케줄러
                     (N분 주기)
```

- Heartbeat는 Redis에 `activation:{id}:lastSeenAt` 갱신 (TTL 포함)
- 배치/스케줄러가 N분마다 Redis → RDB bulk update
- DB lock 경합 및 I/O 감소, 동시 세션 판단은 Redis 기준으로 수행

---

### 1.3.1 Force Validate (세션 강제 해제) *(v0.3.0 업데이트)*

사용자가 선택한 세션을 강제 비활성화하고 현재 기기를 활성화합니다.

> **사용 시나리오 (v0.3.0):** `/validate`에서 `ALL_LICENSES_FULL` (409) 응답 시,
> 사용자가 Global Session Selector에서 종료할 세션을 선택한 후 이 엔드포인트 호출.
> **핵심**: 선택한 세션의 `licenseId`와 `activationId`를 사용

```http
POST /api/v1/licenses/validate/force
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Request Body:**
```json
{
  "licenseId": "550e8400-e29b-41d4-a716-446655440000",
  "deviceFingerprint": "hw-hash-abc123",
  "deactivateActivationIds": ["act-uuid-1", "act-uuid-2"],
  "clientVersion": "1.0.0",
  "clientOs": "Windows 11",
  "deviceDisplayName": "New Device"
}
```

| 필드 | 타입 | 필수 | 설명 |
|-----|------|-----|------|
| licenseId | UUID | O | 활성화할 라이선스 ID |
| deviceFingerprint | string | O | 현재 기기 fingerprint |
| deactivateActivationIds | UUID[] | O | 비활성화할 세션 ID 목록 (최소 1개) |
| clientVersion | string | X | 클라이언트 앱 버전 |
| clientOs | string | X | 운영체제 정보 |
| deviceDisplayName | string | X | 기기 표시명 |

**Response (200 OK):** `/validate` 성공 응답과 동일

> **⚠️ SOT 주의사항**: force-validate 응답도 validate와 동일하게, `valid`, `status`, `entitlements`는 **참고용**입니다.

**서버 처리 규칙:**
1. `deactivateActivationIds`의 각 activation을 **DEACTIVATED** 상태로 마킹
2. 해제된 activation으로 들어오는 이후 heartbeat/validate 요청은 즉시 거부
3. 현재 기기에 대해 새 activation 생성 또는 기존 activation 갱신
4. 트랜잭션 내 pessimistic lock으로 경쟁 조건 방어

**Error Codes:**
| 코드 | HTTP Status | 설명 |
|-----|-------------|------|
| INVALID_ACTIVATION_IDS | 400 | 비활성화 대상 ID가 유효하지 않음 |
| ACCESS_DENIED | 403 | 본인 소유 라이선스가 아님 |
| ACTIVATION_DEACTIVATED | 403 | 해당 activation은 force-validate로 종료됨 |

> **Note:** 기존 세션의 클라이언트는 다음 heartbeat에서 `ACTIVATION_DEACTIVATED`를 받고
> "다른 기기에서 로그인되어 세션이 종료되었습니다" 안내 후 종료해야 합니다.

---

### 1.4 라이선스 상세 조회 (v0.2.0 변경)

본인 소유의 라이선스 상세 정보를 조회합니다.

> **v0.2.0 변경:** 인증 필수, 본인 소유 라이선스만 조회 가능

```http
GET /api/v1/licenses/{licenseId}
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

### 1.5 기기 비활성화 (v0.2.0 변경)

사용자가 특정 기기에서 라이선스를 해제합니다.

> **v0.2.0 변경:** 인증 필수, 본인 소유 라이선스의 기기만 비활성화 가능

```http
DELETE /api/v1/licenses/{licenseId}/activations/{deviceFingerprint}
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

> **v0.2.0 Deprecated:** 보안상 이 엔드포인트는 제거됩니다.
> 대신 `/api/v1/me/licenses` 또는 `/api/v1/licenses/{licenseId}`를 사용하세요.

```http
# v0.1.0 (Deprecated - v1.0.0에서 제거 예정)
GET /api/v1/licenses/key/{licenseKey}
```

---

## 2. Admin License Plan API

관리자가 라이선스 플랜(정책 템플릿)을 관리하는 API입니다.

### Base URL
```
/api/v1/admin/license-plans
```

### 인증/권한
모든 엔드포인트는 `ROLE_ADMIN` 권한 필요

---

### 2.1 플랜 목록 조회

```http
GET /api/v1/admin/license-plans
GET /api/v1/admin/license-plans?activeOnly=true
GET /api/v1/admin/license-plans?productId={uuid}
GET /api/v1/admin/license-plans?page=0&size=20&sort=createdAt,desc
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
GET /api/v1/admin/license-plans/{id}
Authorization: Bearer {adminToken}
```

**Response:** 단일 플랜 객체

---

### 2.3 플랜 생성

```http
POST /api/v1/admin/license-plans
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
PUT /api/v1/admin/license-plans/{id}
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
PATCH /api/v1/admin/license-plans/{id}/activate
Authorization: Bearer {adminToken}
```

**Response:** `200 OK` + 수정된 플랜 객체

---

### 2.6 플랜 비활성화

```http
PATCH /api/v1/admin/license-plans/{id}/deactivate
Authorization: Bearer {adminToken}
```

비활성화된 플랜으로는 새 라이선스를 발급할 수 없습니다.

**Response:** `200 OK` + 수정된 플랜 객체

---

### 2.7 플랜 삭제

```http
DELETE /api/v1/admin/license-plans/{id}
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
/api/v1/admin/licenses
```

### 인증/권한
모든 엔드포인트는 `ROLE_ADMIN` 권한 필요

---

### 3.1 라이선스 검색

다양한 조건으로 라이선스를 검색합니다.

```http
GET /api/v1/admin/licenses
GET /api/v1/admin/licenses?status=ACTIVE
GET /api/v1/admin/licenses?ownerType=USER&ownerId={uuid}
GET /api/v1/admin/licenses?licenseKey=TEST
GET /api/v1/admin/licenses?page=0&size=20
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
GET /api/v1/admin/licenses/owner/{ownerType}/{ownerId}
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
| PENDING | 발급 대기 (결제 확인 중, 관리자 승인 대기 등) | 실패 |
| ACTIVE | 정상 사용 가능 | 성공 |
| EXPIRED_GRACE | 유예 기간 (제한적 사용) | 성공 (경고) |
| EXPIRED_HARD | 완전 만료 | 실패 |
| SUSPENDED | 관리자 정지 | 실패 |
| REVOKED | 회수됨 (환불 등) | 실패 |

**PENDING 상태가 되는 경우:**
- 무통장 입금 대기 (결제 확인 전)
- 관리자 승인형 플랜 (기관 구매 프로세스)
- 비동기 결제 처리 중 (PG 응답 대기)

> **Note:** 일반적인 카드/실시간 결제는 즉시 ACTIVE로 전환됩니다.
> PENDING은 "결제 완료 전" 또는 "승인 프로세스가 있는 플랜"에서만 사용됩니다.

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
| ACTIVATION_DEACTIVATED | 403 | force-validate로 종료된 세션 |
| ACTIVATION_LIMIT_EXCEEDED | 403 | 기기 수 초과 |
| ALL_LICENSES_FULL | 409 | 모든 후보 라이선스가 세션 포화 (v0.3.0 통합) |
| INVALID_ACTIVATION_IDS | 400 | 비활성화 대상 ID가 유효하지 않음 |
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

## 8. 토큰 구조 (v0.2.3 정리)

### 8.0 토큰이 SOT(Source of Truth)인 이유

> **⚠️ 핵심 원칙**: 기능 unlock의 최종 권위는 **토큰(sessionToken/offlineToken)**입니다.

**왜 응답 본체가 아닌 토큰이 SOT인가?**

| 관점 | 응답 본체 (`valid`, `status`) | 토큰 (`sessionToken`, `offlineToken`) |
|-----|-------------------------------|--------------------------------------|
| **변조 방지** | JSON은 누구나 수정 가능 | RS256 서명으로 위/변조 불가 |
| **오프라인 검증** | 서버 접속 없이 검증 불가 | 공개키로 로컬 검증 가능 |
| **기기 바인딩** | 바인딩 없음 | `dfp` 클레임으로 기기 강제 |
| **만료 관리** | 별도 구현 필요 | `exp` 클레임으로 자동 만료 |

**클라이언트 구현 규칙:**
1. **토큰 검증 우선**: 응답을 받으면 먼저 토큰 서명/클레임을 검증
2. **토큰 기준 동작**: `valid: true`여도 토큰 검증 실패 시 기능 unlock 금지
3. **불일치 시 토큰 우선**: 응답 본체와 토큰 값이 다르면 토큰 기준으로 동작
4. **응답 본체 용도**: UI 표시, 로깅, 디버깅에만 사용

---

라이선스 시스템은 **두 종류의 토큰**을 사용합니다:

| 토큰 | 용도 | 알고리즘 | TTL | 발급 시점 |
|-----|------|---------|-----|----------|
| **sessionToken** | 온라인 세션, 앱 기능 unlock | RS256 (RSA) | 10~30분 (기본 15분) | Validate/Heartbeat 매번 |
| **offlineToken** | 오프라인 허용 (Offline Grant) | RS256 (RSA) | `allowOfflineDays` 기반 | Validate/Heartbeat 갱신 |

> **왜 둘 다 RS256인가?**
> - 클라이언트가 오프라인에서 토큰을 검증하려면 **비대칭키(RS256)** 필수
> - HS256은 secret 공유 필요 → 유출 시 공격자가 토큰 위조 가능
> - 동일한 RSA 키 쌍으로 두 토큰 모두 서명/검증 가능 (운영 단순화)

### 8.1 sessionToken (온라인 세션 토큰)

**RS256 서명된 JWS** 형식의 짧은 TTL 토큰입니다. CLI 바꿔치기, session.json 조작 방어용.

**특징:**
- 서버 개인키(RS256)로 서명 → 클라이언트 내장 공개키로 검증
- 짧은 TTL (15분) → 탈취되어도 빠르게 무효화
- Validate/Heartbeat **매번 새로 발급** (단순한 구조)

**Claims:**
| Claim | 타입 | 의미 |
|-------|------|------|
| `iss` | string | 발급자 (`bulc-license-server`) |
| `aud` | string | 제품 코드 (`BULC_EVAC`) |
| `sub` | string | licenseId |
| `dfp` | string | deviceFingerprint (기기 바인딩) |
| `ent` | string[] | entitlements 배열 |
| `iat` | number | 발급 시각 (epoch seconds) |
| `exp` | number | 만료 시각 (epoch seconds) |

**클라이언트 검증 (필수):**
1. RS256 서명 검증 (내장 공개키)
2. `aud` == productCode
3. `dfp` == 현재 기기 fingerprint
4. `exp` > now (±2분 clock skew 허용)
5. `ent` 기반 기능 unlock

**sessionToken 만료 시 Soft Fail (권장):**

네트워크 끊김으로 sessionToken이 만료되어도 즉시 앱을 종료하지 마세요:

1. 백그라운드에서 heartbeat 재시도 (Exponential backoff: 1s → 2s → 4s → ... 최대 60s)
2. 일정 유예 시간(예: 최대 1시간) 동안 기존 기능 유지
3. 유예 시간 내 복구 실패 시:
   - offlineToken이 유효하면 오프라인 모드로 전환
   - 작업 중인 데이터 저장 유도 후 앱 종료
4. 복구 성공 시 정상 운영 재개

> **왜 Soft Fail인가?** 일시적 네트워크 장애로 사용자의 작업 데이터가 손실되면 UX가 매우 나빠집니다.
> offlineToken과 조합하여 graceful degradation을 구현하세요.

### 8.2 offlineToken (오프라인 허용 토큰)

**RS256 서명된 JWS** 형식의 긴 TTL 토큰입니다. 오프라인 환경에서 라이선스 사용 허용.

**특징:**
- 서버 개인키(RS256)로 서명 → 클라이언트 내장 공개키로 오프라인 검증
- TTL: `policySnapshot.allowOfflineDays` 기반 (예: 7/30/90일)
- Validate에서 **필수 발급**, Heartbeat에서 **갱신 (sliding)**
- **Absolute cap**: `exp`는 `license.validUntil`을 초과할 수 없음

**Claims (sessionToken과 통일):**
| Claim | 타입 | 의미 |
|-------|------|------|
| `iss` | string | 발급자 (`bulc-license-server`) |
| `aud` | string | 제품 코드 (`BULC_EVAC`) |
| `sub` | string | licenseId |
| `typ` | string | 토큰 타입 (`"offline"`) |
| `dfp` | string | deviceFingerprint (기기 바인딩) |
| `ent` | string[] | entitlements 배열 |
| `iat` | number | 발급 시각 (epoch seconds) |
| `exp` | number | 오프라인 허용 만료 시각 (epoch seconds) |

> **sliding 갱신 정책:**
> - Heartbeat 성공 시 `exp = now + allowOfflineDays` 로 갱신
> - 단, `exp`는 `license.validUntil`을 초과할 수 없음 (absolute cap)
> - 라이선스 만료 후에는 오프라인 사용도 불가

**갱신 임계값 (서버 CPU 최적화):**

offlineToken 갱신은 RS256 서명 연산을 수반하므로, 매 Heartbeat마다 갱신하지 않습니다:

```
갱신 조건 (둘 중 하나 만족 시):
1. 남은 기간 < 전체 TTL의 50%
2. exp - now < thresholdDays (예: 3일)
```

| 설정 | 기본값 | 설명 |
|-----|-------|------|
| `renewal-threshold-ratio` | 0.5 | 남은 기간이 50% 미만일 때 갱신 |
| `renewal-threshold-days` | 3 | 또는 남은 기간이 3일 미만일 때 갱신 |

> **예시:** allowOfflineDays=30일, 현재 남은 기간 20일 → 갱신 안 함
> 남은 기간 14일(< 50%) → 갱신 수행

### 8.3 발급 조건

| 토큰 | 발급 조건 |
|-----|----------|
| sessionToken | RS256 키 설정 시 항상 발급 (미설정 시 null) |
| offlineToken | `allowOfflineDays > 0`일 때 발급 |

### 8.4 클라이언트 사용 흐름

```
┌──────────────────────────────────────────────────────────────────┐
│ 1. 온라인 상태 (앱 시작)                                           │
│    └─ POST /validate 호출                                        │
│    └─ sessionToken + offlineToken 수신                           │
│    └─ sessionToken 검증 → 앱 기능 unlock                         │
│    └─ offlineToken 로컬 저장 (오프라인 대비)                       │
├──────────────────────────────────────────────────────────────────┤
│ 2. 온라인 상태 (실행 중)                                           │
│    └─ POST /heartbeat 주기적 호출 (5~15분)                        │
│    └─ 새 sessionToken 수신 → 기존 토큰 교체                       │
│    └─ offlineToken 갱신 (sliding window)                         │
├──────────────────────────────────────────────────────────────────┤
│ 3. 오프라인 상태                                                   │
│    └─ offlineToken.exp > now → 앱 사용 허용 (제한적)              │
│    └─ offlineToken.exp <= now → 앱 사용 차단                      │
├──────────────────────────────────────────────────────────────────┤
│ 4. 온라인 복귀                                                     │
│    └─ POST /validate 재호출 → 서버가 최종 상태 확인                │
│    └─ 새 sessionToken + offlineToken 발급                        │
└──────────────────────────────────────────────────────────────────┘
```

### 8.5 방어되는 공격

| 공격 | sessionToken 방어 | offlineToken 방어 |
|------|------------------|-------------------|
| CLI 바꿔치기 | RS256 개인키 없이 위조 불가 | RS256 개인키 없이 위조 불가 |
| session.json 조작 | 서명 검증 실패 | 서명 검증 실패 |
| 다른 PC 복사 | `dfp` 불일치로 거부 | `dfp` 불일치로 거부 |
| 토큰 재사용 | 15분 TTL로 제한 | allowOfflineDays TTL + absolute cap |
| 오프라인 무기한 연장 | - | `exp ≤ license.validUntil` 제한 |
| 시스템 시간 조작 | - | 8.6 참조 |

---

### 8.6 오프라인 모드 보안 - 시스템 시간 조작 방어

offlineToken은 `exp` claim으로 만료를 검증하지만, 사용자가 시스템 시간을 과거로 되돌리면 우회할 수 있습니다.

**클라이언트 필수 구현사항:**

1. **lastKnownServerTime 저장**
   - Secure Storage(KeyChain/Credential Manager)에 `lastKnownServerTime` (epoch seconds) 저장
   - `/validate`, `/heartbeat` 성공 응답의 `serverTime`으로 갱신

2. **시간 역행 감지**
   ```
   allowedSkew = 120  // 2분 허용
   if (systemTime < lastKnownServerTime - allowedSkew) {
       // 시간 조작 감지 → 앱 실행 차단
       showError("시스템 시간이 올바르지 않습니다. 네트워크 연결 후 재시도하세요.")
       // 온라인 복귀 시 /validate로 시간 동기화 유도
   }
   ```

3. **Monotonic Clock 병행 (권장)**
   - 부팅 이후 경과 시간(monotonic)과 wall-clock을 함께 저장
   - 앱 재시작 시 두 값의 변화량 비교로 시간 조작 추가 감지

**클라이언트 로컬 에러:**
| 코드 | 설명 |
|-----|------|
| `LOCAL_CLOCK_TAMPER_DETECTED` | 시스템 시간 역행 감지 (클라이언트 내부 처리) |

> **Note:** 이 에러는 서버에 전송되지 않으며, 클라이언트가 로컬에서 판단하여 앱 실행을 차단합니다.

### 8.6 Stale 세션 판정 기준 (v0.3.0)

서버는 세션이 **stale**(비활성)인지 판단하여 Auto-Resolve 시 자동 종료 대상을 결정합니다.

**Stale 판정 조건:**
```
Stale = (now - activation.lastSeenAt) > staleThresholdMinutes
```

| 설정 | 기본값 | 설명 |
|-----|-------|------|
| `stale-threshold-minutes` | 30 | Heartbeat 없이 경과한 시간 임계값 |

**동작:**
| 상황 | Auto-Resolve 동작 | 결과 |
|-----|------------------|------|
| 빈 슬롯 있음 | 해당 라이선스 선택 | `resolution: OK` |
| 모두 Full, Stale 있음 | Stale 세션 자동 종료 | `resolution: AUTO_RECOVERED` |
| 모두 Full, Stale 없음 | 사용자 선택 요청 | `resolution: USER_ACTION_REQUIRED` |

**Stale Activation 자동 정리 정책:**

> **v0.3.0 기본값: OFF** (보수적 운영)

Stale 세션의 **Activation 레코드**를 자동 삭제(deactivate)하는 정책은 기본적으로 비활성화입니다.
- Stale 세션은 "현재 연결 안 됨" 상태이지만, 사용자가 다시 돌아올 수 있음
- 자동 삭제 시 사용자가 기기 재등록해야 하는 불편 발생
- 플랜별로 정책을 설정하여 활성화 가능 (`cleanupStaleActivations: true`)

```yaml
bulc:
  licensing:
    stale-threshold-minutes: 30
    cleanup-stale-activations: false  # v0.3.0 기본값
```

---

## 9. 인증/권한 설정 (v0.2.0 변경)

### Security 설정

| 엔드포인트 패턴 | 인증 | 권한 | 설명 |
|---------------|-----|------|------|
| `POST /api/v1/licenses/validate` | **필요** | USER | 라이선스 검증 |
| `POST /api/v1/licenses/heartbeat` | **필요** | USER | Heartbeat |
| `GET /api/v1/me/licenses` | **필요** | USER | 내 라이선스 목록 |
| `GET /api/v1/licenses/{id}` | **필요** | USER (본인 소유) | 상세 조회 |
| `DELETE /api/v1/licenses/{id}/activations/*` | **필요** | USER (본인 소유) | 기기 비활성화 |
| `GET /api/v1/licenses/key/*` | **제거** | - | v0.2.0에서 제거 |
| `/api/v1/admin/licenses/**` | **필요** | ADMIN | 관리자 검색 API |
| `/api/v1/admin/license-plans/**` | **필요** | ADMIN | 플랜 관리 API |

### v0.1.0 → v0.2.0 마이그레이션 가이드

**제거되는 엔드포인트:**
```
# v0.1.0 (제거)
POST /api/v1/licenses/{licenseKey}/validate    → POST /api/v1/licenses/validate
POST /api/v1/licenses/{licenseKey}/heartbeat   → POST /api/v1/licenses/heartbeat
GET  /api/v1/licenses/key/{licenseKey}         → GET  /api/v1/me/licenses 또는 /api/v1/licenses/{id}
```

**클라이언트 변경사항:**
1. 모든 API 호출에 `Authorization: Bearer {accessToken}` 헤더 추가
2. validate/heartbeat 요청에 `productId` 필드 추가
3. 라이선스 키 입력 UX는 Claim API로 대체

### 인증(Bearer) vs 토큰 검증 책임 분리

| 구분 | 책임 | 검증 주체 |
|-----|------|----------|
| **Bearer Token (accessToken)** | "요청자(user)가 누구인가" 인증 | 서버 |
| **sessionToken / offlineToken** | "클라이언트가 기능을 unlock해도 되는가" 판단 | 클라이언트 |
| **정책 강제 (activation/concurrency/status)** | 최종 권위 | 서버 |

> **왜 서버에서 "클라이언트 검증"을 하지 않는가?**
> - Bearer는 "요청자 신원"을 확인할 뿐, 클라이언트 앱의 진위 여부를 보장하지 않음
> - sessionToken/offlineToken은 **클라이언트가 RS256 서명을 검증**하여 "서버가 승인한 세션인가" 판단
> - 서버는 라이선스 상태, 기기 수, 동시 세션 등 **정책을 최종 강제**하고, 클라이언트는 토큰 검증으로 **기능 unlock 여부를 결정**

---

## 10. UX 플로우 (v0.3.0 업데이트)

### 핵심 원칙: "사용자 선택은 정말 막혔을 때만"

v0.3.0에서는 **Auto-Resolve 우선** 원칙을 따릅니다:
1. 서버가 자동으로 최적 라이선스 선택
2. Stale 세션은 서버가 자동 종료
3. 사용자 개입은 모든 라이선스가 활성 세션으로 가득 찼을 때만 요청

### 기본 플로우: Sign in → Launch

```
┌──────────────────────────────────────────────────────────────────┐
│ 1. 사용자 로그인                                                   │
│    └─ POST /api/v1/auth/login → accessToken 획득                  │
├──────────────────────────────────────────────────────────────────┤
│ 2. 라이선스 검증 요청 (서버 자동 선택)                               │
│    └─ POST /api/v1/licenses/validate                             │
│    └─ Body: { productCode, deviceFingerprint, ... }              │
│    └─ 서버가 후보 라이선스 중 최적 자동 선택                         │
├──────────────────────────────────────────────────────────────────┤
│ 3. 응답 처리                                                      │
│    ├─ 200 OK (resolution: "OK") → 바로 앱 실행                     │
│    ├─ 200 OK (resolution: "AUTO_RECOVERED") → Toast 표시 후 실행   │
│    └─ 409 (resolution: "USER_ACTION_REQUIRED") → 세션 선택 UI      │
├──────────────────────────────────────────────────────────────────┤
│ 4. 앱 실행                                                        │
│    └─ 주기적으로 POST /api/v1/licenses/heartbeat                   │
│    └─ sessionToken/offlineToken 갱신                              │
└──────────────────────────────────────────────────────────────────┘
```

### 라이선스 선택 로직 (서버 자동)

```
후보 라이선스 탐색:
  │
  ├─ 빈 슬롯 있는 라이선스 발견 → 해당 라이선스 선택 → OK
  │
  ├─ 모두 Full이지만 Stale 세션 있음 → Stale 자동 종료 → AUTO_RECOVERED
  │
  └─ 모두 Full & Stale 없음 → 409 USER_ACTION_REQUIRED (KICK_REQUIRED)
```

### Global Session Kick 플로우

```
409 ALL_LICENSES_FULL 응답 수신
        │
        ▼
┌─────────────────────────────────────┐
│   Global Session Selector UI        │
│                                     │
│  [METEOR Pro - 연간 구독]           │
│  ├─ 🖥️ Office Desktop              │
│  └─ 💻 Home Laptop                  │
│                                     │
│  [METEOR Standard - 월간 구독]      │
│  └─ 📱 Tablet (비활성 30분+)        │
│                                     │
│  [종료할 세션 선택] [취소]            │
└─────────────────────────────────────┘
        │
        ▼ 사용자 선택
        │
POST /api/v1/licenses/validate/force
  └─ licenseId: 선택한 세션의 라이선스
  └─ deactivateActivationIds: [선택한 세션 ID]
        │
        ▼
200 OK → 앱 실행
```

### AUTO_RECOVERED 토스트 예시

```
┌────────────────────────────────────────┐
│ ℹ️ 오래된 세션이 자동 정리되었습니다      │
│    Office Desktop (30분 이상 비활성)    │
└────────────────────────────────────────┘
```

---

## Appendix A: cURL Examples

### 내 라이선스 목록 조회
```bash
curl http://localhost:8080/api/v1/me/licenses \
  -H "Authorization: Bearer {accessToken}"
```

### 라이선스 검증
```bash
curl -X POST http://localhost:8080/api/v1/licenses/validate \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{
    "productId": "550e8400-e29b-41d4-a716-446655440000",
    "deviceFingerprint": "device-hash-123",
    "clientVersion": "1.0.0",
    "clientOs": "Windows 11"
  }'
```

### Heartbeat
```bash
curl -X POST http://localhost:8080/api/v1/licenses/heartbeat \
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
curl -X POST http://localhost:8080/api/v1/admin/license-plans \
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

## Appendix B: 토큰 상세 스펙 (참조)

> **Note:** sessionToken과 offlineToken의 상세 스펙은 **섹션 8. 토큰 구조**를 참조하세요.

### Backend 설정

```yaml
bulc:
  licensing:
    # RS256 키 쌍 (sessionToken + offlineToken 공용)
    rsa-keys:
      private-key: ${LICENSE_RSA_PRIVATE_KEY:}  # Base64 PKCS#8 또는 PEM
      public-key: ${LICENSE_RSA_PUBLIC_KEY:}    # 클라이언트 배포용

    session-token:
      ttl-minutes: 15              # sessionToken 만료 시간 (온라인)
      issuer: bulc-license-server

    offline-token:
      issuer: bulc-license-server
      # TTL은 policySnapshot.allowOfflineDays 기반 (플랜별 설정)
      renewal-threshold-ratio: 0.5  # 남은 기간이 50% 미만일 때만 갱신
```

> **Note:** sessionToken과 offlineToken은 동일한 RSA 키 쌍을 사용합니다.
> 클라이언트는 공개키 하나만 내장하면 두 토큰 모두 검증 가능합니다.

### 예시 sessionToken (디코딩)

**Header:**
```json
{
  "alg": "RS256",
  "typ": "JWT"
}
```

**Payload:**
```json
{
  "iss": "bulc-license-server",
  "aud": "BULC_EVAC",
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "dfp": "hw-hash-abc123",
  "ent": ["core-simulation", "export-csv"],
  "iat": 1735200000,
  "exp": 1735200900
}
```

---

## Appendix C: 개인정보 처리 정책

라이선스 시스템에서 수집하는 정보와 처리 방침입니다.

### 수집 항목

| 항목 | 수집 여부 | 목적 | 보관 기간 |
|-----|:--------:|------|---------|
| deviceFingerprint | O | 기기 식별, 활성화 제한 | 라이선스 유효 기간 + 1년 |
| clientVersion | O | 버전 호환성 확인 | 라이선스 유효 기간 |
| clientOs | O | 통계, 호환성 확인 | 라이선스 유효 기간 |
| IP 주소 | △ | Rate limiting, 부정 사용 탐지 | **저장 안 함** 또는 마스킹 |
| Region | △ | 통계 (선택적) | coarse region만 (국가 수준) |

### 수집 최소화 원칙

**IP 주소:**
- 기본: 저장하지 않음 (메모리에서만 처리)
- 저장 시: `/24` 마스킹 (예: `192.168.1.xxx`) 또는 해시+salt
- 보관 기간: 최대 30일

**Region:**
- IP 기반 GeoIP 추정 시 개인정보 성격 가능
- coarse region만 저장 (국가/대륙 수준)
- 또는 opt-in 방식으로 사용자 동의 후 수집

**deviceFingerprint:**
- 원본 하드웨어 ID는 클라이언트에서 해시 처리
- 서버에는 해시값만 전송/저장
- 역추적 불가능한 one-way hash 사용

### 약관 동의

라이선스 활성화 시 아래 내용에 대한 동의가 필요합니다:
- 서비스 제공을 위한 기기 식별 정보 수집
- 부정 사용 방지를 위한 접속 정보 처리

> **Note:** 약관 동의만으로 끝나지 않습니다.
> 수집 최소화 + 마스킹 + 보관기간 제한을 기술적으로 구현해야 합니다.

---

## Appendix D: 구현 현황

### M1 - 도메인 레이어 (완료)
- [x] License Aggregate (Entity, Value Objects)
- [x] License Repository
- [x] License Service (Command 로직)
- [x] Exception Handling

### M1.5 - 보안 개선 (완료)
- [x] `/api/v1/me/licenses` 엔드포인트 추가
- [x] validate/heartbeat Bearer token 인증 적용
- [x] `/api/v1/licenses/key/*` 공개 접근 제거
- [x] Security 설정 변경

### M1.5.1 - 동시 세션 관리 UX (v0.2.1 완료)
- [x] `/api/v1/licenses/validate/force` 엔드포인트 추가
- [x] 409 Conflict 응답 (candidates, activeSessions) 구현
- [x] ForceValidateRequest DTO 구현
- [x] LicenseCandidate, ActiveSessionInfo DTO 구현
- [x] deviceDisplayName 필드 지원

### M1.6 - sessionToken 추가 (v0.2.2 완료)
- [x] SessionTokenService 구현 (RS256 JWS)
- [x] ValidationResponse에 sessionToken 필드 추가
- [x] validate/heartbeat/force-validate에서 sessionToken 발급
- [x] CLI 위/변조 방어 설계 문서화

### M1.7 - offlineToken 정비 (v0.2.3 완료)
- [x] **offlineToken RS256 전환** (HS256 → RS256 완료)
- [x] offlineToken claims 통일 (iss, aud, typ, dfp, ent 추가)
- [x] absolute cap 적용 (`exp ≤ license.validUntil`)
- [x] 갱신 임계값 적용 (ratio 50% 또는 3일 미만)
- [x] Heartbeat에서 offlineToken 갱신 (sliding window)
- [x] 토큰 구조 문서화 (sessionToken vs offlineToken)

### M1.8 - v0.2.3 추가 기능 (완료, v0.3.0에서 일부 변경)
- [x] ~~strategy 파라미터~~ → v0.3.0에서 제거 (서버 자동 선택으로 통합)
- [x] ACTIVATION_DEACTIVATED ErrorCode 추가
- [x] Force Validate 경쟁 조건 방어 (pessimistic lock)

### M1.9 - v0.3.0 Auto-Resolve + Global Session Kick (예정)
- [ ] `strategy` 필드 제거 (서버 항상 AUTO_SELECT)
- [ ] 용량 기반 라이선스 선택 로직 구현
- [ ] Stale 세션 자동 종료 로직 구현 (30분 임계값)
- [ ] `resolution` 응답 필드 추가 (OK, AUTO_RECOVERED, USER_ACTION_REQUIRED)
- [ ] Global Session Kick 응답 구조 (`activeSessions`에 `licenseId`, `productName` 포함)
- [ ] 에러 코드 통합 (`ALL_LICENSES_FULL`)
- [ ] AUTO_RECOVERED 응답에 `recoveryAction`, `terminatedSession` 포함

> **Note:** Claim 기능은 추후 Redeem 기능으로 별도 구현 예정

### M2 - Read 레이어 (완료)
- [x] Query Service (CQRS 패턴)
- [x] View DTOs (LicenseDetailView, LicenseSummaryView)
- [x] QueryDSL 기반 동적 검색
- [x] Admin Controller

### M3 - License Plan Admin API (완료)
- [x] LicensePlanAdminController 구현
- [x] LicensePlanAdminService 구현
- [x] Plan CRUD + activate/deactivate 엔드포인트
- [x] Soft delete 지원

### 향후 계획
- M4: Billing 연동
- M5: Redeem (라이선스 키 귀속) 기능
- M5: 오프라인 토큰 고도화

---

## Appendix E: 구현 파일 구조

```
backend/src/main/java/com/bulc/homepage/licensing/
├── controller/
│   ├── LicenseController.java           # 사용자 API (v0.2.0 변경)
│   ├── MyLicenseController.java         # /api/me/licenses 엔드포인트
│   ├── LicenseAdminController.java      # 관리자 API
│   └── LicensePlanAdminController.java  # 플랜 관리 API
├── domain/
│   ├── License.java                     # Aggregate Root
│   ├── LicenseActivation.java           # 기기 활성화 Entity
│   ├── LicensePlan.java                 # 라이선스 플랜 Entity
│   ├── LicenseStatus.java               # 상태 Enum
│   ├── LicenseType.java                 # 타입 Enum
│   ├── UsageCategory.java               # 용도 Enum
│   ├── OwnerType.java                   # 소유자 유형 Enum
│   └── ActivationStatus.java            # 활성화 상태 Enum
├── dto/
│   ├── ActivationRequest.java           # 검증 요청 DTO (v0.1.0 레거시)
│   ├── ValidateRequest.java             # v0.2.0 검증 요청 DTO
│   ├── ForceValidateRequest.java        # v0.2.1 강제 검증 요청 DTO
│   ├── ValidationResponse.java          # 검증 응답 DTO (sessionToken, candidates, activeSessions)
│   ├── MyLicensesResponse.java          # 내 라이선스 목록 응답
│   ├── LicenseCandidate.java            # 다중 라이선스 후보
│   ├── ActiveSessionInfo.java           # 활성 세션 정보
│   └── ApiResponse.java                 # 공통 응답 DTO
├── exception/
│   ├── LicenseException.java            # 커스텀 예외
│   ├── ErrorCode.java                   # 에러 코드 Enum
│   └── LicenseExceptionHandler.java     # 예외 핸들러
├── query/
│   ├── LicenseQueryService.java         # Query 인터페이스
│   ├── LicenseQueryServiceImpl.java     # Query 구현
│   ├── LicenseQueryRepository.java      # Query Repository
│   ├── LicenseQueryRepositoryImpl.java
│   ├── LicenseSearchCond.java           # 검색 조건 DTO
│   └── view/
│       ├── LicenseDetailView.java       # 상세 조회 View
│       ├── LicenseSummaryView.java      # 목록 조회 View
│       ├── MyLicenseView.java           # 내 라이선스 View
│       ├── PolicySnapshotView.java      # 정책 스냅샷 View
│       └── ActivationView.java          # 활성화 정보 View
├── repository/
│   ├── LicenseRepository.java           # JPA Repository
│   ├── ActivationRepository.java        # 활성화 Repository
│   └── LicensePlanRepository.java       # 플랜 Repository
└── service/
    ├── LicenseService.java              # Command Service
    ├── LicensePlanAdminService.java     # 플랜 관리 Service
    └── SessionTokenService.java         # v0.2.2: sessionToken (JWS) 발급 서비스
```

---

*Last Updated: 2026-01-08 (v0.3.0 Auto-Resolve + Global Session Kick UX)*
