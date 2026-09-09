# 라이선스 서명 키 배포·회전 절차 (v1.2.0)

> **Jira**: MDP-788 (Epic MDP-332) · **계약 정본**: `licensing_contract_v1.2.0_draft.md` §2 (키 배포 확정 3항, 2026-09-02 사용자 승인)
> **원 설계 근거**: `bulc_auth_module_spec_v0.4.md` §8.1 — 공개키 내장(고정) 명시, 키 회전은 「추후 multi-key 지원」으로 의도적 이연. 본 문서의 ㉯ 는 그 이연분의 이행이다.

## 0. 전제 — 검증 키는 클라이언트에 내장한다

엔진(fds_gpu)·Electron·Unity 는 오프라인 토큰 검증을 하므로 RSA 공개키가 바이너리에 내장된다.
**런타임 키 로드는 금지** — 공격자가 키를 바꿔치기해 라이선스를 우회할 수 있다(Keygen 명문 금지 사유).
JWKS 런타임 fetch 는 OIDC 서버측 RP 관행이며, 사용자 통제 머신의 오프라인 검증기에는 해당하지 않는다.

## 1. kid — 실키 도출 (RFC 7638 JWK Thumbprint)

- 서버(`DefaultSigningKeyProvider`)는 **실제 로드된 개인키에서 유도한 공개키의 RFC 7638 SHA-256 thumbprint** 를 `kid` 로 발행한다 (base64url 43자, 패딩 없음).
- 종전의 상수 `"bulc-prod-v1"` 은 폐기 — 어떤 키를 로드했든 같은 값이라 다중키 선택이 성립하지 않았다.
- thumbprint 는 키 자체에서 결정되므로 **클라이언트·CI 가 공개키만으로 동일한 kid 를 독립 산출**할 수 있다 (서버와 조율 불요).
- 산출식: `base64url( SHA-256( {"e":"<e>","kty":"RSA","n":"<n>"} ) )` — n·e 는 RFC 7518 Base64urlUInt (부호 바이트 없는 최소 옥텟). 구현·검증 = `JwkThumbprint.java` (RFC 7638 §3.1 공식 벡터로 테스트).
- 테스트 키는 `test-` 프리픽스 kid 를 유지한다 (prod 혼입 검출 축 — 기존 정책 불변).

## 2. 진단·CI 대조용 공개키 엔드포인트 (㉮)

```
GET /api/v1/licensing/public-key        (인증 불요 — 공개키는 비밀이 아님)

200: { "kid", "alg": "RS256", "kty": "RSA", "use": "sig",
       "n": <Base64urlUInt>, "e": <Base64urlUInt>, "pem": <SPKI PEM> }
503: { "error": "KEY_NOT_CONFIGURED", ... }   (서명 키 미설정 기동)
```

- **용도 한정**: CI 키 정합 대조(클라이언트 내장 상수 ↔ 운영 공개키, MDP-801) + 운영 공개키 식별. **클라이언트 토큰 검증의 신뢰 경로가 아니다** (§0).
- `n` 은 Rust 내장 modulus 상수와, `pem` 은 openssl 등 표준 도구와 직접 대조 가능.
- `licensing_api.md` Appendix B 의 `LICENSE_RSA_PUBLIC_KEY` "클라이언트 배포용" 슬롯 의도의 실현이다.

### 운영 공개키 1회 식별 (미결 해소 절차)

유통 중인 공개키 3종 중 어느 것이 운영 키인지 아직 측정된 바 없다 (계약 §2). 본 엔드포인트 배포 후:

```bash
curl -s https://api.msimul.com/api/v1/licensing/public-key | jq -r '.n'
# → Rust c3d2ef16… / Unity c84ecc44… 내장 상수와 대조 → 운영 키 확정 → MDP-799/801 에 기록
```

## 3. 클라이언트 내장 multi-key (㉯)

클라이언트는 단일 키 상수 대신 **`kid → 공개키` 배열**을 내장하고, 토큰 `header.kid` 로 검증 키를 선택한다.

- `header.kid` 가 배열에 없으면 → 서명 검증 실패와 동일 취급 (경성 실패).
- `kid` 없는 토큰(구버전 서버) → 배열의 기본(최구) 키로 폴백 — 마이그레이션 창 한정.
- 적용 대상 4종: Rust `fds_gpu` (MDP-795~797 창구) · Electron BULC-AI · Unity BULC (MDP-799) · Java SDK.

## 4. 무중단 회전 runbook (1회 회전)

| 단계 | 행위 | 검증 |
|---|---|---|
| R1 | 신규 키 쌍 생성 (openssl, PKCS#8) — 개인키는 배포 시크릿 저장소에만 | 신규 kid 사전 산출 (RFC 7638, 공개키만으로 가능) |
| R2 | **클라이언트 릴리즈**: 내장 배열에 신규 공개키 추가 (구키 병존) | CI: 배열 내 전 키 ↔ ㉮ 엔드포인트 대조 |
| R3 | 현장 배포본이 R2 릴리즈로 충분히 소진될 때까지 대기 | — |
| R4 | **서버 전환**: `LIC_PRIVATE_KEY_*` 를 신규 키로 교체 후 재기동 | ㉮ 엔드포인트 `kid` 가 신규 값인지 확인 · 발행 토큰 `header.kid` 변경 확인 |
| R5 | 다음 클라이언트 릴리즈에서 구키 제거 | — |

- **롤백**: R4 직후 문제 시 서버 키를 구키로 되돌리면 즉시 복구 (R2 클라이언트는 양 키 모두 보유).
- R4 이전에 배포된 구(단일키) 클라이언트는 서버 전환 시점부터 검증 실패 — **R3 대기가 실질 게이트**다.
- 2회 이상의 잦은 회전 실수요가 실증되면 KSK-서명 JWKS 런타임 배포(㉰, 계약 §2 이연)를 재개봉한다.

## 5. 서버 키 설정 (기존 — 참조)

| 환경변수 | 형식 | 우선순위 |
|---|---|---|
| `LIC_PRIVATE_KEY_BASE64` | base64(PKCS#8 PEM 텍스트) | 1 |
| `LIC_PRIVATE_KEY_PATH` | PEM 파일 경로 | 2 |

prod 프로필은 키 부재 시 부팅 실패(fail-fast). 서버는 개인키에서 공개키·kid 를 유도하며 별도 공개키 설정 슬롯은 없다.
