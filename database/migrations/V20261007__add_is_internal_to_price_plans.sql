-- 내부 전용 요금제 플래그 (MDP-949).
--
-- 실결제 전환 후 카드(구독형)·가상계좌(무통장입금) 흐름을 운영에서 점검해야 하는데, 실제
-- 상품가는 600만원이라 테스트할 수 없다. 그래서 소액 테스트 요금제를 운영 카탈로그에 두되
-- 일반 고객에게는 보이지 않아야 한다.
--
-- is_active 를 끄는 방법은 쓸 수 없다 — 비활성 요금제는 결제 자체가 안 되므로 테스트가
-- 불가능하다. "살 수는 있지만 아무에게나 보이지는 않는" 상태가 필요해서 축을 하나 더 뒀다.
--   is_active   = 판매 가능 여부
--   is_internal = 노출 대상 (true 면 매니저 이상에게만 보이고 매니저 이상만 결제 가능)
--
-- 목록 숨김만으로는 부족하다. pricePlanId 를 알면 결제 API 를 직접 호출할 수 있으므로
-- 조회(ProductController)와 결제(PaymentService 승인·빌링 두 경로) 모두에서 막는다.
--
-- 기본값 FALSE = 기존 5건은 모두 공개 유지. NOT NULL 로 두어 "모르는 상태"를 없앤다.

ALTER TABLE price_plans
    ADD COLUMN IF NOT EXISTS is_internal BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN price_plans.is_internal IS '내부 전용 요금제 — true 면 매니저 이상(roles_code 000·001)에게만 노출되고 결제도 그 역할만 가능';

-- 일반 고객 목록 조회는 is_internal = false 만 훑는다. 공개 요금제가 대부분이라
-- 부분 인덱스로 둔다.
CREATE INDEX IF NOT EXISTS idx_price_plans_public_lookup
    ON price_plans (product_code, currency, price)
    WHERE is_active = TRUE AND is_internal = FALSE;
