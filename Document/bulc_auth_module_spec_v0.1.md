# BulC Licensing Auth Module Spec v0.1 (CLI)

## 1. 목적

### 1.1 목표

- **상주 런처 없이** (불필요한 프로세스 상주 금지) 각 제품 앱이 실행 중일 때만 라이선스를 검증한다.
- **언어/프레임워크 독립**: Java/Unity/.NET 등 어떤 앱이든 동일한 방식으로 통합 가능.
- **계정 기반 인증**: 브라우저 로그인(OAuth2 Authorization Code + PKCE)으로 안전하게 토큰을 획득/갱신한다.
- **라이선스 검증은 CLI가 담당**하고, 앱은 CLI를 호출해 표준 JSON 결과만 해석한다.
- **동시 세션 초과 시 2-phase validate + force UX**를 지원한다(기존 세션 종료 선택).

### 1.2 비목표 (Non-goals)

- 앱 업데이트/패치/다운로드 매니저 기능(런처 기능) 제공하지 않음.
- 백엔드 발급/환불/정지 등 Admin/Billing 기능은 CLI가 수행하지 않음.
- DRM 수준의 완벽한 치트 방지(로컬 공격자/바이너리 패치 완전 방어)는 목표가 아님.

---

## 2. 구성 요소

### 2.1 바이너리

| 바이너리 | 역할 |
|---------|------|
| `bulc-auth.exe` | 브라우저 로그인, 토큰 저장/갱신/로그아웃 담당 |
| `bulc-lic.exe` | 내 라이선스 목록 조회, validate/force/heartbeat, session 파일 생성 담당 |

### 2.2 설치 경로 (고정)

```
C:\Program Files\Bulc\Common\bulc-auth.exe
C:\Program Files\Bulc\Common\bulc-lic.exe
```

> 앱은 작업 디렉토리/상대경로에서 CLI를 찾지 않는다. **반드시 설치된 고정 경로에서 실행**한다.

---

## 3. 데이터 저장 (Windows)

### 3.1 Token Store (필수)

- **Refresh token 등 민감정보**는 **DPAPI (사용자 범위)**로 암호화하여 저장한다.
- **저장 위치 (권장)**:
  ```
  %AppData%\Bulc\Auth\tokens.dat  (DPAPI 암호화 파일)
  ```
- **저장 스코프**: 기본은 "BulC 공용 계정" (향후 제품별 client_id 분리 가능)

### 3.2 Local Session Output (앱이 읽는 표준)

- **기본 저장 위치 (권장)**:
  ```
  %ProgramData%\Bulc\Session\{productCode}\session.json
  ```
- 앱이 CLI에 `--out`으로 경로를 지정할 수도 있다.

---

## 4. 표준 출력 규칙 (가장 중요)

### 4.1 출력 모드

- **기본**: `--json` 모드 (권장 기본값)
- **stdout**: JSON만 출력
- **stderr**: 사람 읽는 로그/진단 메시지(기본은 최소), **토큰/코드 등 민감정보 출력 금지**

### 4.2 Exit Code 규약 (고정)

| Exit Code | 의미 | 앱 권장 처리 |
|-----------|------|-------------|
| **0** | 성공 | stdout JSON 파싱 후 진행 |
| **10** | 인증 필요/토큰 문제 | 로그인 유도 (`bulc-auth login`) |
| **20** | 라이선스 불가 (없음/만료/정지/회수/동시세션 초과 등) | 라이선스 선택/구매/데모 모드 |
| **30** | 네트워크 문제 | 오프라인 모드 판단 (offline token) |
| **40** | 서버 오류 | 재시도/안내 |
| **50** | 클라이언트 오류 (입력/환경/파일/권한) | 개발자 로그/에러 안내 |

> 세부 원인은 항상 stdout JSON의 `errorCode`로 식별한다 (가능한 경우).

---

## 5. 공통 JSON 포맷

### 5.1 성공 공통

