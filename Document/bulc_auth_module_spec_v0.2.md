# BulC Licensing Auth Module Spec v0.2-min (CLI, Embedded Public Key)

## 0. v0.1 → v0.2-min 핵심 변경점

### sessionToken (JWS) 도입 (필수)

- `validate`/`validate-force` 성공 시 **sessionToken을 반드시 포함**
- `session.json`에도 **sessionToken을 저장**
- **앱은 기능 unlock 시 sessionToken 검증 필수**
- session.json의 나머지 필드는 "표시/캐시" 성격

### 검증키 내장

- sessionToken 검증키는 **앱/CLI에 공개키를 고정 내장**
- JWKS fetch 없음 (초기 단순화)
- CLI/앱은 "valid=true" 같은 값보다 **sessionToken의 클레임(entitlements/exp 등)을 최종 판단 근거**로 사용

---

## 1. 목표 / 위협 모델 (요약)

### 방어 목표

- **단순 CLI 바꿔치기** / **session.json 조작** / **stdout 조작**으로 기능이 열리는 것을 방지한다.

### 비목표 (한계 인정)

- 고급 공격(앱 패치/후킹)은 완전 방지 목표가 아니며, **비용을 올리고 서버 측 운영/탐지로 대응**한다.

---

## 2. 구성 요소

### 2.1 바이너리

| 바이너리 | 역할 |
|---------|------|
| `bulc-auth.exe` | 브라우저 로그인 + 토큰 저장/갱신 |
| `bulc-lic.exe` | 라이선스 list/validate/force/heartbeat + session.json 생성 |

### 2.2 설치 경로 (고정)

```
C:\Program Files\Bulc\Common\bulc-auth.exe
C:\Program Files\Bulc\Common\bulc-lic.exe
```

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

### 5.4 검증 규칙 (앱/CLI 공통)

앱은 `session.json`을 읽은 뒤 **아래를 모두 검증**해야 함:

```
1. 서명 검증 성공 (내장 공개키)
2. aud == productCode
3. dfp == 현재 기기 fingerprint
4. exp > now (유효)
5. entitlements 기반 기능 unlock
```

> **중요**: `"valid": true` 같은 필드는 **참고로만** 사용 가능.
> **최종 unlock은 sessionToken 검증을 통과해야 한다.**

---

## 6. CLI 명령 스펙 (v0.1 대비 변경점)

### 6.1 `bulc-lic validate` 성공 출력 (변경)

v0.1 성공 JSON에 **sessionToken 추가 (필수)**:

```json
{
  "valid": true,
  "licenseId": "uuid",
  "status": "ACTIVE",
  "validUntil": "2026-12-05T10:00:00Z",
  "entitlements": ["core-simulation"],
  "offlineToken": "opaque",
  "offlineTokenExpiresAt": "2026-01-10T00:00:00Z",
  "sessionToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9....",
  "sessionPath": "C:\\ProgramData\\Bulc\\Session\\BULC_EVAC\\session.json"
}
```

### 6.2 `bulc-lic validate-force` 성공 출력

동일하게 **sessionToken 포함**

### 6.3 `bulc-lic heartbeat` 성공 출력 (변경)

갱신된 sessionToken 포함:

```json
{
  "valid": true,
  "licenseId": "uuid",
  "status": "ACTIVE",
  "sessionToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...."
}
```

---

## 7. session.json 스키마 (v2)

### 7.1 파일 내용

```json
{
  "schemaVersion": "2",
  "productCode": "BULC_EVAC",
  "licenseId": "uuid",
  "deviceFingerprint": "hw-hash-abc123",

  "sessionToken": "eyJ...",

  "status": "ACTIVE",
  "validUntil": "2026-12-05T10:00:00Z",
  "entitlements": ["core-simulation"],

  "offlineToken": "opaque",
  "offlineTokenExpiresAt": "2026-01-10T00:00:00Z",

  "issuedAt": "2025-12-29T03:00:00Z"
}
```

### 7.2 스키마 버전 규칙

- **schemaVersion**: `"2"` 로 상승
- 앱은 `schemaVersion < 2`이면 **거부** (또는 재validate 유도)

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

## 10. 앱 통합 가이드 (v0.2)

### 10.1 기능 Unlock 흐름

```
1. bulc-auth status → 로그인 상태 확인
2. bulc-lic validate → sessionToken 획득
3. session.json 읽기
4. sessionToken 검증 (공개키로 서명 확인)
   - aud == productCode
   - dfp == 현재 기기 fingerprint
   - exp > now
5. entitlements 확인 후 기능 unlock
```

### 10.2 주기적 갱신

```
1. heartbeat 주기: 5~15분 권장
2. heartbeat 성공 시 새 sessionToken으로 session.json 업데이트
3. exp 만료 전에 반드시 갱신
```

### 10.3 검증 실패 시 처리

| 실패 사유 | 처리 |
|----------|------|
| 서명 검증 실패 | "무효한 세션" → 재로그인/재검증 유도 |
| aud 불일치 | "잘못된 제품" → 에러 표시 |
| dfp 불일치 | "다른 기기의 세션" → 재검증 유도 |
| exp 만료 | "세션 만료" → heartbeat 또는 재검증 |
| entitlements 없음 | 해당 기능 비활성화 |

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

## 13. CLI 명령 전체 요약 (v0.2)

### bulc-auth.exe

| Command | 목적 | 성공 stdout |
|---------|------|-------------|
| `bulc-auth status` | 로그인 상태 확인 | `{ "ok": true, "signedIn": true, "accessTokenExpiresAt": "..." }` |
| `bulc-auth login` | 브라우저 로그인 (PKCE) | `{ "ok": true }` |
| `bulc-auth logout` | 로컬 토큰 삭제 | `{ "ok": true }` |

### bulc-lic.exe

| Command | 목적 | 성공 stdout |
|---------|------|-------------|
| `bulc-lic version` | CLI 버전 | `{ "ok": true, "version": "0.2.0" }` |
| `bulc-lic device-id` | fingerprint 조회 | `{ "ok": true, "deviceFingerprint": "..." }` |
| `bulc-lic list` | 라이선스 목록 | `{ "licenses": [...] }` |
| `bulc-lic validate` | 검증 + sessionToken | `{ "valid": true, "sessionToken": "eyJ...", ... }` |
| `bulc-lic validate-force` | 강제 검증 | `{ "valid": true, "sessionToken": "eyJ...", ... }` |
| `bulc-lic heartbeat` | 세션 갱신 | `{ "valid": true, "sessionToken": "eyJ...", ... }` |

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
