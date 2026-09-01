# BULC 라이선스 계약 v1.2.0 (개정 초안)

> **상태**: 초안 — 서버팀 검토 대기
> **현행**: v1.1.1 → **제안**: v1.2.0
> **작성**: 2026-09-01
> **정본 위치**: 본 문서 (`BulCHomepage/Document/01_제품_설계/`). api·domain 축 정본 = B 사본 규약(`TEMP-DEC-MDP-754-001` 옵션 A) 승계.
> **클라이언트 적용 가이드**: `bulc-licensing-sdk/specs/client-migration-v1.2.0.md`
> **Jira**: MDP-787 ~ MDP-799 (Epic MDP-332 / MDP-799 만 MDP-371)

기기 정체성을 `deviceFingerprint` 에서 서버 발급 `activationId` 로 전환하고, 그 마이그레이션 창에 키 배포·세션 계약·스키마 결손을 한 번에 정리한다.

근거는 전부 2026-09-01 리포 실측이다 — BulCHomepage `59169dc` · FDS_GPU_RUST_AmgX2 `v0.70.0 / b14e8f2` · bulc-licensing-sdk `adc164a`.

---

## §0. 왜 지금인가

계약 버전업은 자주 열리지 않는 창이다. 아래 세 가지가 그 창에서만 무손상으로 가능하다.

1. **배포본을 깨지 않고 클레임 체계를 바꿀 수 있는 마지막 시점.** 현장에 나간 두 클라이언트가 `dfp` 를 *선택적으로만* 검사한다 — Rust `if let Some(token_dfp) … if !file_dfp.is_empty()`, Electron `if (dfp && dfp !== …)`. 필드 규모가 커지기 전에 전환해야 한다.
2. **경로·에러코드 변경은 버전 경계에서만 가능하다.** `DELETE …/activations/{deviceFingerprint}` 의 경로 파라미터 교체, `SESSION_DEACTIVATED` 정본화가 여기에 해당한다.
3. **스키마 폭 확장은 마이그레이션 창이 아니면 영구 고착된다.** `products.code` 가 `VARCHAR(3)` 이고 외래키가 3곳에 걸려 있다.

> ⚠️ **이 drift 는 이미 사고를 냈다.** Unity 클라이언트(MDP-754)는 스펙 문서의 `aud: "BULC_EVAC"` 예시를 따라 `"bulc-meteor-pro"` 를 기대하도록 구현되었으나, 서버 실제값은 `products.code` = `"001"` 이다. 게다가 내장 공개키를 SDK 리포의 스텁 — *서명 검증 없이 `true` 를 반환하는 코드* 옆의 상수 — 에서 가져왔다. 실 서버 왕복 단언이 없어 GRADUATE 까지 검출되지 않았다. → MDP-799

---

## §1. 정체성 전환 — activationId 주 · fingerprint 보조

신규 개념 도입이 아니다. `Activation.id` 는 이미 UUID 기본키이며 `GlobalSessionInfo.activationId` 로 이미 와이어에 노출되고, takeover 의 `deactivateActivationIds` 도 이미 이 값을 쓴다. 있는 식별자를 정체성으로 승격하는 것이다.

### 현행의 문제

좌석 동일성 판정이 **fingerprint 문자열 완전 일치**에 걸려 있다.

```java
// License.java:314-343 — addActivation
Activation existing = activations.stream()
        .filter(a -> a.getDeviceFingerprint().equals(deviceFingerprint))
        .findFirst().orElse(null);
if (existing != null) { existing.reactivate(...); return existing; }
```

한 글자만 달라도 좌석이 2개 잡힌다. fingerprint 산식은 `SHA-256(primaryMac + ":" + hostname)` 이고 `primaryMac` 은 *Node 의 네트워크 인터페이스 열거 순서에서 첫 번째*에 의존한다. GUI(Node)와 CLI(Rust)가 같은 값을 내려면 열거 순서·MAC 표기·hostname 취득 API가 모두 일치해야 하는데, 가상 어댑터(Hyper-V·WSL·VPN·Docker)가 많은 PC 에서 이 전제가 깨진다.

참조: `device-fingerprint.ts` · `License.java:314-343` · `LicenseService.java:1114-1117` (`hasSelfActiveSession` 도 같은 취약점)

### 전환 설계