```json
{ "ok": true }
```

### 5.2 실패 공통

```json
{
  "ok": false,
  "errorCode": "STRING_CODE",
  "errorMessage": "HUMAN_READABLE",
  "hint": "OPTIONAL_HINT"
}
```

### 5.3 라이선스 검증 계열 공통 (백엔드와 맞춤)

validate/heartbeat/force 계열은 기존 API 문서 스타일을 유지:

```json
{
  "valid": false,
  "errorCode": "LICENSE_NOT_FOUND",
  "errorMessage": "해당 제품의 라이선스가 없습니다"
}
```

---

## 6. Device Fingerprint

### 6.1 요구사항

- Windows 장치에서 재설치/재부팅에 비교적 안정적인 값 기반
- 개인정보 과다 수집 금지 (표시 목적 외 저장 최소화)

### 6.2 제공 방식

- `bulc-lic device-id` 명령으로 fingerprint 생성/조회
- 앱이 직접 만들지 않고 **CLI가 생성한 값을 사용**하는 것을 기본으로 한다.

---

## 7. Command Spec

### 7.1 bulc-auth 명령

#### 7.1.1 `bulc-auth login`

브라우저를 열어 사용자 로그인 후 토큰을 저장한다 (PKCE + loopback redirect).

**Command:**
```bash
bulc-auth login --authority <URL> --clientId <ID> --scopes <SCOPE1,SCOPE2> [--timeoutSec 180]
```

**Output (stdout):**
```json
{ "ok": true }
```

**실패:**
- Exit 10: 로그인 실패/토큰 교환 실패
- Exit 30: 네트워크 불가
- Exit 50: 로컬 콜백 서버 구동 실패 등

#### 7.1.2 `bulc-auth logout`

저장된 토큰 삭제 (로컬 로그아웃).

```bash
bulc-auth logout
```

#### 7.1.3 `bulc-auth status`

로그인 상태 (토큰 존재/만료 여부).

```json
{
  "ok": true,
  "signedIn": true,
  "accessTokenExpiresAt": "2025-12-29T04:00:00Z"
}
```

### 7.2 bulc-lic 명령

#### 7.2.1 `bulc-lic list`

내 라이선스 목록 조회 (제품별 필터 포함).

**Command:**
```bash
bulc-lic list --productCode <CODE> [--status ACTIVE] [--json]
```

**Output:**
```json
{
  "licenses": [
    {
      "licenseId": "uuid",
      "productCode": "BULC_EVAC",
      "productName": "BulC Evac",
      "planName": "Pro 연간 구독",
      "licenseType": "SUBSCRIPTION",
      "status": "ACTIVE",
      "validUntil": "2026-12-05T10:00:00Z",
      "usedActivations": 1,
      "maxActivations": 3,
      "maxConcurrentSessions": 2,
      "displayHint": "Pro 연간 / 2026-12-05"
    }
  ]
}
```

#### 7.2.2 `bulc-lic device-id`

현재 PC의 fingerprint 생성/조회.

**Command:**
```bash
bulc-lic device-id
```

**Output:**
```json
{ "deviceFingerprint": "hw-hash-abc123" }
```

#### 7.2.3 `bulc-lic validate`

앱 시작 시 호출. 성공하면 session.json 생성 가능.

**Command:**
```bash
bulc-lic validate --productCode <CODE> --licenseId <UUID> [--deviceFingerprint <HASH>] [--out <PATH>]
```

- `--deviceFingerprint`가 없으면 CLI가 내부에서 생성한 값을 사용한다.
- `--out`이 있으면 해당 경로에 session.json 저장. 없으면 표준 위치에 저장.

**성공 Output:**
```json
{
  "valid": true,
  "licenseId": "uuid",
  "status": "ACTIVE",
  "validUntil": "2026-12-05T10:00:00Z",
  "entitlements": ["core-simulation"],
  "offlineToken": "opaque",
  "offlineTokenExpiresAt": "2026-01-10T00:00:00Z",
  "sessionPath": "C:\\ProgramData\\Bulc\\Session\\BULC_EVAC\\session.json"
}
```

