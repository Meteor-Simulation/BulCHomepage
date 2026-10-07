-- 소액 결제 점검용 내부 전용 요금제 2건.
--
-- 실결제 전환 후 카드(구독형)와 가상계좌(무통장입금) 흐름을 운영에서 확인해야 하는데 실제
-- 상품가가 600만원이라 테스트할 수 없다. 매니저 이상에게만 보이는 소액 요금제를 둔다.
--
-- ## license_plan_id 를 NULL 로 두는 것이 핵심이다
--
-- PaymentService 의 중복 구매 차단(requirePurchasable)과 라이선스 발급은 둘 다
-- `if (pricePlan.getLicensePlanId() != null)` 안에 있다. 그래서 NULL 이면
--   - 이미 라이선스를 보유한 계정도 몇 번이든 결제할 수 있다 (테스트 반복 가능)
--   - 결제가 끝나도 라이선스가 발급되지 않아 실제 라이선스 목록이 오염되지 않는다
-- 기존 라이선스를 일시 정지시킬 필요가 없다. 선례도 있다 — price_plans id 5 가 이미 NULL 이다.
--
-- 반대급부: "결제 → 라이선스 발급" 연결은 이 요금제로 검증되지 않는다. 결제 수단 자체를
-- 점검하는 것이 목적이므로 의도된 선택이다.
--
-- ## 금액
--
-- 토스 최소 결제금액은 카드 100원, 계좌이체 200원, 가상계좌는 문서 간 불일치(1원 또는 200원).
-- 카드용은 100원으로 두고, 가상계좌용은 하한 논란을 피해 1,000원으로 둔다. 가상계좌 수수료는
-- 건당 400원 정액이라 금액을 낮춰도 비용이 줄지 않는다 — 낮출 이유가 없다.
--
-- 결제 수단은 결제 시점에 화면에서 고르는 값이라 요금제가 수단을 강제하지는 않는다.
-- 이름을 나눠 둔 것은 결제 기록에서 어느 흐름으로 들어온 건인지 구분하기 위해서다.

INSERT INTO price_plans (product_code, name, description, price, currency, license_plan_id, is_active, is_internal)
SELECT '001', '[내부] 결제 점검 100원', '카드·구독형 점검용 (매니저 이상 전용)', 100.00, 'KRW', NULL, TRUE, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM price_plans WHERE name = '[내부] 결제 점검 100원'
);

INSERT INTO price_plans (product_code, name, description, price, currency, license_plan_id, is_active, is_internal)
SELECT '001', '[내부] 무통장입금 점검 1000원', '가상계좌 입금확인 점검용 (매니저 이상 전용)', 1000.00, 'KRW', NULL, TRUE, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM price_plans WHERE name = '[내부] 무통장입금 점검 1000원'
);
