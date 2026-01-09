# BulC Licensing Auth Module Spec v0.3 (In-process Module, Embedded Public Key)

## 0. 핵심 원칙: 토큰이 SOT (Source of Truth)

> **⚠️ 필수 준수사항** - 클라이언트 구현 시 반드시 따라야 하는 핵심 규칙입니다.

### 응답 신뢰 모델

| 데이터 | 용도 | SOT 여부 |
|--------|------|---------|
| **`sessionToken`** | 온라인 기능 unlock | ✅ **SOT** |
| **`offlineToken`** | 오프라인 기능 unlock | ✅ **SOT** |
| `valid`, `status`, `entitlements` | UI 표시/디버깅/참고용 | ❌ **참고용 (권한 판단 금지)** |

**클라이언트 동작 규칙:**
- 기능 unlock은 **토큰 서명/클레임 검증이 성공한 경우에만** 허용
- 응답 본체의 `valid: true`를 기준으로 unlock하면 **보안 위반**
- 토큰과 응답 본체 값이 불일치하면 **토큰 기준으로 동작**

### v0.2 → v0.3 핵심 변경점

1. **CLI → In-process 모듈**: CLI 호출 대신 앱 내장 라이브러리로 전환
2. **SOT 명확화**: 응답 본체는 참고용, 토큰만 권한 판단 기준
3. **offlineToken RS256 통일**: sessionToken과 동일한 RS256 서명

---

## 1. 목표 / 위협 모델 (요약)

### 방어 목표

- **단순 CLI 바꿔치기** / **session.json 조작** / **stdout 조작**으로 기능이 열리는 것을 방지한다.

### 비목표 (한계 인정)

- 고급 공격(앱 패치/후킹)은 완전 방지 목표가 아니며, **비용을 올리고 서버 측 운영/탐지로 대응**한다.

---

## 2. 구성 요소 (In-process 모듈)

### 2.1 라이브러리 모듈

| 모듈 | 역할 | 플랫폼 |
|------|------|--------|
| `BulcAuth` | 브라우저 로그인 + 토큰 저장/갱신 | .NET / Java |
| `BulcLicense` | 라이선스 validate/heartbeat + 토큰 검증 | .NET / Java |

### 2.2 통합 방식

```
앱 (Unreal/Unity/Java)
  └── BulcLicense 모듈 (In-process)
        ├── HTTP API 호출 (validate/heartbeat)
        ├── 토큰 저장/로드 (DPAPI/Keychain)
        └── RS256 검증 (내장 공개키)
```

> **v0.2 CLI 방식 대비 장점:**
> - 프로세스 간 통신 오버헤드 제거
> - stdout/stderr 파싱 불필요
> - 토큰 검증 로직이 앱과 동일 프로세스에서 실행

---

## 3. Token / Session 저장소

### 3.1 Token Store (필수)

- **DPAPI (CurrentUser)** 암호화 파일
- 경로: `%AppData%\Bulc\Auth\tokens.dat`

### 3.2 Session Output

- 기본: `%ProgramData%\Bulc\Session\{productCode}\session.json`
- 또는 `--out <path>` 지정 가능

---

## 4. 표준 출력 및 Exit Code

### 출력 규칙

- **stdout**: JSON only
- **stderr**: 진단 (민감정보 금지)

### Exit Code (고정)

| Exit Code | 의미 | 앱 권장 처리 |
|-----------|------|-------------|
| **0** | 성공 | stdout JSON 파싱 후 진행 |
| **10** | 인증 필요/토큰 문제 | 로그인 유도 (`bulc-auth login`) |
| **20** | 라이선스 불가 | 라이선스 선택/구매/데모 모드 |
| **30** | 네트워크 문제 | 오프라인 모드 판단 |
| **40** | 서버 오류 | 재시도/안내 |
| **50** | 클라이언트 오류 | 개발자 로그/에러 안내 |

---

## 5. sessionToken (JWS) 스펙 (고정)

### 5.1 형식

- **Compact JWS** (JWT 형태)
- **알고리즘**: RS256 (권장)
  - 서버 개인키로 서명
  - 앱/CLI는 공개키로 검증

### 5.2 필수 클레임 (Claims)