**동시 세션 초과 (2-phase)** — validate 결과로 "선택 UX" 제공

Exit code는 **20** (라이선스 불가)로 두되, 앱이 JSON을 보고 "force 가능" UX를 띄운다.

```json
{
  "valid": false,
  "errorCode": "CONCURRENT_SESSION_LIMIT_EXCEEDED",
  "errorMessage": "동시 실행 가능한 세션 수를 초과했습니다.",
  "maxConcurrentSessions": 2,
  "sessionTtlMinutes": 30,
  "activeSessions": [
    {
      "activationId": "uuid-a",
      "deviceDisplayName": "DESKTOP-1234",
      "lastSeenAt": "2025-12-26T02:10:00Z"
    }
  ],
  "nextAction": "VALIDATE_FORCE_AVAILABLE"
}
```

#### 7.2.4 `bulc-lic validate-force`

사용자가 "기존 세션 종료 후 계속"을 선택했을 때 호출.

**Command:**
```bash
bulc-lic validate-force --productCode <CODE> --licenseId <UUID> --deactivateActivationIds <UUID1,UUID2> [--deviceFingerprint <HASH>] [--out <PATH>]
```

**성공 Output:** validate와 동일 (세션 생성 포함)

**실패 예:**
- 경쟁 조건으로 여전히 초과: `CONCURRENT_SESSION_LIMIT_EXCEEDED`
- activationId 불일치: `ACTIVATION_NOT_FOUND` 또는 `INVALID_REQUEST`

#### 7.2.5 `bulc-lic heartbeat`

앱 실행 중 주기 호출 (5~15분). 상주 프로세스는 없고, 앱이 실행 중일 때만 호출.

**Command:**
```bash
bulc-lic heartbeat --productCode <CODE> --licenseId <UUID> [--deviceFingerprint <HASH>]
```

**성공:**
```json
{ "valid": true, "licenseId": "uuid", "status": "ACTIVE" }
```

**세션 종료됨 (기존 세션이 force로 끊김):**
```json
{
  "valid": false,
  "errorCode": "SESSION_DEACTIVATED",
  "errorMessage": "다른 기기에서 세션이 활성화되어 현재 세션이 종료되었습니다."
}
```

---

## 8. session.json 스키마 (앱 계약)

### 8.1 파일 내용 (최소)

```json
{
  "schemaVersion": "1",
  "productCode": "BULC_EVAC",
  "licenseId": "uuid",
  "deviceFingerprint": "hw-hash-abc123",
  "status": "ACTIVE",
  "validUntil": "2026-12-05T10:00:00Z",
  "entitlements": ["core-simulation"],
  "offlineToken": "opaque",
  "offlineTokenExpiresAt": "2026-01-10T00:00:00Z",
  "issuedAt": "2025-12-29T03:00:00Z"
}
```

### 8.2 조작 방지 (권장, v0.2 후보)

- `sessionToken` (서명된 토큰)을 추가하여 앱이 파일 변조를 감지할 수 있게 한다.
- 초기 v0.1에서는 필수는 아니지만, 외부 배포면 빠르게 도입 추천.

---

## 9. 보안 요구사항

### 9.1 필수

- Refresh token은 **반드시 DPAPI로 암호화** 저장.
- CLI는 민감정보(토큰/code/verifier)를 로그로 출력하지 않는다.
- CLI는 **127.0.0.1 loopback 콜백**을 사용하며 외부 바인딩 금지.

### 9.2 권장

- `bulc-auth.exe`, `bulc-lic.exe`는 **코드 서명 (Authenticode)** 한다.
- 앱은 실행 파일을 **고정 경로에서만 실행** (상대경로 금지).
- 앱이 CLI 출력만 믿지 않고, 가능한 경우(online) validate/heartbeat 결과를 신뢰한다.

