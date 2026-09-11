# entitlement 어휘 정본 (v1.2.0)

> **Jira**: MDP-789 (Epic MDP-332) · **계약 정본**: `licensing_contract_v1.2.0_draft.md` §2
> **사용자 결정 (2026-09-09)**: 정본 어휘 = 서버 어휘 · Unity 미매핑 4종 이번 제외 · CLI 는 core-simulation 재사용

## 0. 배경

종전 `license_plan_entitlements.entitlement_key` 는 자유 문자열(VARCHAR 100)이고 서버 검증·제품 스코프가 0이었다 — 오타가 곧 권한 오지급이었다. 또한 서버 어휘(`core-simulation`)와 클라이언트(Unity) 어휘(`core-sim`)가 **문자 일치 0쌍**이라, 어느 쪽이 정본인지 불명확했다.

## 1. 정본 어휘 = 서버 어휘

서버가 토큰의 `ent` 클레임에 발행하는 값이 정본이다. 클라이언트(Unity 등)는 자기 쪽 표기로의 매핑을 **클라이언트에서** 유지한다 (예: Unity `EntitlementCapabilityMap` 의 `core-simulation → core-sim`). 서버는 서버 어휘만 발행하며, 배포된 토큰·시드를 깨지 않는 additive 방향이다.

## 2. 제품 스코프 정본 키 (현행)

| 제품 코드 | 제품 | 정본 entitlement 키 |
|---|---|---|
| `001` | BUL:C | `core-simulation`, `export-csv`, `advanced-visualization` |

- CLI(FDS_GPU, KETI 납품)는 화재 시뮬이므로 **`core-simulation` 재사용** — 전용 키 없음.
- 정본 위치 = `application.yml` 의 `bulc.licensing.entitlements.by-product`. 레지스트리 구현 = `EntitlementRegistry`.

## 3. 검증 규칙

- 플랜 생성/수정(`LicensePlanAdminService`) 시, 각 entitlement 키가 플랜 제품 스코프의 정본 키인지 검증한다. 미등록 키는 `INVALID_ENTITLEMENT_KEY`(400)로 거부.
- 레지스트리가 비어 있으면(`by-product` 미설정) 검증을 생략한다 — 개발 편의. **운영은 반드시 설정**한다.

## 4. 이번 범위에서 제외된 어휘 (후속 결정)

| 어휘 | 출처 | 상태 |
|---|---|---|
| `report-auto` · `evacuation` · `paid-lib-fire` · `paid-lib-smoke` | Unity 클라이언트 | **미등록 (잠금 유지)**. 서버 대응 어휘 미정 — 어느 기능이 유료/무료인지 제품 결정 확정 후 후속 이슈로 레지스트리에 등록하면 잠금 해제 경로가 열린다. |
| `export-csv` · `advanced-visualization` | 서버 시드/예시 | 레지스트리에는 등록(정본)하나, Unity `EntitlementCapabilityMap` 은 현재 미매핑(무시). 클라이언트 매핑 추가는 클라이언트 작업. |

## 5. 회귀 검증

- `EntitlementRegistryTest`: 정본 키 허용 / 클라이언트 어휘(core-sim)·오타 거부 / 제품 스코프 격리 / 미구성 시 검증 생략.
- `LicensePlanAdminServiceEntitlementTest`: 정본만 포함 시 생성 성공 / 미등록 키 INVALID_ENTITLEMENT_KEY / 미구성 시 생략 / 빈 목록 허용.
