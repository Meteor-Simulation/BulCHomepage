# BulC Homepage API 정의서

| 항목 | 내용 |
|------|------|
| 문서 버전 | v1.1 |
| 작성일 | 2026-02-06 |
| 프로젝트명 | BulC Homepage |
| Base URL | `http://localhost:8080/api` |

### 주요 변경사항 (v1.1)
- **Users PK 마이그레이션**: `user.id`가 email에서 UUID로 변경
- JWT 토큰의 subject가 email에서 userId(UUID)로 변경
- 인증 응답의 `user.id` 필드가 UUID 문자열로 변경

---

## 1. 개요

### 1.1 API 공통 사항

#### 인증 방식
```
Authorization: Bearer <access_token>
```

#### 공통 응답 형식
```json
{
    "success": true,
    "message": "성공 메시지",
    "data": { ... }
}
```

#### 에러 응답 형식
```json
{
    "success": false,
    "message": "에러 메시지",
    "data": null
}
```

#### HTTP 상태 코드

| 코드 | 설명 |
|------|------|
| 200 | 성공 |
| 201 | 생성됨 |
| 400 | 잘못된 요청 |
| 401 | 인증 필요 |
| 403 | 권한 없음 |
| 404 | 찾을 수 없음 |
| 409 | 충돌 (라이선스 세션 충돌 등) |
| 500 | 서버 오류 |

---

## 2. 인증 API (Auth)

### 2.1 회원가입

| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/auth/signup` |
| **인증** | 불필요 |
| **설명** | 새 사용자 계정 생성 |

**Request Body:**
```json
{
    "email": "user@example.com",
    "password": "password123!",
    "name": "홍길동",
    "phone": "010-1234-5678"
}
```

**Response (200):**
```json
{
    "success": true,
    "message": "회원가입이 완료되었습니다",
    "data": {
        "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
        "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
        "tokenType": "Bearer",
        "expiresIn": 3600,
        "user": {
            "id": "550e8400-e29b-41d4-a716-446655440000",
            "email": "user@example.com",
            "name": "홍길동",
            "rolesCode": "002"
        }
    }
}
```

> **참고**: `user.id`는 UUID 형식입니다. JWT 토큰의 subject도 이 UUID를 사용합니다.

---

### 2.2 로그인

| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/auth/login` |
| **인증** | 불필요 |
| **설명** | 이메일/비밀번호로 로그인 |

**Request Body:**
```json
{
    "email": "user@example.com",
    "password": "password123!"
}
```

**Response (200):**
```json
{
    "success": true,
    "message": "로그인 성공",
    "data": {
        "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
        "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
        "tokenType": "Bearer",
        "expiresIn": 3600,
        "user": {
            "id": "550e8400-e29b-41d4-a716-446655440000",
            "email": "user@example.com",
            "name": "홍길동",
            "rolesCode": "002"
        }
    }
}
```

---

### 2.3 로그아웃

| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/auth/logout` |
| **인증** | 필요 |
| **설명** | 현재 토큰 무효화 |

**Response (200):**
```json
{
    "success": true,
    "message": "로그아웃 성공",
    "data": null
}
```

---

### 2.4 토큰 갱신

| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/auth/refresh` |
| **인증** | 불필요 |
| **설명** | Refresh Token으로 새 토큰 발급 |

**Request Body:**
```json
{
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**Response (200):**
```json
{
    "success": true,
    "message": "토큰 갱신 성공",
    "data": {
        "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
        "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
        "tokenType": "Bearer",
        "expiresIn": 3600,
        "user": { ... }
    }
}
```

---

### 2.5 현재 사용자 조회

| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/auth/me` |
| **인증** | 필요 |
| **설명** | 현재 로그인한 사용자 정보 조회 |

**Response (200):**
```json
{
    "success": true,
    "message": "사용자 정보 조회 성공",
    "data": {
        "email": "user@example.com",
        "name": "홍길동",
        "phone": "010-1234-5678",
        "rolesCode": "002",
        "countryCode": "KR",
        "isActive": true,
        "createdAt": "2026-01-01T00:00:00"
    }
}
```

---

### 2.6 이메일 인증 코드 발송

| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/auth/send-verification` |
| **인증** | 불필요 |
| **설명** | 회원가입용 이메일 인증 코드 발송 |

**Request Body:**
```json
{
    "email": "user@example.com"
}
```

**Response (200):**
```json
{
    "success": true,
    "message": "인증 코드가 발송되었습니다",
    "data": null
}
```

---

### 2.7 이메일 인증 코드 검증

| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/auth/verify-code` |
| **인증** | 불필요 |
| **설명** | 이메일 인증 코드 검증 |