| 축 | v1.1.1 | v1.2.0 |
|---|---|---|
| 주 식별자 | `deviceFingerprint` | `activationId` (UUID) |
| 최초 기동 | `deviceFingerprint` | 동일 — **부트스트랩 키로만** 유지 |
| 기기 바인딩 | fingerprint 일치 | DPAPI 저장 + fingerprint **보조 신호** |
| 자기 세션 식별 | 마스킹 규칙 복제(≈32bit) | `activationId` 직접 비교 |

### fingerprint 를 남기는 이유 — 실패 모드 3종

| 실패 모드 | activationId 단독 | 하이브리드 완화 |
|---|---|---|
| **저장 소실** (파일 삭제·프로필 초기화) | 새 activation 생성 → **좌석 누수** (stale 처리까지 점유) | fingerprint 로 조회 → 기존 `addActivation` 재사용 로직이 **자동 재바인딩** |
| **파일 복제** (타 PC 반출) | 좌석 도용 | DPAPI(CurrentUser) 저장 + 발급 시 fingerprint 기록 → heartbeat 불일치 시 재바인딩 요구 |
| **최초 기동** | 식별자 없음 | fingerprint 1회 사용 |

> ⚠️ **전제 — 서버가 자기 activationId 를 알려주지 않는다.** `ValidationResponse` 성공 응답 17개 필드에 `activationId` 가 없다. `activeSessions[]` 안, 즉 409 경로에만 존재한다. **성공 응답 최상위 + 토큰 `act` 클레임**에 실어야 이 전환이 성립한다. → MDP-787

---

## §2. 토큰 계약

### 클레임 표

| 위치 | 클레임 | v1.1.1 | v1.2.0 | 비고 |
|---|---|---|---|---|
| header | `alg` | RS256 | 불변 | 클라이언트는 `alg != RS256` 이면 서명 검증 이전 즉시 실패 |
| header | `kid` | 발행 중 | **문서화** | 서버는 이미 발행하나 스펙 3종에 언급 0건 |
| payload | `iss` | `bulc-license-server` | 불변 | |
| payload | `aud` | `products.code` | 불변 | **문서 정정** — 예시를 `"BULC_EVAC"` → `"001"` |
| payload | `sub` | licenseId | 불변 | |
| payload | `act` | — | **신규** | activationId (UUID) |
| payload | `dfp` | 필수 취급 | **deprecated** | v1.2 유지 · 제거는 v1.3 |
| payload | `ent` | 자유 문자열 | **어휘 레지스트리** | 서버 검증 추가 |
| payload | `iat` / `exp` | epoch s | 불변 | skew ±120초 |

### 키 배포 — 2단 설계

엔진은 오프라인 검증을 하므로 공개키가 바이너리에 박힌다. 현재 배포된 `fds_gpu.exe` 에는 RSA modulus 가 **상수 배열로 하드코딩**되어 있어, 키 교체 시 전량 재배포가 강제된다. 소비자가 4종(Rust · Electron · Unity · Java SDK)으로 늘어나므로 지금 해소한다.

| 단계 | 수단 | 효과 |
|---|---|---|
| **1단** (v1.2) | 클라이언트가 **키 배열**(`kid → key`)을 내장하고 토큰 `header.kid` 로 선택 | 양 키 병존 배포 → 서버 전환 → 구키 제거. 무중단 회전 1회 가능 |
| **2단** (후속) | `GET /.well-known/jwks.json` — 응답을 **현행 키로 서명한 JWS** 로 제공 | 재배포 없는 회전. TLS 단독 신뢰를 피하고 내장 키를 루트 앵커로 유지 |

> **설계 의도**: JWKS 를 그냥 신뢰하면 신뢰 근거가 TLS 하나로 축소된다. 응답 자체를 *이미 신뢰하는 키*로 서명해야 오프라인 검증 모델과 정합한다. 1단만으로도 당면 회전 요구는 충족되므로 2단은 후속으로 분리 가능하다.

### 선행 작업 — 현재 `kid` 는 정보를 담고 있지 않다

```java
// DefaultSigningKeyProvider.java:142-144
public String keyId() {
    return PROD_KEY_ID;      // "bulc-prod-v1" — 실제 로드된 키와 무관한 상수
}
```

