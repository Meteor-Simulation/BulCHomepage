-- =========================================================
-- 마이그레이션: price_plans에 license_plan_id 컬럼 추가
-- 실행일: 2026-02-09
-- 설명: 결제 완료 시 자동 라이선스 발급을 위한 연결
-- =========================================================

-- 1. price_plans 테이블에 license_plan_id 컬럼 추가
ALTER TABLE price_plans
ADD COLUMN IF NOT EXISTS license_plan_id UUID NULL;

COMMENT ON COLUMN price_plans.license_plan_id IS '연결된 라이선스 플랜 ID (결제 완료 시 해당 플랜으로 라이선스 발급)';

-- 2. 샘플 라이선스 플랜 생성 (BUL:C 제품용)
-- product_id는 product code '001'에서 생성한 결정적 UUID
-- Java: UUID.nameUUIDFromBytes("001".getBytes(StandardCharsets.UTF_8))
-- = 3f333a88-4ea0-3fb5-8f3e-7b3c4a8c8b9a (예시, 실제 값은 다를 수 있음)

-- 참고: product_id는 실제 운영 환경에서 products 테이블의 UUID 또는
-- 제품 코드에서 생성한 결정적 UUID를 사용해야 합니다.

-- BUL:C PRO 1년 플랜
INSERT INTO license_plans (id, product_id, code, name, description, license_type, duration_days, grace_days, max_activations, max_concurrent_sessions, allow_offline_days)
SELECT
    'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d'::UUID,
    uuid_generate_v5(uuid_ns_url(), 'product:001'),
    'BULC-PRO-1Y',
    'BUL:C PRO 1년',
    'BUL:C PRO 버전 1년 구독 라이선스',
    'SUBSCRIPTION',
    365,
    7,
    3,
    1,
    7
WHERE NOT EXISTS (SELECT 1 FROM license_plans WHERE code = 'BULC-PRO-1Y');

-- BUL:C 3D Premium 1년 플랜
INSERT INTO license_plans (id, product_id, code, name, description, license_type, duration_days, grace_days, max_activations, max_concurrent_sessions, allow_offline_days)
SELECT
    'b2c3d4e5-f6a7-4b5c-9d0e-1f2a3b4c5d6e'::UUID,
    uuid_generate_v5(uuid_ns_url(), 'product:001'),
    'BULC-3D-PREMIUM-1Y',
    'BUL:C 3D Premium 1년',
    'BUL:C 3D Premium 버전 1년 구독 라이선스',
    'SUBSCRIPTION',
    365,
    7,
    3,
    1,
    7
WHERE NOT EXISTS (SELECT 1 FROM license_plans WHERE code = 'BULC-3D-PREMIUM-1Y');

-- 3. 라이선스 플랜 기능 권한 추가
INSERT INTO license_plan_entitlements (id, plan_id, entitlement_key)
SELECT uuid_generate_v4(), 'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d'::UUID, 'core-simulation'
WHERE NOT EXISTS (SELECT 1 FROM license_plan_entitlements WHERE plan_id = 'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d'::UUID AND entitlement_key = 'core-simulation');

INSERT INTO license_plan_entitlements (id, plan_id, entitlement_key)
SELECT uuid_generate_v4(), 'b2c3d4e5-f6a7-4b5c-9d0e-1f2a3b4c5d6e'::UUID, 'core-simulation'
WHERE NOT EXISTS (SELECT 1 FROM license_plan_entitlements WHERE plan_id = 'b2c3d4e5-f6a7-4b5c-9d0e-1f2a3b4c5d6e'::UUID AND entitlement_key = 'core-simulation');

INSERT INTO license_plan_entitlements (id, plan_id, entitlement_key)
SELECT uuid_generate_v4(), 'b2c3d4e5-f6a7-4b5c-9d0e-1f2a3b4c5d6e'::UUID, '3d-visualization'
WHERE NOT EXISTS (SELECT 1 FROM license_plan_entitlements WHERE plan_id = 'b2c3d4e5-f6a7-4b5c-9d0e-1f2a3b4c5d6e'::UUID AND entitlement_key = '3d-visualization');

-- 4. price_plans와 license_plans 연결
-- BUL:C PRO (KRW, USD) → BULC-PRO-1Y 플랜
UPDATE price_plans
SET license_plan_id = 'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d'::UUID
WHERE name = 'BUL:C PRO' AND license_plan_id IS NULL;

-- BUL:C 3D Premium (KRW, USD) → BULC-3D-PREMIUM-1Y 플랜
UPDATE price_plans
SET license_plan_id = 'b2c3d4e5-f6a7-4b5c-9d0e-1f2a3b4c5d6e'::UUID
WHERE name = 'BUL:C 3D Premium' AND license_plan_id IS NULL;

-- 5. 확인 쿼리
-- SELECT pp.id, pp.name, pp.price, pp.currency, lp.code as license_plan_code, lp.name as license_plan_name
-- FROM price_plans pp
-- LEFT JOIN license_plans lp ON pp.license_plan_id = lp.id;
