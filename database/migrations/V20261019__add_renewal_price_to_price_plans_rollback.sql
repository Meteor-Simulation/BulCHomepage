-- V20261019__add_renewal_price_to_price_plans.sql 되돌리기.
--
-- 주의: 이 컬럼을 지우면 하드웨어 포함 요금제의 갱신이 첫 결제가(990만원)로 청구된다.
-- 해당 요금제를 비활성화한 뒤에만 실행할 것.

ALTER TABLE price_plans
    DROP COLUMN IF EXISTS renewal_price;
