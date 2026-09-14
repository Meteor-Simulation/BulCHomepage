# 라이선스 v1.2.0 스키마 마이그레이션 운영 반영 runbook (MDP-858)

> 대상 = `database/migrations/V20260909__licensing_v1_2_0_schema.sql` (MDP-791).
> 본 문서는 로컬 Postgres 16 왕복 리허설 (MDP-858, 2026-09-14) 의 실측 결과와 운영 적용 절차를 기록한다.
> 검증 상세 로그 = Jira MDP-858 코멘트.

## 1. 리허설 결과 요약 (postgres:16-alpine — 운영과 동일 이미지)

| 항목 | 결과 |
|---|---|
| forward 적용 (실데이터 재현: products '001' + FK 참조행 price_plans/promotions/subscriptions + license_plans) | ✅ 성공, 단일 트랜잭션, **0.2초** |
| 데이터 보존 | ✅ 전량 보존, '001' 값 불변 |
| FK 정합 (미등록 코드 삽입 거부) | ✅ 유지 |
| `grace_period_features` 기존 행 기본값 'full' 채움 | ✅ |
| forward 결과 ≡ 신규 설치 (`init.sql`) 스키마 | ✅ 의미 동치 — 잔차는 신규 컬럼의 물리 순서뿐 (`ADD COLUMN` 은 말미 추가, 신규 설치는 정의 위치. JPA 무관, 무해) |
| rollback 왕복 (pre 상태 복귀) | ✅ pg_dump **완전 일치** (COMMENT 복원 보강 후) |
| rollback 가드 시나리오 (3자 초과 코드 존재 시) | ✅ `value too long for type character varying(3)` 로 실패 + **트랜잭션 원자성 확인** (선행 DROP COLUMN 도 함께 되돌아감 — 부분 적용 없음) |

리허설에서 보강한 것 (본 PR):
- rollback 스크립트에 `products.code` COMMENT 복원 추가 (미복원 시 pre 상태와 dump 잔차).
- `init.sql` 코멘트 동기화 3건 (`products.code` 문구 통일 + 신규 컬럼 2개 COMMENT 누락 보충).

## 2. 운영 적용 절차

### 선행 조건 (둘 다 필수)

1. **MDP-856 (러너 fail-closed) 머지 확인.** main 의 `scripts/db-migrate.sh` 는 `V*.sql` 전부를 적용하므로,
   수정 전 러너로 배포하면 `V20260909__..._rollback.sql` 이 정렬상 원본 «바로 다음» 에 실행된다.
   확인: `scripts/db-migrate.sh` 에 `*_rollback.sql` 건너뜀 분기가 있는지.
2. 서버측 `git pull` 완료 (러너는 서버 체크아웃의 `database/migrations` 를 읽음).

### 적용

```bash
bash scripts/db-migrate.sh --status   # V20260909 가 '미적용' 목록에 있는지 + rollback 파일이 건너뜀인지 확인
bash scripts/db-migrate.sh            # 적용 (deploy.sh [4/9] 가 자동 호출하는 경로와 동일)
```

- 락 특성: `ALTER TYPE` 은 ACCESS EXCLUSIVE 락이지만 VARCHAR 폭 확장은 테이블 리라이트 없음 → 실측 0.2초.
  `ddl-auto: validate` 이므로 백엔드 재시작 «전» 실행 (deploy.sh 순서가 이미 보장).
- 적용 후 검증:
  ```sql
  SELECT character_maximum_length FROM information_schema.columns
   WHERE table_name='products' AND column_name='code';          -- 32
  SELECT count(*) FROM license_plans WHERE grace_period_features IS NULL;  -- 0
  ```

### 롤백 (비상시)

러너는 rollback 파일을 자동 적용하지 않는다 (MDP-856 이후 의도된 동작). **수동 psql 로만** 적용한다:

```sql
-- 1) 가드: 0행이어야 안전 (3자 초과 신규 코드가 있으면 롤백 불가 — 해당 제품 정리 선행)
SELECT code FROM products WHERE length(code) > 3;
```
```bash
# 2) 적용
docker exec -i bulc-db-prod psql -U bulc_prod_user -d bulc_homepage_db -v ON_ERROR_STOP=1 \
  < database/migrations/V20260909__licensing_v1_2_0_schema_rollback.sql
# 3) schema_migrations 원장에서 V20260909 행 삭제 (재적용 가능하게)
```

가드 위반 시 스크립트는 원자적으로 실패한다 (부분 적용 없음 — 리허설 실측).

## 3. 남은 배포 전 결정

- `bulc-cli` `[::1]` IPv6 루프백 redirect 등록 여부 = MDP-796 결정 사항 (스키마와 무관, CLI 로그인 전 결정).