**Request Body:**
```json
{
    "email": "user@example.com",
    "code": "123456"
}
```

**Response (200):**
```json
{
    "success": true,
    "message": "이메일이 인증되었습니다",
    "data": null
}
```

---

### 2.8 이메일 중복/상태 확인

| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/auth/check-email` |
| **인증** | 불필요 |
| **설명** | 이메일 중복 및 계정 상태 확인 |

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| email | string | O | 확인할 이메일 |

**Response (200):**
```json
{
    "success": true,
    "message": "확인 완료",
    "data": {
        "exists": true,
        "isActive": true,
        "hasPassword": true
    }
}
```

---

### 2.9 비밀번호 재설정 요청

| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/auth/password/reset-request` |
| **인증** | 불필요 |
| **설명** | 비밀번호 재설정 인증 코드 발송 |

**Request Body:**
```json
{
    "email": "user@example.com"
}
```

**Response (200):**
```json
{
    "success": true,
    "message": "인증 코드가 발송되었습니다",
    "data": null
}
```

---

### 2.10 비밀번호 재설정

| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/auth/password/reset` |
| **인증** | 불필요 |
| **설명** | 새 비밀번호로 변경 |

**Request Body:**
```json
{
    "email": "user@example.com",
    "code": "123456",
    "newPassword": "newPassword123!"
}
```

**Response (200):**
```json
{
    "success": true,
    "message": "비밀번호가 변경되었습니다",
    "data": null
}
```

---

### 2.11 OAuth 회원가입 완료

| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/auth/oauth/signup` |
| **인증** | 불필요 (임시 토큰 사용) |
| **설명** | OAuth 신규 사용자 회원가입 완료 |

**Request Body:**
```json
{
    "token": "임시토큰...",
    "password": "password123!",
    "name": "홍길동",
    "phone": "010-1234-5678"
}
```

**Response (200):**
```json
{
    "success": true,
    "message": "회원가입이 완료되었습니다",
    "data": {
        "accessToken": "...",
        "refreshToken": "...",
        "user": { ... }
    }
}
```

---

## 3. 사용자 API (User)

### 3.1 내 정보 조회

| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/users/me` |
| **인증** | 필요 |
| **설명** | 현재 사용자 상세 정보 조회 |

**Response (200):**
```json
{
    "success": true,
    "message": "조회 성공",
    "data": {
        "email": "user@example.com",
        "name": "홍길동",
        "phone": "010-1234-5678",
        "rolesCode": "002",
        "countryCode": "KR",
        "isActive": true,
        "createdAt": "2026-01-01T00:00:00",
        "socialAccounts": [
            { "provider": "naver", "connectedAt": "2026-01-01T00:00:00" }
        ]
    }
}
```

---

### 3.2 내 정보 수정

| 항목 | 내용 |
|------|------|
| **Endpoint** | `PUT /api/users/me` |
| **인증** | 필요 |
| **설명** | 사용자 정보 수정 |

**Request Body:**
```json
{
    "name": "홍길동",
    "phone": "010-9876-5432"
}
```

**Response (200):**
```json
{
    "success": true,
    "message": "정보가 수정되었습니다",
    "data": { ... }
}
```

---

### 3.3 비밀번호 변경

| 항목 | 내용 |
|------|------|
| **Endpoint** | `PUT /api/users/me/password` |
| **인증** | 필요 |
| **설명** | 현재 비밀번호 확인 후 새 비밀번호로 변경 |

**Request Body:**
```json
{
    "currentPassword": "oldPassword123!",
    "newPassword": "newPassword123!"
}
```

**Response (200):**
```json
{
    "success": true,
    "message": "비밀번호가 변경되었습니다",
    "data": null
}
```

---

### 3.4 계정 비활성화 (탈퇴)

| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/users/me/deactivate` |
| **인증** | 필요 |
| **설명** | 계정 비활성화 처리 |

**Request Body:**
```json
{
    "password": "password123!",
    "reason": "개인 사유"
}
```

**Response (200):**
```json
{
    "success": true,
    "message": "계정이 비활성화되었습니다",
    "data": null
}
```

---

## 4. 상품 API (Product)

### 4.1 활성 상품 목록

| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/products` |
| **인증** | 불필요 |
| **설명** | 활성화된 상품 목록 조회 |

**Response (200):**
```json
{
    "success": true,
    "message": "조회 성공",
    "data": [
        {
            "code": "METEOR",
            "name": "METEOR 시뮬레이션",
            "description": "화재 시뮬레이션 소프트웨어"
        },
        {
            "code": "BULC",
            "name": "BUL:C",
            "description": "화재 예측 및 제어 소프트웨어"
        }
    ]
}
```

---

### 4.2 상품별 요금제 조회

| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/products/{code}/plans` |
| **인증** | 불필요 |
| **설명** | 특정 상품의 활성 요금제 조회 |