| Claim | 타입 | 필수 | 의미 |
|-------|------|------|------|
| `iss` | string | O | 토큰 발급자 (서버) |
| `aud` | string | O | 제품 코드 (예: `BULC_EVAC`) |
| `sub` | string | O | licenseId |
| `dfp` | string | O | deviceFingerprint |
| `ent` | string[] | O | entitlements |
| `iat` | number | O | issued at (epoch seconds) |
| `exp` | number | O | expiry (epoch seconds) |
| `jti` | string | X | 토큰 ID (재사용 탐지용, 추후) |

### 5.3 만료 정책 (권장)

| 시나리오 | 만료 시간 | 갱신 방법 |
|---------|----------|----------|
| **온라인 세션 토큰** | 10~30분 | 앱은 exp 만료 전에 heartbeat (또는 validate)로 갱신 |
| **오프라인** | v0.2-min에서는 "기존 offlineToken" 유지 가능 | 기능 unlock의 1차 기준은 sessionToken |

> 오프라인 허용을 제대로 하려면 v0.3에서 `offlineSessionToken` (JWS)로 확장 권장

### 5.4 검증 규칙 (⚠️ SOT 기반 필수)

앱은 `session.json`을 읽은 뒤 **토큰 기반으로 검증**해야 함:

```
1. 서명 검증 성공 (내장 공개키로 RS256 검증)
2. aud == productCode
3. dfp == 현재 기기 fingerprint
4. exp > now (유효)
5. ent(entitlements) 클레임 기반 기능 unlock
```

> **⚠️ SOT 주의사항**:
> - `"valid": true`, `"status": "ACTIVE"`, `"entitlements": [...]` 필드는 **UI 표시/참고용**입니다.
> - **기능 unlock 판단에 절대 사용 금지** - 토큰 클레임만 사용하세요.
> - **최종 unlock은 sessionToken/offlineToken 검증을 통과해야 합니다.**

---

## 6. API 응답 스펙 (In-process 모듈)

### 6.1 Validate 응답 구조

```json
{
  "valid": true,
  "resolution": "OK",
  "licenseId": "uuid",
  "status": "ACTIVE",
  "validUntil": "2026-12-05T10:00:00Z",
  "entitlements": ["core-simulation"],
  "sessionToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9....",
  "offlineToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9....",
  "offlineTokenExpiresAt": "2026-01-10T00:00:00Z",
  "serverTime": "2026-01-09T10:00:00Z"
}
```

**응답 필드 분류:**
| 필드 | SOT 여부 | 용도 |
|------|---------|------|
| `sessionToken` | ✅ **SOT** | 온라인 기능 unlock 기준 |
| `offlineToken` | ✅ **SOT** | 오프라인 기능 unlock 기준 |
| `valid` | ❌ 참고용 | UI 표시용 (권한 판단 금지) |
| `status` | ❌ 참고용 | UI 표시용 (권한 판단 금지) |
| `entitlements` | ❌ 참고용 | UI 표시용 (토큰 `ent` 클레임 사용) |

### 6.2 Force-Validate 응답 구조

동일하게 **sessionToken/offlineToken이 SOT**

### 6.3 Heartbeat 응답 구조

```json
{
  "valid": true,
  "licenseId": "uuid",
  "status": "ACTIVE",
  "sessionToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9....",
  "offlineToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...."
}
```

> **⚠️ SOT 주의사항**: `valid`, `status` 필드는 UI 표시용입니다. 기능 unlock은 토큰 검증 기준.

---

## 7. session.json 스키마 (v3)

### 7.1 파일 내용

```json
{
  "schemaVersion": "3",
  "productCode": "BULC_EVAC",
  "licenseId": "uuid",
  "deviceFingerprint": "hw-hash-abc123",

  "sessionToken": "eyJ...",
  "offlineToken": "eyJ...",
  "offlineTokenExpiresAt": "2026-01-10T00:00:00Z",

  "status": "ACTIVE",
  "validUntil": "2026-12-05T10:00:00Z",
  "entitlements": ["core-simulation"],

  "issuedAt": "2025-12-29T03:00:00Z",
  "serverTime": "2026-01-09T10:00:00Z"
}
```

**필드 분류:**
| 필드 | SOT 여부 | 용도 |
|------|---------|------|
| `sessionToken` | ✅ **SOT** | 기능 unlock 기준 (토큰 검증 필수) |
| `offlineToken` | ✅ **SOT** | 오프라인 unlock 기준 (토큰 검증 필수) |
| `status`, `entitlements` | ❌ 참고용 | UI 표시용 (권한 판단 금지) |

