-- MDP-791: 라이선스 계약 v1.2.0 스키마 마이그레이션
--
-- 계약 v1.2.0 §4. 버전 경계에서만 가능한 스키마 변경 4종을 단일 트랜잭션으로 적용한다.
--
-- ① products.code VARCHAR(3) → VARCHAR(32) + FK 3곳 동시 확장 (가장 침습적)
--    ⚠ 기존 '001' 값 불변 — 폭만 확장. 배포된 fds_gpu.exe 의 EXPECTED_AUDIENCE="001"
--      하드코딩이 깨지지 않는다. 신규 제품만 문자열 코드 사용.
--    FK(price_plans/promotions/subscriptions.product_code)는 products.code 를 참조하므로
--    참조/피참조 컬럼 타입을 함께 넓혀야 정합이 유지된다.
-- ② license_plans.grace_period_features — 유예기간 기능 범위. 종전 스펙 문서에만 있고
--    서버 컬럼이 없어 클라이언트 가정으로만 살아 있었다. v1 기본값 'full'.
-- ③ license_activations.client_kind — gui|cli 구분 (MDP-790 B5). nullable.
-- (④ deviceDisplayName @Size(100) 은 DTO 검증이라 스키마 변경 없음 — 컬럼은 이미 VARCHAR(100))
--
-- 롤백 = V20260909__licensing_v1_2_0_schema_rollback.sql

BEGIN;

-- ① products.code 및 FK 3곳 폭 확장 (값 불변)
ALTER TABLE products        ALTER COLUMN code         TYPE VARCHAR(32);
ALTER TABLE price_plans     ALTER COLUMN product_code TYPE VARCHAR(32);
ALTER TABLE promotions      ALTER COLUMN product_code TYPE VARCHAR(32);
ALTER TABLE subscriptions   ALTER COLUMN product_code TYPE VARCHAR(32);

-- ② license_plans.grace_period_features (기본 'full', 기존 행도 채움)
ALTER TABLE license_plans
    ADD COLUMN IF NOT EXISTS grace_period_features VARCHAR(32) NOT NULL DEFAULT 'full';

-- ③ license_activations.client_kind (gui|cli, nullable)
ALTER TABLE license_activations
    ADD COLUMN IF NOT EXISTS client_kind VARCHAR(8) NULL;

COMMENT ON COLUMN products.code IS '상품 코드 (기존 000~999 고정폭 → v1.2.0 에서 VARCHAR(32), 기존 값 불변), UNIQUE';
COMMENT ON COLUMN license_plans.grace_period_features IS '유예기간(EXPIRED_GRACE) 중 기능 범위 (v1 기본 full)';
COMMENT ON COLUMN license_activations.client_kind IS '활성화 클라이언트 종류 (gui|cli, nullable — 구버전 미전송)';

COMMIT;