**Path Parameters:**

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| code | string | 상품 코드 (METEOR, BULC 등) |

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| currency | string | X | 통화 (KRW, USD), 기본값 KRW |

**Response (200):**
```json
{
    "success": true,
    "message": "조회 성공",
    "data": [
        {
            "id": 1,
            "name": "Basic",
            "description": "기본 요금제",
            "price": 50000,
            "currency": "KRW"
        },
        {
            "id": 2,
            "name": "Pro",
            "description": "프로 요금제",
            "price": 100000,
            "currency": "KRW"
        }
    ]
}
```

---

## 5. 결제 API (Payment)

### 5.1 결제 승인

| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/payments/confirm` |
| **인증** | 필요 |
| **설명** | 토스페이먼츠 결제 승인 처리 |

**Request Body:**
```json
{
    "paymentKey": "토스페이먼츠_결제키",
    "orderId": "주문번호",
    "amount": 50000
}
```

**Response (200):**
```json
{
    "success": true,
    "message": "결제가 완료되었습니다",
    "data": {
        "orderId": "주문번호",
        "amount": 50000,
        "status": "COMPLETED",
        "paymentMethod": "카드",
        "paidAt": "2026-01-13T12:00:00"
    }
}
```

---

### 5.2 결제 정보 조회

| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/payments/{orderId}` |
| **인증** | 필요 |
| **설명** | 결제 상세 정보 조회 |

**Response (200):**
```json
{
    "success": true,
    "message": "조회 성공",
    "data": {
        "orderId": "주문번호",
        "amount": 50000,
        "status": "COMPLETED",
        "paymentMethod": "카드",
        "productName": "METEOR Basic",
        "paidAt": "2026-01-13T12:00:00"
    }
}
```

---

## 6. 구독 API (Subscription)

### 6.1 내 구독 목록

| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/subscriptions` |
| **인증** | 필요 |
| **설명** | 현재 사용자의 구독 목록 조회 |

**Response (200):**
```json
{
    "success": true,
    "message": "조회 성공",
    "data": [
        {
            "id": 1,
            "productCode": "METEOR",
            "productName": "METEOR 시뮬레이션",
            "pricePlanId": 1,
            "pricePlanName": "Basic",
            "price": 50000,
            "currency": "KRW",
            "status": "A",
            "startDate": "2026-01-01T00:00:00",
            "endDate": "2026-02-01T00:00:00",
            "autoRenew": true,
            "billingCycle": "MONTHLY",
            "nextBillingDate": "2026-02-01T00:00:00"
        }
    ]
}
```

---

### 6.2 구독 상세 조회

| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/subscriptions/{id}` |
| **인증** | 필요 |
| **설명** | 구독 상세 정보 조회 |

**Response (200):**
```json
{
    "success": true,
    "message": "조회 성공",
    "data": {
        "id": 1,
        "productCode": "METEOR",
        "productName": "METEOR 시뮬레이션",
        "status": "A",
        "statusText": "활성",
        "autoRenew": true,
        "billingCycle": "MONTHLY",
        "billingCycleText": "월간",
        "nextBillingDate": "2026-02-01T00:00:00"
    }
}
```

---

### 6.3 자동 갱신 활성화

| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/subscriptions/{id}/auto-renew` |
| **인증** | 필요 |
| **설명** | 구독 자동 갱신 활성화 |

**Request Body:**
```json
{
    "billingKeyId": 1
}
```

**Response (200):**
```json
{
    "success": true,
    "message": "자동 갱신이 활성화되었습니다",
    "data": null
}
```

---

### 6.4 자동 갱신 비활성화

| 항목 | 내용 |
|------|------|
| **Endpoint** | `DELETE /api/subscriptions/{id}/auto-renew` |
| **인증** | 필요 |
| **설명** | 구독 자동 갱신 비활성화 |

**Response (200):**
```json
{
    "success": true,
    "message": "자동 갱신이 비활성화되었습니다",
    "data": null
}
```

---

### 6.5 구독 결제 이력

| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/subscriptions/{id}/payments` |
| **인증** | 필요 |
| **설명** | 해당 구독의 결제 이력 조회 |

**Response (200):**
```json
{
    "success": true,
    "message": "조회 성공",
    "data": [
        {
            "id": 1,
            "amount": 50000,
            "status": "COMPLETED",
            "billingDate": "2026-01-01",
            "paidAt": "2026-01-01T09:00:00"
        }
    ]
}
```

---

### 6.6 빌링키 발급

| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/subscriptions/billing-keys` |
| **인증** | 필요 |
| **설명** | 자동 결제용 빌링키(카드) 등록 |

**Request Body:**
```json
{
    "authKey": "토스페이먼츠_인증키",
    "customerKey": "고객키"
}
```

**Response (200):**
```json
{
    "success": true,
    "message": "카드가 등록되었습니다",
    "data": {
        "id": 1,
        "cardCompany": "삼성카드",
        "cardNumber": "****-****-****-1234",
        "cardType": "신용",
        "isDefault": true
    }
}
```

---

### 6.7 빌링키 목록 조회

| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/subscriptions/billing-keys` |
| **인증** | 필요 |
| **설명** | 등록된 빌링키(카드) 목록 조회 |

**Response (200):**
```json
{
    "success": true,
    "message": "조회 성공",
    "data": [
        {
            "id": 1,
            "cardCompany": "삼성카드",
            "cardNumber": "****-****-****-1234",
            "cardType": "신용",
            "ownerType": "개인",
            "isDefault": true,
            "createdAt": "2026-01-01T00:00:00"
        }
    ]
}
```

---

### 6.8 기본 결제 수단 변경

| 항목 | 내용 |
|------|------|
| **Endpoint** | `PATCH /api/subscriptions/billing-keys/{id}/default` |
| **인증** | 필요 |
| **설명** | 해당 빌링키를 기본 결제 수단으로 설정 |

**Response (200):**
```json
{
    "success": true,
    "message": "기본 결제 수단이 변경되었습니다",
    "data": null
}
```

---

### 6.9 빌링키 삭제

| 항목 | 내용 |
|------|------|
| **Endpoint** | `DELETE /api/subscriptions/billing-keys/{id}` |
| **인증** | 필요 |
| **설명** | 빌링키 삭제 (자동 갱신 해제 필요) |

**Response (200):**
```json
{
    "success": true,
    "message": "카드가 삭제되었습니다",
    "data": null
}
```

---

## 7. 라이선스 API (License)

### 7.1 라이선스 검증 및 활성화

| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/v1/licenses/validate` |
| **인증** | 필요 |
| **설명** | 라이선스 검증 및 기기 활성화 (Auto-Resolve) |

**Request Body:**
```json
{
    "licenseKey": "LICENSE-KEY-HERE",
    "deviceFingerprint": "device-unique-id",
    "deviceDisplayName": "사용자PC"
}
```

**Response (200 - 성공):**
```json
{
    "success": true,
    "message": "라이선스 검증 성공",
    "data": {
        "resolution": "OK",
        "sessionToken": "eyJ...",
        "offlineToken": "eyJ...",
        "expiresAt": "2026-01-13T13:00:00",
        "licenseInfo": {
            "licenseKey": "LICENSE-KEY-HERE",
            "productId": "METEOR",
            "validUntil": "2026-12-31T23:59:59"
        }
    }
}
```

**Response (409 - 세션 충돌):**
```json
{
    "success": false,
    "message": "활성 세션 수 초과",
    "data": {
        "resolution": "USER_ACTION_REQUIRED",
        "activeSessions": [
            {
                "deviceFingerprint": "device-1",
                "deviceDisplayName": "사무실PC",
                "lastHeartbeat": "2026-01-13T11:00:00"
            },
            {
                "deviceFingerprint": "device-2",
                "deviceDisplayName": "집PC",
                "lastHeartbeat": "2026-01-13T10:30:00"
            }
        ],
        "maxActivations": 2
    }
}
```

---

### 7.2 Heartbeat (세션 갱신)

| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/v1/licenses/heartbeat` |
| **인증** | 필요 |
| **설명** | 세션 유지를 위한 Heartbeat (15분마다) |

**Request Body:**
```json
{
    "sessionToken": "eyJ..."
}
```

**Response (200):**
```json
{
    "success": true,
    "message": "세션 갱신 성공",
    "data": {
        "sessionToken": "eyJ... (갱신된 토큰)",
        "expiresAt": "2026-01-13T13:15:00"
    }
}
```

---

### 7.3 강제 활성화

| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/v1/licenses/validate/force` |
| **인증** | 필요 |
| **설명** | 기존 세션을 종료하고 강제 활성화 |

**Request Body:**
```json
{
    "licenseKey": "LICENSE-KEY-HERE",
    "deviceFingerprint": "new-device-id",
    "deviceDisplayName": "새PC",
    "terminateDeviceFingerprints": ["device-1"]
}
```

**Response (200):**
```json
{
    "success": true,
    "message": "강제 활성화 성공",
    "data": {
        "sessionToken": "eyJ...",
        "offlineToken": "eyJ...",
        "expiresAt": "2026-01-13T13:00:00"
    }
}
```

---

### 7.4 기기 비활성화

| 항목 | 내용 |
|------|------|
| **Endpoint** | `DELETE /api/v1/licenses/{licenseId}/activations/{deviceFingerprint}` |
| **인증** | 필요 |
| **설명** | 특정 기기의 활성화 해제 |

**Response (200):**
```json
{
    "success": true,
    "message": "기기가 비활성화되었습니다",
    "data": null
}
```

---

### 7.5 라이선스 상세 조회

| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/v1/licenses/{licenseId}` |
| **인증** | 필요 |
| **설명** | 라이선스 상세 정보 조회 |

**Response (200):**
```json
{
    "success": true,
    "message": "조회 성공",
    "data": {
        "id": "uuid",
        "licenseKey": "LICENSE-KEY-HERE",
        "productId": "METEOR",
        "status": "ACTIVE",
        "validFrom": "2026-01-01T00:00:00",
        "validUntil": "2026-12-31T23:59:59",
        "maxActivations": 3,
        "currentActivations": 2,
        "activations": [
            {
                "deviceFingerprint": "device-1",
                "deviceDisplayName": "사무실PC",
                "status": "ACTIVE",
                "lastHeartbeat": "2026-01-13T12:00:00"
            }
        ]
    }
}
```

---

### 7.6 내 라이선스 목록

| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/v1/me/licenses` |
| **인증** | 필요 |
| **설명** | 현재 사용자의 라이선스 목록 조회 |

**Response (200):**
```json
{
    "success": true,
    "message": "조회 성공",
    "data": [
        {
            "id": "uuid-1",
            "licenseKey": "LICENSE-KEY-1",
            "productId": "METEOR",
            "productName": "METEOR 시뮬레이션",
            "status": "ACTIVE",
            "validUntil": "2026-12-31T23:59:59",
            "currentActivations": 2,
            "maxActivations": 3
        }
    ]
}
```

---

## 8. 관리자 API (Admin)

### 8.1 사용자 목록 조회

| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/admin/users` |
| **인증** | 필요 (Admin/Manager) |
| **설명** | 활성 사용자 목록 조회 |

**Response (200):**
```json
{
    "success": true,
    "message": "조회 성공",
    "data": [
        {
            "email": "user@example.com",
            "name": "홍길동",
            "rolesCode": "002",
            "createdAt": "2026-01-01T00:00:00"
        }
    ]
}
```

---

### 8.2 사용자 권한 변경

| 항목 | 내용 |
|------|------|
| **Endpoint** | `PUT /api/admin/users/{email}/role` |
| **인증** | 필요 (Admin/Manager) |
| **설명** | 사용자 권한 코드 변경 |

**Request Body:**
```json
{
    "rolesCode": "001"
}
```

**Response (200):**
```json
{
    "success": true,
    "message": "권한이 변경되었습니다",
    "data": null
}
```

---

### 8.3 상품 목록 조회

| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/admin/products` |
| **인증** | 필요 (Admin/Manager) |
| **설명** | 전체 상품 목록 조회 (비활성 포함) |

**Response (200):**
```json
{
    "success": true,
    "message": "조회 성공",
    "data": [
        {
            "code": "METEOR",
            "name": "METEOR 시뮬레이션",
            "description": "...",
            "isActive": true
        }
    ]
}
```

---

### 8.4 상품 생성

| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/admin/products` |
| **인증** | 필요 (Admin/Manager) |
| **설명** | 새 상품 생성 |

**Request Body:**
```json
{
    "code": "NEWPRODUCT",
    "name": "새 상품",
    "description": "상품 설명"
}
```

**Response (201):**
```json
{
    "success": true,
    "message": "상품이 생성되었습니다",
    "data": { ... }
}
```

---

### 8.5 상품 수정

| 항목 | 내용 |
|------|------|
| **Endpoint** | `PUT /api/admin/products/{code}` |
| **인증** | 필요 (Admin/Manager) |
| **설명** | 상품 정보 수정 |

**Request Body:**
```json
{
    "name": "수정된 이름",
    "description": "수정된 설명"
}
```

**Response (200):**
```json
{
    "success": true,
    "message": "상품이 수정되었습니다",
    "data": { ... }
}
```

---

### 8.6 상품 활성화/비활성화

| 항목 | 내용 |
|------|------|
| **Endpoint** | `PATCH /api/admin/products/{code}/toggle` |
| **인증** | 필요 (Admin/Manager) |
| **설명** | 상품 활성화 상태 토글 |

**Response (200):**
```json
{
    "success": true,
    "message": "상품 상태가 변경되었습니다",
    "data": {
        "isActive": false
    }
}
```

---