### 7.2 스키마 버전 규칙

- **schemaVersion**: `"3"` 로 상승 (SOT 원칙 반영)
- 앱은 `schemaVersion < 3`이면 **거부** (또는 재validate 유도)

---

## 8. 공개키 내장 방식 (고정)

### 8.1 배포 형태

- 공개키는 **PEM 또는 Base64 DER**로 앱/CLI에 **하드코딩** (리소스 파일로 포함해도 됨)
- **키 회전**은 v0.2-min에서는 고려하지 않음 (추후 v0.3에서 multi-key 지원)

### 8.2 구현 권장

| 플랫폼 | 권장 라이브러리 |
|--------|----------------|
| **Java** | `java.security.Signature` 또는 **Nimbus JOSE JWT**로 RS256 검증 |
| **.NET** | `RSA.ImportFromPem()` + `JwtSecurityTokenHandler.ValidateToken()` |

### 8.3 공개키 예시 (PEM 형식)

```
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...
(실제 키는 서버와 동기화하여 생성)
-----END PUBLIC KEY-----
```

---

## 9. 서버 요구사항 (백엔드)

| 요구사항 | 설명 |
|---------|------|
| sessionToken 포함 | `validate`/`validate-force` 응답에 **sessionToken을 포함** |
| RS256 서명 | sessionToken은 **RS256으로 서명** |
| Claims 규칙 | 위 5.2 claims 규칙을 만족 |
| dfp 바인딩 | `dfp`는 요청의 `deviceFingerprint`로 바인딩 |

---

## 10. 앱 통합 가이드 (v0.3 In-process)

### 10.1 기능 Unlock 흐름 (⚠️ SOT 기반)

```
1. BulcAuth.checkLoginStatus() → 로그인 상태 확인
2. BulcLicense.validate() → ValidationResponse 획득
3. sessionToken 검증 (⚠️ 이것이 기능 unlock의 SOT)
   - 서명 검증 (내장 공개키로 RS256 검증)
   - aud == productCode
   - dfp == 현재 기기 fingerprint
   - exp > now
   - ent(entitlements) 클레임 확인
4. 토큰 검증 성공 시 기능 unlock
```

> **⚠️ 필수 규칙**:
> - `response.valid`, `response.status`, `response.entitlements`는 **참고용**
> - **기능 unlock은 오직 토큰 검증 기준으로만 결정**

### 10.2 주기적 갱신

```
1. heartbeat 주기: 5~15분 권장
2. heartbeat 성공 시 새 sessionToken으로 갱신
3. exp 만료 전에 반드시 갱신
4. 갱신된 토큰 클레임 기준으로 기능 상태 업데이트
```

### 10.3 검증 실패 시 처리

| 실패 사유 | 처리 |
|----------|------|
| 토큰 서명 검증 실패 | "무효한 세션" → 재로그인/재검증 유도 |
| aud 불일치 | "잘못된 제품" → 에러 표시 |
| dfp 불일치 | "다른 기기의 세션" → 재검증 유도 |
| exp 만료 | "세션 만료" → heartbeat 또는 재검증 |
| ent 클레임 없음 | 해당 기능 비활성화 |

### 10.4 ❌ 잘못된 구현 예시

```java
// ❌ 잘못된 구현 - 응답 본체만 확인
if (response.valid && response.status.equals("ACTIVE")) {
    unlockFeature();  // 보안 위반!
}

// ✅ 올바른 구현 - 토큰 검증 후 unlock
TokenValidationResult result = tokenValidator.validate(response.sessionToken);
if (result.isValid() && result.getEntitlements().contains("core-simulation")) {
    unlockFeature();
}
```

---

## 11. 구현 우선순위 (권장)

| 순서 | 작업 | 설명 |
|------|------|------|
| 1 | sessionToken 검증 계약 | session.json 작성/읽기 + JWT 검증 로직 고정 |
| 2 | bulc-auth 로그인/토큰 저장 | OAuth PKCE + DPAPI 저장 |
| 3 | bulc-lic validate/force | sessionToken 포함해 저장 |
| 4 | 앱 (Java) sessionToken 검증 | 공개키 내장 + JWT 검증 후 기능 unlock |
| 5 | heartbeat 갱신 | 만료 전 재발급 |