어떤 키를 로드했든 `kid` 는 항상 같은 문자열이다. javadoc 은 "prod와 test 키를 구분"한다고 적었으나 이 구현에서는 구분되지 않는다. **1단 설계가 이 상태로는 성립하지 않는다** — 모든 토큰의 `kid` 가 동일하면 클라이언트가 키를 선택할 수 없다. 서버가 실제 키에서 `kid` 를 도출하도록(JWK thumbprint(RFC 7638) 또는 키별 설정 id) 바꾸는 것이 선행이다.

### 운영 키의 정체는 아직 측정되지 않았다

운영 개인키는 `LIC_PRIVATE_KEY_BASE64` / `LIC_PRIVATE_KEY_PATH` 로 주입되며 **어느 리포에도 없다**. prod 프로필은 키 부재 시 부팅이 실패하므로(`handleMissingKey` → `IllegalStateException`) 배포 성공은 "키가 설정돼 있다"만 말해줄 뿐 그 값을 말해주지 않는다.

유통 중인 값은 **3종**이고 그중 어느 것도 운영 대조로 검증된 적이 없다. `c3 d2 ef 16`(Rust·Electron 공통)이 운영 키라는 정황은 강하나 — 두 클라이언트 모두 서명 불일치 시 경성 실패하고 출하 중이다 — 정황이지 측정이 아니다.

**검증 공백**: CI 의 게이트 스텝은 *음성* 경로만 본다(라이선스 없음 → `exit 3`). 운영 토큰이 내장 키로 통과하는지 보는 `real_token_signature_e2e` 는 `#[ignore]` 라 자동 실행된 적이 없다. "CI green" 도 "배포 성공" 도 키 정합에 대해 아무 말을 하지 않는다.

**확정 방법**: `cd GPU_code && BULC_TEST_TOKEN=<운영 JWT> cargo test real_token_signature_e2e -- --ignored --nocapture` — 하네스는 이미 있다. 다만 그 하네스가 FDS_GPU 리포에 있어 다른 클라이언트가 재사용할 수 없다. 공통 적합성 검사로의 이관은 별도 이슈로 등재한다.

→ MDP-788 (키 배포·`kid` 도출) · MDP-801 (검증 하네스 이관 — 등재만)

---

## §3. 세션 · 좌석 계약

| ID | 변경 | 현행 근거 |
|---|---|---|
| **B1** | **경로 변경** `DELETE /licenses/{licenseId}/activations/{deviceFingerprint}` → `{activationId}` · D-3 자가 반납 실배선 동반 | `LicenseController.java:146` |
| **B2** | 서버 self 판정을 activationId 비교로 교체 · 클라이언트 `isSelf` 요구 소멸 | `LicenseService.java:1114-1117` |
| **B3** | **필드 채움** 409 응답의 `licenseId` · `maxConcurrentSessions` 가 `null` | `ValidationResponse.java:145-151` |
| **B4** | **코드 정규화** `SESSION_DEACTIVATED` 정본화 · `ACTIVATION_DEACTIVATED` 는 하위호환 수용 | 서버 `LicenseService:1019` ↔ 문서 `licensing_api.md:460` |
| **B5** | **신규** `clientKind` (`gui` \| `cli`) 요청 필드 + activation 컬럼 | 좌석 공유 시 어느 쪽이 실행 중인지 관측 불가해짐 |

→ MDP-790

### 좌석 규약 — 공동 1 Seat

GUI 와 CLI 는 **같은 `activationId` 를 공유**하여 한 좌석을 쓴다. 이는 서버 로직 변경 없이 성립한다 — 동일 activation 이면 `addActivation` 이 재사용하기 때문이다. 공유의 실제 메커니즘은 §5 의 저장소 규약이다.

| 한도 | 의미 | 시드값 |
|---|---|---|
| `maxActivations` | 등록 가능한 **기기 수** | 3 |
| `maxConcurrentSessions` | **동시 활성 세션 수** — stale 30분 기준으로 계수 | 1~2 |

### CLI 만료 정책 — Unity 와 의도적으로 다름

> **버그 아님 · 계약에 명시할 것**
>
> CLI 는 실행 중 10분 주기 heartbeat 를 유지하되, **403 수신 시 현재 해석을 완주**하고 다음 기동에서 차단한다(경고 1회 출력 후 heartbeat 중단). FDS 해석이 수 시간~수일 실행되어 중단 시 손실이 크기 때문이다.
>
> 따라서 CLI 에서 heartbeat 의 역할은 강제 종료가 아니라 ① 실행 중 좌석 점유 반영 ② offlineToken 자동 갱신 ③ 만료 경고다. Unity 클라이언트의 `INV-LICENSE-003`(종단 시 즉시 종료 요청)과 다른 정책이므로 병기한다.