### 8.7 요금제 목록 조회

| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/admin/price-plans` |
| **인증** | 필요 (Admin/Manager) |
| **설명** | 전체 요금제 목록 조회 |

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| productCode | string | X | 상품 코드로 필터링 |

**Response (200):**
```json
{
    "success": true,
    "message": "조회 성공",
    "data": [
        {
            "id": 1,
            "productCode": "METEOR",
            "name": "Basic",
            "price": 50000,
            "currency": "KRW",
            "isActive": true,
            "licensePlanId": "uuid"
        }
    ]
}
```

---

### 8.8 요금제 생성

| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/admin/price-plans` |
| **인증** | 필요 (Admin/Manager) |
| **설명** | 새 요금제 생성 |

**Request Body:**
```json
{
    "productCode": "METEOR",
    "name": "Enterprise",
    "description": "엔터프라이즈 요금제",
    "price": 500000,
    "currency": "KRW",
    "licensePlanId": "uuid"
}
```

**Response (201):**
```json
{
    "success": true,
    "message": "요금제가 생성되었습니다",
    "data": { ... }
}
```

---

### 8.9 라이선스 목록 조회

| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/admin/license-list` |
| **인증** | 필요 (Admin/Manager) |
| **설명** | 전체 라이선스 목록 조회 |

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| status | string | X | 상태 필터 (ACTIVE, EXPIRED 등) |
| productId | string | X | 상품 ID 필터 |

**Response (200):**
```json
{
    "success": true,
    "message": "조회 성공",
    "data": [
        {
            "id": "uuid",
            "licenseKey": "LICENSE-KEY",
            "ownerEmail": "user@example.com",
            "productId": "METEOR",
            "status": "ACTIVE",
            "validUntil": "2026-12-31T23:59:59",
            "currentActivations": 2,
            "maxActivations": 3
        }
    ]
}
```

---

### 8.10 라이선스 상태 변경

| 항목 | 내용 |
|------|------|
| **Endpoint** | `PATCH /api/admin/licenses/{id}/activate` |
| **인증** | 필요 (Admin) |
| **설명** | 라이선스 활성화 |

**Response (200):**
```json
{
    "success": true,
    "message": "라이선스가 활성화되었습니다",
    "data": null
}
```

---

### 8.11 라이선스 정지

| 항목 | 내용 |
|------|------|
| **Endpoint** | `PATCH /api/admin/licenses/{id}/suspend` |
| **인증** | 필요 (Admin) |
| **설명** | 라이선스 정지 |

**Request Body:**
```json
{
    "reason": "정지 사유"
}
```

**Response (200):**
```json
{
    "success": true,
    "message": "라이선스가 정지되었습니다",
    "data": null
}
```

---

### 8.12 결제 내역 조회

| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/admin/payments` |
| **인증** | 필요 (Admin/Manager) |
| **설명** | 전체 결제 내역 조회 |

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| from | date | X | 시작일 (YYYY-MM-DD) |
| to | date | X | 종료일 (YYYY-MM-DD) |
| status | string | X | 상태 (COMPLETED, FAILED 등) |

**Response (200):**
```json
{
    "success": true,
    "message": "조회 성공",
    "data": [
        {
            "id": 1,
            "orderId": "ORDER-001",
            "userEmail": "user@example.com",
            "amount": 50000,
            "status": "COMPLETED",
            "paymentMethod": "카드",
            "paidAt": "2026-01-13T12:00:00"
        }
    ]
}
```

---

## 9. 라이선스 플랜 관리 API (Admin)

### 9.1 라이선스 플랜 목록

| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/v1/admin/license-plans` |
| **인증** | 필요 (Admin) |
| **설명** | 라이선스 플랜 목록 조회 |

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| includeDeleted | boolean | X | 삭제된 플랜 포함 여부 |

**Response (200):**
```json
{
    "success": true,
    "message": "조회 성공",
    "data": [
        {
            "id": "uuid",
            "name": "Basic Plan",
            "description": "기본 플랜",
            "maxActivations": 3,
            "maxDevices": 5,
            "entitlements": ["feature1", "feature2"],
            "isActive": true,
            "createdAt": "2026-01-01T00:00:00"
        }
    ]
}
```

---

### 9.2 라이선스 플랜 생성

| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/v1/admin/license-plans` |
| **인증** | 필요 (Admin) |
| **설명** | 새 라이선스 플랜 생성 |

**Request Body:**
```json
{
    "name": "Enterprise Plan",
    "description": "엔터프라이즈 플랜",
    "maxActivations": 10,
    "maxDevices": 20,
    "entitlements": ["feature1", "feature2", "feature3"]
}
```