---

## 10. 버전/호환성 정책

### 10.1 CLI 버전 규칙

- CLI는 `bulc-lic version`으로 버전 출력 가능 (추가 커맨드 또는 `--version`)
- 앱은 최소 요구 CLI 버전을 체크할 수 있다.

### 10.2 스키마 버전

- session.json에 `schemaVersion` 포함 (필수)
- 파괴적 변경 시 `schemaVersion` 증가

---

## 11. 통합 가이드 (앱 개발자 관점)

### 11.1 앱이 해야 하는 최소 동작

**실행 시:**
1. `bulc-auth status` 확인 (미로그인 시 로그인 버튼 제공)
2. 로그인 필요 시 `bulc-auth login` 호출 (사용자 액션 기반)
3. `bulc-lic list`로 라이선스 후보 표시 (복수면 선택)
4. `bulc-lic validate` 호출 → 성공 시 session.json을 읽고 기능 활성화

**실행 중:**
1. 주기적으로 `bulc-lic heartbeat` 호출
2. `SESSION_DEACTIVATED`면 "세션 종료됨" UI + 재검증/재로그인 유도

### 11.2 동시 세션 초과 UX (2-phase)

validate 결과가 `CONCURRENT_SESSION_LIMIT_EXCEEDED`면:
1. `activeSessions` 목록을 사용자에게 보여줌
2. "선택한 세션 종료 후 계속" 선택 시 `validate-force` 호출

---

## 12. 구현 기술 (권장)

| 항목 | 권장 기술 |
|------|----------|
| CLI 구현 언어 | .NET 8 (Windows-only 최적) |
| CLI 프레임워크 | System.CommandLine 또는 Spectre.Console.Cli |
| Loopback 서버 | ASP.NET Core Minimal API (Kestrel)로 127.0.0.1:0 바인딩 |
| 저장 | DPAPI (ProtectedData) 기반 암호화 파일 또는 Windows Credential Manager |

---

## 13. CLI 스펙 원페이지 (고정)

### 공통 규칙

- 모든 명령은 기본적으로 `--json` 출력 (기본값).
- `--json` 없이도 JSON만 출력하도록 강제 권장
- **stdout**: JSON만
- **stderr**: 진단 로그(기본 최소) / 민감정보 금지
- **Exit code**: 0/10/20/30/40/50 고정

### 공통 실패 JSON

```json
{ "ok": false, "errorCode": "STRING", "errorMessage": "STRING", "hint": "STRING(optional)" }
```

### bulc-auth.exe

| Command | 목적 | 입력 옵션 | 성공 stdout (JSON) | 대표 실패 / Exit |
|---------|------|----------|-------------------|-----------------|
| `bulc-auth status` | 로그인 상태 확인 | (없음) | `{ "ok": true, "signedIn": true, "accessTokenExpiresAt": "..." }` | 토큰 스토어 손상(50) |
| `bulc-auth login` | 브라우저 로그인 후 토큰 저장 (PKCE+loopback) | `--authority <url> --clientId <id> --scopes <csv> --timeoutSec <n=180>` | `{ "ok": true }` | 네트워크(30), 로그인/토큰교환 실패(10), loopback 서버 실패(50) |
| `bulc-auth logout` | 로컬 토큰 삭제 | (없음) | `{ "ok": true }` | 파일 권한/IO(50) |
| `bulc-auth token` | access token 존재 확인/만료 확인 (디버그 전용) | `--print`(옵션) | `{ "ok": true, "hasAccessToken": true, "expiresAt": "..." }` | 운영 빌드에서 `--print` 금지(50) |

> 운영 안전 위해 `bulc-auth token --print`는 **개발용 빌드에서만 허용** 추천.

### bulc-lic.exe