→ MDP-797

---

## §4. 스키마 마이그레이션

| 대상 | 변경 | 주의 |
|---|---|---|
| `products.code` | `VARCHAR(3)` → `VARCHAR(32)` | **기존 `'001'` 값은 불변.** 컬럼 폭만 확장한다 — 배포된 Rust 의 `EXPECTED_AUDIENCE = "001"` 하드코딩이 깨지지 않는다. 신규 제품만 문자열 코드 사용 |
| **FK 3곳** | `price_plans.product_code` · `promotions.product_code` · `subscriptions.product_code` 동시 확장 | 가장 침습적인 항목. 단일 트랜잭션 마이그레이션 + 롤백 스크립트 필수 |
| `license_plans` | **컬럼** `grace_period_features` | 현재 스펙 문서에만 존재하고 서버 컬럼이 없어, 유예 정책이 클라이언트 가정으로만 살아 있다 |
| `license_activations` | **컬럼** `client_kind` | B5 |
| `ValidateRequest` | **검증** `deviceDisplayName` `@Size(100)` | DB 컬럼은 100 인데 DTO 검증이 없어 서버가 방어하지 못한다 |
| `AuthorizationCodeStore` | 인메모리 `ConcurrentHashMap` → **Redis** | 재기동·다중 인스턴스에서 로그인 중 code 유실. 코드 주석 자체가 "운영 환경에서는 Redis 권장" |

참조: `database/init.sql:266` (products.code) · `:287` (price_plans) · `:323` (promotions) · `Activation.java:56-64` · `AuthorizationCodeStore.java:28-33`

→ MDP-791 · MDP-793

---

## §5. 클라이언트 저장소 규약

좌석 공유의 실제 메커니즘. "두 구현이 같은 해시를 낸다"는 깨지기 쉬운 전제를 "같은 파일을 읽는다"로 대체한다.

```
%APPDATA%\BULC\device-activation.json        (DPAPI CurrentUser)
{
  "v": 1,
  "activationId": "<uuid>",
  "licenseId":    "<uuid>",
  "dfp":          "<sha256 hex>",     // 보조 바인딩 · 재바인딩 키
  "boundAt":      "2026-09-01T00:00:00Z"
}
```

- **공유 주체** — BULC-AI(Electron) 와 `fds_gpu.exe` 가 같은 파일을 읽고 쓴다.
- **동시 쓰기** — 임시 파일 기록 후 원자적 교체(`MoveFileEx` / `rename`) + 짧은 재시도. 부분 기록 상태를 만들지 않는다.
- **기존 파일 존치** — `%APPDATA%\BULC\engine-license.bin`(offlineToken, DPAPI)은 그대로 유지한다. 엔진의 오프라인 검증 경로는 무변경이다.
- **소실 복구** — 파일이 없으면 fingerprint 로 부트스트랩 validate → 서버가 기존 activation 을 재사용 → 새 `activationId` 를 받아 재기록.

→ MDP-795

---

## §6. 클라이언트 호환 매트릭스

| 클라이언트 | 배포 상태 | `dfp` 취급 | v1.2 영향 | 조치 |
|---|---|---|---|---|
| Rust `fds_gpu` v0.70.0 | **현장 배포됨** | optional | **무손상** — `act` 무시 | 없음 — 다음 릴리즈에서 `act` 채택 |
| Electron BULC-AI | **배포됨** | optional | **무손상** | 없음 |
| Unity BULC | 미배포 | **필수** (`INV-LICENSE-002` ③) | 규칙 ③ 개정 필요 | 키 · `aud` 교정과 동시 처리 (MDP-799) |
| Java SDK | stale | — | 영향 없음 | 갱신 책임 소재 미정 |

---

## §7. 롤아웃 순서