**Response (201):**
```json
{
    "success": true,
    "message": "플랜이 생성되었습니다",
    "data": { ... }
}
```

---

### 9.3 라이선스 플랜 수정

| 항목 | 내용 |
|------|------|
| **Endpoint** | `PUT /api/v1/admin/license-plans/{id}` |
| **인증** | 필요 (Admin) |
| **설명** | 라이선스 플랜 수정 |

**Request Body:**
```json
{
    "name": "수정된 플랜명",
    "description": "수정된 설명",
    "maxActivations": 5
}
```

**Response (200):**
```json
{
    "success": true,
    "message": "플랜이 수정되었습니다",
    "data": { ... }
}
```

---

### 9.4 라이선스 플랜 삭제

| 항목 | 내용 |
|------|------|
| **Endpoint** | `DELETE /api/v1/admin/license-plans/{id}` |
| **인증** | 필요 (Admin) |
| **설명** | 라이선스 플랜 삭제 (Soft Delete) |

**Response (200):**
```json
{
    "success": true,
    "message": "플랜이 삭제되었습니다",
    "data": null
}
```

---

## 리딤 코드 API

### 사용자 API

#### POST /api/v1/redeem - 리딤 코드 등록

리딤 코드를 입력하여 라이선스를 발급받습니다.

| 항목 | 내용 |
|------|------|
| URL | POST /api/v1/redeem |
| 인증 | Bearer Token 필수 |

**Request Body**
| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| code | String | O | 리딤 코드 (8~64자, 영숫자) |

**Response (200 OK)**
| 필드 | 타입 | 설명 |
|------|------|------|
| licenseId | UUID | 발급된 라이선스 ID |
| licenseKey | String | 라이선스 키 |
| productName | String | 상품명 |
| planName | String | 플랜명 |
| validUntil | Instant | 라이선스 유효 기한 |

**에러 응답**
| HTTP | 코드 | 설명 |
|------|------|------|
| 400 | REDEEM_CODE_INVALID | 코드 형식 오류 |
| 404 | REDEEM_CODE_NOT_FOUND | 코드 미존재 |
| 410 | REDEEM_CODE_EXPIRED | 코드 만료 |
| 410 | REDEEM_CODE_DISABLED | 코드 비활성화 |
| 409 | REDEEM_CODE_DEPLETED | 코드 사용횟수 소진 |
| 409 | REDEEM_CAMPAIGN_FULL | 캠페인 한도 초과 |
| 403 | REDEEM_CAMPAIGN_NOT_ACTIVE | 캠페인 비활성 |
| 409 | REDEEM_USER_LIMIT_EXCEEDED | 사용자 한도 초과 |
| 429 | REDEEM_RATE_LIMITED | 요청 빈도 초과 |

---

### 관리자 API (Admin)

모든 엔드포인트: `@PreAuthorize("hasRole('ADMIN')")`

#### GET /api/v1/admin/redeem-campaigns - 캠페인 목록

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| page | int | N | 페이지 번호 |
| size | int | N | 페이지 크기 |
| status | String | N | ACTIVE/PAUSED/ENDED |

**Response:** `Page<RedeemCampaignResponse>`

#### GET /api/v1/admin/redeem-campaigns/{id} - 캠페인 상세

**Response:** `RedeemCampaignResponse`

#### POST /api/v1/admin/redeem-campaigns - 캠페인 생성

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| name | String | O | 캠페인명 |
| description | String | N | 설명 |
| productId | UUID | O | 상품 ID |
| licensePlanId | UUID | O | 라이선스 플랜 ID |
| usageCategory | String | N | 사용 용도 (기본 COMMERCIAL) |
| seatLimit | Integer | N | 발급 한도 (NULL=무제한) |
| perUserLimit | int | N | 사용자당 한도 (기본 1) |
| validFrom | Instant | N | 유효 시작일 |
| validUntil | Instant | N | 유효 종료일 |

**Response (201 Created):** `RedeemCampaignResponse`

#### PUT /api/v1/admin/redeem-campaigns/{id} - 캠페인 수정

요청/응답: 생성과 동일

#### PATCH /api/v1/admin/redeem-campaigns/{id}/pause - 일시정지

**Response:** 204 No Content

#### PATCH /api/v1/admin/redeem-campaigns/{id}/end - 종료

**Response:** 204 No Content

#### PATCH /api/v1/admin/redeem-campaigns/{id}/resume - 재개

**Response:** 204 No Content

#### POST /api/v1/admin/redeem-campaigns/codes - 코드 생성

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| campaignId | UUID | O | 캠페인 ID |
| codeType | String | O | RANDOM / CUSTOM |
| customCode | String | N | CUSTOM 시 코드 |
| count | int | O | 생성 수량 (1~1000) |
| maxRedemptions | int | O | 코드당 최대 사용횟수 |
| expiresAt | Instant | N | 코드 만료일 |