| Command | 목적 | 입력 옵션 | 성공 stdout (JSON) | 대표 실패 / Exit |
|---------|------|----------|-------------------|-----------------|
| `bulc-lic version` | CLI 버전 | (없음) | `{ "ok": true, "version": "0.1.0" }` | - |
| `bulc-lic device-id` | deviceFingerprint 생성/조회 | `--refresh`(재생성 옵션은 기본 비추) | `{ "ok": true, "deviceFingerprint": "..." }` | 환경/권한(50) |
| `bulc-lic list` | 내 라이선스 목록 | `--productCode <code> --status <ACTIVE\|...>(opt)` | `{ "licenses":[...] }` | 인증 필요(10), 네트워크(30) |
| `bulc-lic validate` | 실행 전 검증(+activation) | `--productCode <code> --licenseId <uuid> --deviceFingerprint <hash>(opt) --out <path>(opt) --deviceName <name>(opt)` | `{ "valid": true, ..., "sessionPath":"..." }` | 인증 필요(10), 라이선스 불가(20), 네트워크(30) |
| `bulc-lic validate-force` | 동시세션 초과 시, 기존 세션 종료 후 계속 | `--productCode <code> --licenseId <uuid> --deactivateActivationIds <csv> --deviceFingerprint <hash>(opt) --out <path>(opt)` | validate 성공과 동일 | 인증 필요(10), 라이선스 불가/경쟁조건 409(20), 네트워크(30) |
| `bulc-lic heartbeat` | 실행 중 주기 갱신 | `--productCode <code> --licenseId <uuid> --deviceFingerprint <hash>(opt)` | `{ "valid": true, "licenseId":"...", "status":"ACTIVE" }` | SESSION_DEACTIVATED(20), 인증 필요(10), 네트워크(30) |

### validate / validate-force 성공 JSON (고정)

```json
{
  "valid": true,
  "licenseId": "uuid",
  "status": "ACTIVE",
  "validUntil": "2026-12-05T10:00:00Z",
  "entitlements": ["core-simulation"],
  "offlineToken": "opaque",
  "offlineTokenExpiresAt": "2026-01-10T00:00:00Z",
  "sessionPath": "C:\\ProgramData\\Bulc\\Session\\BULC_EVAC\\session.json"
}
```

### 동시 세션 초과 (JSON 고정 / Exit=20)

```json
{
  "valid": false,
  "errorCode": "CONCURRENT_SESSION_LIMIT_EXCEEDED",
  "errorMessage": "동시 실행 가능한 세션 수를 초과했습니다.",
  "maxConcurrentSessions": 2,
  "sessionTtlMinutes": 30,
  "activeSessions": [
    { "activationId": "uuid-a", "deviceDisplayName": "DESKTOP-1234", "lastSeenAt": "2025-12-26T02:10:00Z" }
  ],
  "nextAction": "VALIDATE_FORCE_AVAILABLE"
}
```

### heartbeat에서 세션 종료됨 (JSON 고정 / Exit=20)

```json
{
  "valid": false,
  "errorCode": "SESSION_DEACTIVATED",
  "errorMessage": "다른 기기에서 세션이 활성화되어 현재 세션이 종료되었습니다."
}
```

---

## 14. .NET 8 구현 구조 (권장)

### 솔루션 레이아웃

```
bulc-cli/
  Bulc.Cli.sln
  src/
    Bulc.Common/
      Bulc.Common.csproj
      Json/
      Http/
      Security/
      Storage/
      Time/
    Bulc.Auth.Cli/
      Bulc.Auth.Cli.csproj
      Program.cs
      Commands/
      OAuth/
      Loopback/
      TokenStore/
    Bulc.Lic.Cli/
      Bulc.Lic.Cli.csproj
      Program.cs
      Commands/
      Device/
      Licensing/
      Session/
  tests/
    Bulc.Common.Tests/
    Bulc.Auth.Tests/
    Bulc.Lic.Tests/
  packaging/
    installer/
    signing/
    release-notes/
```

### 핵심 의도