| 단계 | 내용 |
|---|---|
| **S1** | **서버 v1.2.0 배포 (additive 만)** — `act` 클레임 · `activationId` 응답 필드 · `clientKind` · 409 필드 채움 · `kid` 키 배열. 이 시점에 배포된 클라이언트는 전부 무손상으로 계속 동작한다 |
| **S2** | **스키마 마이그레이션** — `products.code` 폭 확장(값 불변) · `grace_period_features` · `client_kind` · Redis code store 전환 |
| **S3** | **계약 문서 개정 + 사본 단일화** — `aud` 예시 정정 · `kid` 문서화 · 에러코드 정본화 · 위임검증/CLI 로그인 패턴 정본 신설 · CLI 만료 정책 병기 |
| **S4** | **파괴적 변경 릴리즈** — `DELETE` 경로 activationId 화. 이 경로는 현재 클라이언트가 호출하지 않으므로(D-3 미배선) 실질 파괴 없음 |
| **S5** | **클라이언트 순차 대응** — 엔진(크레이트 분리 → CLI 로그인 → heartbeat) → Unity 정합 → EVAC 게이트 |

---

## §8. 작업 분해

| Jira | Type | Summary | 리포 | 인일 |
|---|---|---|---|---|
| MDP-787 | 스토리 | 계약 v1.2.0 정체성 전환 — `act` 클레임 · `activationId` 성공응답 · self 판정 | BulCHomepage | 2~3 |
| MDP-788 | 스토리 | 키 배포 축 — `kid` 다중키 선택 + 회전 절차 (+JWKS 후속) | BulCHomepage | 3~5 |
| MDP-789 | 스토리 | entitlement 어휘 레지스트리 + 서버 검증 | BulCHomepage | 2~3 |
| MDP-790 | 스토리 | 세션 계약 정합 — DELETE 경로 · 409 필드 · 에러코드 · `clientKind` | BulCHomepage | 2~3 |
| MDP-791 | 작업 | 스키마 마이그레이션 — `products.code` 폭 · `grace_period_features` · `@Size` | BulCHomepage | 2~4 |
| MDP-792 | 작업 | CLI 전용 `client_id` 등록 + `bulc-desktop` 불일치 정리 | BulCHomepage | 0.5~1 |
| MDP-793 | 작업 | `AuthorizationCodeStore` Redis 이관 | BulCHomepage | 2~3 |
| MDP-794 | 작업 | 계약 v1.2.0 스펙 개정 + 사본 단일화 + `aud` 예시 정정 | sdk · Homepage | 3~5 |
| MDP-795 | 작업 | `bulc-license` 크레이트 분리 + `device-activation` 공유 저장소 | FDS_GPU | 3~5 |
| MDP-796 | 스토리 | CLI OAuth PKCE 로그인 — WinHTTP · 루프백 · RTR · 토큰 저장 | FDS_GPU | 7~11 |
| MDP-797 | 스토리 | CLI heartbeat 수명주기 + 만료 정책 + `license` 서브커맨드 | FDS_GPU | 3~5 |
| MDP-798 | 스토리 | EVAC 엔진 라이선스 게이트 배선 | FDS_GPU | 1~2 |
| MDP-799 | 버그 | Unity 라이선스 클라이언트 실서버 정합 — 운영 공개키 · `aud` · `act` | UnityFDSEditor | 1~2 |
| MDP-801 | 작업 | 키 정합 검증 하네스 SDK 이관 — 양성 경로 회귀 + 공통 적합성 검사 **(등재만 · 미착수)** | sdk | — |

합계 **33~52 인일** (MDP-801 제외). KETI CLI 납품 최단 경로는 MDP-792 · 794(부분) · 795 · 796 · 797 로 약 **15~25 인일**이며, MDP-788(키 배포)은 CLI 배포 *이전*에 끝나야 한다 — 배포 후에는 공개키가 박힌 바이너리가 현장에 풀린다.

---

## §9. 비범위

| 항목 | 사유 |
|---|---|
| Device Authorization Grant (RFC 8628) | KETI 실행 환경에 브라우저가 있다는 전제로 제외 확정. 헤드리스 요구가 실증되면 재개봉 |
| push 채널 (WebSocket / SSE) | Q-14 장기 항목. v1.2 에 섞으면 범위가 폭발한다 |
| `dfp` 클레임 제거 | v1.3 — 현장 배포본이 소진된 뒤 |
| `products.code` *값* 변경 | 배포된 엔진의 `EXPECTED_AUDIENCE = "001"` 하드코딩을 깬다. 폭만 확장한다 |