**Response (201 Created):**
| 필드 | 타입 | 설명 |
|------|------|------|
| generatedCount | int | 생성된 코드 수 |
| codes | List&lt;String&gt; | 코드 원문 (1회만 노출) |

#### GET /api/v1/admin/redeem-campaigns/{campaignId}/codes - 코드 목록

**Response:** `Page<RedeemCodeResponse>`

#### DELETE /api/v1/admin/redeem-campaigns/codes/{codeId} - 코드 비활성화

**Response:** 204 No Content

---

## 10. 프로모션 API (Promotion)

### 10.1 쿠폰 검증

| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/promotions/validate` |
| **인증** | 불필요 |
| **설명** | 쿠폰 코드 유효성 검증 |

**Request Body:**
```json
{
    "code": "SUMMER2026",
    "productCode": "METEOR"
}
```

**Response (200):**
```json
{
    "success": true,
    "message": "유효한 쿠폰입니다",
    "data": {
        "code": "SUMMER2026",
        "name": "여름 할인",
        "discountType": 1,
        "discountValue": 20,
        "discountText": "20% 할인"
    }
}
```

---

## 11. 기타 API

### 11.1 헬스 체크

| 항목 | 내용 |
|------|------|
| **Endpoint** | `GET /api/health` |
| **인증** | 불필요 |
| **설명** | 서버 상태 확인 |

**Response (200):**
```json
{
    "status": "UP",
    "timestamp": "2026-01-13T12:00:00"
}
```

---

### 11.2 문의하기

| 항목 | 내용 |
|------|------|
| **Endpoint** | `POST /api/contact` |
| **인증** | 불필요 |
| **설명** | 문의 이메일 발송 |

**Request Body:**
```json
{
    "name": "홍길동",
    "email": "user@example.com",
    "subject": "문의 제목",
    "message": "문의 내용"
}
```

**Response (200):**
```json
{
    "success": true,
    "message": "문의가 접수되었습니다",
    "data": null
}
```

---

## 12. 에러 코드 정리

| 코드 | 메시지 | 설명 |
|------|--------|------|
| `AUTH_001` | 이메일 또는 비밀번호가 올바르지 않습니다 | 로그인 실패 |
| `AUTH_002` | 비활성화된 계정입니다 | 탈퇴한 계정 |
| `AUTH_003` | 유효하지 않은 토큰입니다 | 토큰 검증 실패 |
| `AUTH_004` | 토큰이 만료되었습니다 | 토큰 만료 |
| `AUTH_005` | 이미 가입된 이메일입니다 | 이메일 중복 |
| `USER_001` | 사용자를 찾을 수 없습니다 | 사용자 없음 |
| `PAYMENT_001` | 결제 금액이 일치하지 않습니다 | 금액 검증 실패 |
| `PAYMENT_002` | 결제 승인에 실패했습니다 | 결제 실패 |
| `LICENSE_001` | 라이선스를 찾을 수 없습니다 | 라이선스 없음 |
| `LICENSE_002` | 라이선스가 만료되었습니다 | 만료된 라이선스 |
| `LICENSE_003` | 활성 세션 수를 초과했습니다 | 세션 충돌 |
| `LICENSE_004` | 유효하지 않은 세션입니다 | 세션 검증 실패 |
| REDEEM_CODE_INVALID | 400 | 유효하지 않은 리딤 코드 형식 |
| REDEEM_CODE_NOT_FOUND | 404 | 리딤 코드 미존재 |
| REDEEM_CODE_EXPIRED | 410 | 만료된 리딤 코드 |
| REDEEM_CODE_DISABLED | 410 | 비활성화된 리딤 코드 |
| REDEEM_CODE_DEPLETED | 409 | 사용 횟수 소진 |
| REDEEM_CAMPAIGN_FULL | 409 | 캠페인 한도 초과 |
| REDEEM_CAMPAIGN_NOT_ACTIVE | 403 | 캠페인 비활성 |
| REDEEM_USER_LIMIT_EXCEEDED | 409 | 사용자 한도 초과 |
| REDEEM_CAMPAIGN_NOT_FOUND | 404 | 캠페인 미존재 |
| REDEEM_CODE_HASH_DUPLICATE | 409 | 중복 코드 |
| REDEEM_RATE_LIMITED | 429 | 요청 빈도 초과 |

---

## 13. 문서 이력

| 버전 | 작성일 | 작성자 | 변경 내용 |
|------|--------|--------|----------|
| v1.0 | 2026-01-13 | - | 초안 작성 |