---

## 12. 남은 리스크 (명시)

### 인정하는 리스크

| 리스크 | 대응 전략 |
|--------|----------|
| 앱 바이너리 패치로 sessionToken 검증 제거 시 우회 가능 | **완전 방지하지 않고**, 운영 탐지/제재 및 업데이트로 비용을 올리는 전략 채택 |

### 방어하는 공격

| 공격 | 방어 |
|------|------|
| CLI 바꿔치기 (가짜 bulc-lic.exe) | sessionToken 서명 검증 실패 |
| session.json 조작 | sessionToken 서명 검증 실패 |
| stdout 조작 (프록시 등) | sessionToken 서명 검증 실패 |
| 다른 PC의 session.json 복사 | dfp 불일치로 거부 |

---

## 13. In-process API 요약 (v0.3)

### BulcAuth 모듈

| Method | 목적 | 반환 |
|--------|------|------|
| `checkLoginStatus()` | 로그인 상태 확인 | `AuthStatus` |
| `login()` | 브라우저 로그인 (PKCE) | `LoginResult` |
| `logout()` | 로컬 토큰 삭제 | `void` |

### BulcLicense 모듈

| Method | 목적 | 반환 | SOT |
|--------|------|------|-----|
| `getDeviceFingerprint()` | fingerprint 조회 | `string` | - |
| `listLicenses()` | 라이선스 목록 | `License[]` | - |
| `validate()` | 검증 + 토큰 획득 | `ValidationResponse` | `sessionToken` ✅ |
| `forceValidate()` | 강제 검증 | `ValidationResponse` | `sessionToken` ✅ |
| `heartbeat()` | 세션 갱신 | `HeartbeatResponse` | `sessionToken` ✅ |

> **⚠️ SOT 주의**: 모든 응답의 `valid`, `status`, `entitlements`는 **참고용**.
> 기능 unlock은 반드시 **토큰 검증** 기준으로 결정.

---

## 14. .NET 8 구현 구조 (v0.2 추가사항)

### 추가 컴포넌트

#### Bulc.Common

| 컴포넌트 | 책임 |
|---------|------|
| `JwtValidator` | RS256 공개키로 sessionToken 검증 |
| `EmbeddedKeys` | 내장 공개키 로드 (PEM → RSA) |

#### Bulc.Lic.Cli

| 컴포넌트 | 책임 |
|---------|------|
| `SessionWriter` | schemaVersion=2, sessionToken 포함 저장 |

### 솔루션 구조 (확장)

```
bulc-cli/
  src/
    Bulc.Common/
      Security/
        JwtValidator.cs      # RS256 검증
        EmbeddedKeys.cs      # 공개키 내장
        PublicKey.pem        # 내장 리소스
    Bulc.Lic.Cli/
      Session/
        SessionWriter.cs     # schemaVersion=2
```

---

## 변경 이력

| 버전 | 날짜 | 변경 내용 |
|------|------|----------|
| v0.1 | 2025-12-29 | 초기 스펙 작성 |
| v0.2-min | 2025-12-29 | sessionToken (JWS) 필수 도입, schemaVersion=2, 공개키 내장 |
| v0.3 | 2026-01-09 | **CLI → In-process 모듈 전환**, SOT 원칙 명확화, offlineToken RS256 통일, schemaVersion=3 |

### v0.3 주요 변경사항

1. **SOT (Source of Truth) 원칙 명확화**
   - `sessionToken`/`offlineToken`이 기능 unlock의 유일한 기준
   - 응답 본체의 `valid`, `status`, `entitlements`는 UI 표시용 참고 값
   - 권한 판단에 응답 본체 사용 금지

2. **CLI → In-process 모듈 전환**
   - `bulc-auth.exe`, `bulc-lic.exe` → `BulcAuth`, `BulcLicense` 라이브러리
   - 프로세스 간 통신 오버헤드 제거
   - stdout/stderr 파싱 불필요

3. **offlineToken RS256 통일**
   - sessionToken과 동일한 RS256 서명 방식
   - 오프라인에서도 공개키로 검증 가능

4. **session.json schemaVersion 3**
   - SOT 필드 구분 명확화
   - `serverTime` 필드 추가
