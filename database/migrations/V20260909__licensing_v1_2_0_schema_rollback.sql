-- MDP-791 롤백: 라이선스 계약 v1.2.0 스키마 마이그레이션 되돌리기
--
-- ⚠ 주의: products.code 를 VARCHAR(3) 으로 되돌리면, v1.2.0 배포 후 3자 초과
--   문자열 코드를 쓰는 신규 제품이 하나라도 있으면 실패/절단된다. 신규 문자열
--   코드 제품이 없을 때만 안전하다. 운영 롤백 전 반드시 다음으로 확인:
--     SELECT code FROM products WHERE length(code) > 3;   -- 0행이어야 안전
--
-- grace_period_features / client_kind 는 additive 컬럼이라 드롭만 한다.

BEGIN;

ALTER TABLE license_activations DROP COLUMN IF EXISTS client_kind;
ALTER TABLE license_plans       DROP COLUMN IF EXISTS grace_period_features;

-- 폭 축소 (신규 3자 초과 코드가 없을 때만 안전)
ALTER TABLE subscriptions   ALTER COLUMN product_code TYPE VARCHAR(3);
ALTER TABLE promotions      ALTER COLUMN product_code TYPE VARCHAR(3);
ALTER TABLE price_plans     ALTER COLUMN product_code TYPE VARCHAR(3);
ALTER TABLE products        ALTER COLUMN code         TYPE VARCHAR(3);

COMMIT;