- **Bulc.Common**: 재사용 가능한 핵심 (HTTP, JSON, DPAPI storage, error/exit 규약)
- **Bulc.Auth.Cli**: OAuth/PKCE + loopback + 토큰 저장/갱신
- **Bulc.Lic.Cli**: 라이선스 관련 명령 (목록/validate/force/heartbeat) + session.json 생성

### 필수 기술 선택 (고정 추천)

| 항목 | 선택 |
|------|------|
| CLI 프레임워크 | System.CommandLine (단순/표준) |
| HTTP | HttpClientFactory |
| JSON | System.Text.Json (source generator 가능) |
| Loopback 서버 | Kestrel Minimal API로 127.0.0.1:0 바인딩 |
| 암호화 저장 | System.Security.Cryptography.ProtectedData (DPAPI, CurrentUser) |
| 설정 | appsettings.json은 최소화하고, 대부분 CLI 옵션 + 기본값 |

### 공통 컴포넌트 책임 (구체)

#### Bulc.Common

| 컴포넌트 | 책임 |
|---------|------|
| `ExitCodes` | 0/10/20/30/40/50 상수 정의 |
| `CliJsonWriter` | stdout JSON만 출력 (성공/실패/valid응답) |
| `HttpErrorMapper` | 예외/HTTP status → errorCode, exit code 매핑 |
| `DpapiFileStore` | DPAPI 암호화 파일 읽기/쓰기 |
| `PathProvider` | ProgramFiles 공용 경로, `%AppData%\Bulc\Auth\`, `%ProgramData%\Bulc\Session\{productCode}\` |
| `Clock` | UTC 시간 통일 |

#### Bulc.Auth.Cli

| 컴포넌트 | 책임 |
|---------|------|
| `PkceGenerator` | verifier/challenge (state 포함) |
| `LoopbackServer` | start(127.0.0.1:0) → 실제 port 반환, callback 수신(code/state) → TaskCompletionSource로 전달 |
| `OAuthClient` | authorize URL 생성, token exchange (code + verifier), refresh token |
| `TokenStore` | DPAPI 저장/로드, 만료 체크 |

#### Bulc.Lic.Cli

| 컴포넌트 | 책임 |
|---------|------|
| `DeviceFingerprintProvider` | 안정적 입력 기반 해시 생성 (최소 구현부터) |
| `LicensingApiClient` | `/api/me/licenses`, `/api/licenses/validate`, `/api/licenses/validate/force`, `/api/licenses/heartbeat` |
| `SessionWriter` | session.json schemaVersion=1 고정, `--out` 우선, 없으면 표준 경로 |

### 에러/상태 매핑 (실무용 고정 규칙)

| 상황 | Exit Code | errorCode |
|------|-----------|-----------|
| 401/403 (auth) | 10 | `AUTH_REQUIRED` 또는 `TOKEN_EXPIRED` |
| `CONCURRENT_SESSION_LIMIT_EXCEEDED` / `SESSION_DEACTIVATED` / `LICENSE_*` | 20 | 해당 코드 그대로 |
| 네트워크 예외 (DNS/timeout) | 30 | `NETWORK_UNAVAILABLE` |
| 5xx | 40 | `SERVER_ERROR` |
| 로컬 IO/권한/파싱 | 50 | `CLIENT_ERROR` |

---

## 15. 구현 순서 (권장)

다음 순서로 구현하면 막히는 지점이 적음:

1. **Bulc.Common** (출력/exit/DPAPI store/path)
2. **bulc-auth status/login/logout** (loopback+PKCE까지)
3. **bulc-lic list/device-id**
4. **bulc-lic validate** (동시세션 초과 JSON까지)
5. **bulc-lic validate-force**
6. **bulc-lic heartbeat**
7. **Java 앱용 thin wrapper** (프로세스 실행+JSON 파싱) — 별도 모듈로 추가

---

## 변경 이력

| 버전 | 날짜 | 변경 내용 |
|------|------|----------|
| v0.1 | 2025-12-29 | 초기 스펙 작성 |
