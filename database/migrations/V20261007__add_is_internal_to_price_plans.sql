-- 내부 전용 요금제 플래그 + 소액 결제 점검용 요금제 2건.
--
-- 컬럼 추가와 요금제 삽입을 한 파일에 둔 것은 의도다. 처음에는
-- V20261007__add_is_internal... / V20261007_2__seed_internal... 두 파일로 나눴는데,
-- 러너가 `ls -1 | LC_ALL=C sort` 순서로 적용하므로 `_2__seed` 가 `__add` 보다 **먼저**
-- 정렬됐다(ASCII 에서 '2'=0x32 < '_'=0x5F). 컬럼이 없는 상태로 INSERT 가 돌아
-- "column is_internal does not exist" 로 배포가 멈췄다. `_2` 는 순번이 아니다.
-- 둘을 합치면 이 순서 문제가 사라진다. 되돌리기는 같은 이름의 *_rollback.sql 에 있다.
--
-- ## 왜 is_active 로 해결할 수 없나
--
-- 실결제 전환 후 카드(구독형)·가상계좌(무통장입금) 흐름을 운영에서 점검해야 하는데 실제
-- 상품가가 600만원이라 테스트할 수 없다. 그런데 비활성 요금제는 결제 자체가 막혀 점검에
-- 쓸 수 없으므로, "살 수는 있지만 아무에게나 보이지는 않는" 상태가 따로 필요했다.
--   is_active   = 판매 가능 여부
--   is_internal = 노출·결제 대상 제한 (roles_code 000 admin · 001 manager)
--
-- 목록 숨김만으로는 부족하다. pricePlanId 는 연속 숫자라 추측 가능하고 결제 API 는 직접
-- 호출할 수 있으므로, 조회(ProductController)와 결제(PaymentService 승인·빌링 두 경로)
-- 모두에서 역할을 검사한다.
--
-- ## license_plan_id 를 NULL 로 두는 것이 핵심이다
--
-- PaymentService 의 중복 구매 차단(requirePurchasable)과 라이선스 발급은 둘 다
-- `if (pricePlan.getLicensePlanId() != null)` 안에 있다. 그래서 NULL 이면
--   - 이미 라이선스를 보유한 계정도 몇 번이든 결제할 수 있다 (점검 반복 가능)
--   - 결제가 끝나도 라이선스가 발급되지 않아 실제 라이선스 목록이 오염되지 않는다
-- 기존 라이선스를 일시 정지시킬 필요가 없다. 선례도 있다 — price_plans id 5 가 이미 NULL 이다.
-- 반대급부: "결제 → 라이선스 발급" 연결은 이 요금제로 검증되지 않는다(의도된 선택).
--
-- ## 금액
--
-- 토스 최소 결제금액은 카드 100원, 계좌이체 200원, 가상계좌는 문서 간 불일치(1원 또는 200원).
-- 카드용은 100원, 가상계좌용은 하한 논란을 피해 1,000원으로 둔다. 가상계좌 수수료는 건당
-- 400원 정액이라 금액을 낮춰도 비용이 줄지 않는다 — 낮출 이유가 없다.
--
-- 결제 수단은 결제 시점에 화면에서 고르는 값이라 요금제가 수단을 강제하지는 않는다.
-- 이름을 나눠 둔 것은 결제 기록에서 어느 흐름으로 들어온 건인지 구분하기 위해서다.

-- 기본값 FALSE = 기존 5건은 모두 공개 유지. NOT NULL 로 두어 "모르는 상태"를 없앤다.
-- 길이 제약이 없는 BOOLEAN 이라 CHAR/VARCHAR 불일치 문제는 없다.
ALTER TABLE price_plans
    ADD COLUMN IF NOT EXISTS is_internal BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN price_plans.is_internal IS '내부 전용 요금제 — true 면 매니저 이상(roles_code 000·001)에게만 노출되고 결제도 그 역할만 가능';

-- 일반 고객 목록 조회는 is_internal = false 만 훑는다. 공개 요금제가 대부분이라 부분 인덱스.
CREATE INDEX IF NOT EXISTS idx_price_plans_public_lookup
    ON price_plans (product_code, currency, price)
    WHERE is_active = TRUE AND is_internal = FALSE;

-- 점검용 요금제 2건. NOT EXISTS 로 감싸 재실행에 안전하게 한다.
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
