-- =========================================================
-- MDP-536: 라이선스 플랜 entitlement_key '3d-visualization' → 'evac' rename
-- 회의록 2026-05-26 D-02: 400/600만원 옵션 의미를 'Evac 제외/포함'으로 통일
-- 생성: 2026-06-01
-- =========================================================

-- entitlement_key rename (BUL:C 3D Premium → BUL:C + Evac 의미)
UPDATE license_plan_entitlements
SET entitlement_key = 'evac'
WHERE entitlement_key = '3d-visualization';

-- 검증: 600만원 (b2c3d4e5-...) 플랜이 core-simulation + evac 만 갖도록
-- SELECT plan_id, entitlement_key FROM license_plan_entitlements
-- WHERE plan_id = 'b2c3d4e5-f6a7-4b5c-9d0e-1f2a3b4c5d6e';
