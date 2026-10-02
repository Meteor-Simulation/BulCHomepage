-- MDP-748 / MDP-749: 결제에 적용된 쿠폰을 기록한다.
--
-- 지금까지 쿠폰은 프론트에서만 계산되고 결제 기록에 남지 않았다. 그래서
--   1) 어떤 결제에 어떤 쿠폰이 쓰였는지 추적할 수 없고 (환불 금액 산정·감사 불가)
--   2) 쿠폰 사용 횟수(promotions.usage_count)가 증가하지 않아 usage_limit 이 무의미했다
--
-- 금액은 정가(price_plans.price)가 아니라 실제 청구액이 payments.amount 에 들어가므로,
-- 할인액을 따로 남겨야 "정가 얼마에서 얼마를 깎았는지" 를 사후에 재구성할 수 있다.
--
-- 쿠폰 코드를 FK 가 아니라 코드 문자열로 박아 두는 이유: 프로모션이 나중에 삭제·수정되어도
-- 결제 시점의 사실이 남아야 한다. 결제 기록은 회계 자료이므로 참조가 끊겨선 안 된다.

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS promotion_code  VARCHAR(50),
    ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(18, 2);

COMMENT ON COLUMN payments.promotion_code  IS '적용된 쿠폰 코드 (promotions.code 의 결제 시점 스냅샷, 미적용 시 NULL)';
COMMENT ON COLUMN payments.discount_amount IS '서버가 산정한 할인액. amount = 정가 - discount_amount (미적용 시 NULL)';

-- 쿠폰별 사용 내역 조회용 (관리자 화면에서 "이 쿠폰으로 결제된 건" 집계)
CREATE INDEX IF NOT EXISTS idx_payments_promotion_code
    ON payments (promotion_code)
    WHERE promotion_code IS NOT NULL;
