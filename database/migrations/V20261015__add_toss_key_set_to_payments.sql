-- 결제가 어느 토스 키 세트로 생성됐는지 기록한다.
--
-- ## 왜 필요한가 — 취소할 때 키를 골라야 한다
--
-- 토스는 연동 방식마다 API 키 세트를 따로 발급한다.
--   ck/sk   : API 개별 연동 — 자동결제(빌링)
--   gck/gsk : 주문서형·결제창형 — 결제위젯 (계좌이체·가상계좌·간편결제가 여기에만 열린다)
--
-- 그리고 **세트가 아닌 키를 섞으면 토스가 INVALID_API_KEY 로 거부한다.** 승인뿐 아니라
-- 취소·조회도 마찬가지다. 즉 결제를 취소하려면 "이 결제가 어느 세트로 만들어졌는지" 를
-- 알아야 하는데, 지금까지는 결제 경로가 하나(빌링)뿐이라 그 구분이 필요 없었다.
--
-- ## 왜 주문번호 접두사로 때우지 않는가
--
-- 빌링은 `BILL-`, 위젯은 `BULC_` 로 시작하니 접두사로도 갈라낼 수는 있다. 그러나 그 규칙은
-- 코드 두 곳(서버의 빌링 주문번호 생성, 프론트의 generateOrderId)에 흩어진 암묵적 약속이고,
-- 한쪽이 바뀌면 조용히 틀린다. 이 프로젝트는 같은 종류의 암묵적 약속으로 두 번 당했다 —
--   · card.has("company") 로 카드 결제를 판별 (토스가 그 필드를 없애 판별이 무너짐)
--   · userEmail 파라미터에 실제로는 UUID 가 담김 (findByEmail 이 늘 빈 결과)
-- 둘 다 "틀려도 예외가 안 나고 조용히 빗나가는" 형태였다. 그래서 명시 컬럼으로 둔다.
--
-- ## 기존 행
--
-- 운영 결제 7건은 전부 빌링키 경로다(confirmPayment 는 한 번도 성공한 적이 없다).
-- 따라서 기존 행은 BILLING 으로 채운다. 이후 들어오는 행은 애플리케이션이 직접 넣는다.
--
-- CHAR 가 아니라 VARCHAR 를 쓴다 — bpchar 불일치로 validate 환경에서 기동 실패한 전례가 있다.

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS toss_key_set VARCHAR(10);

-- 기존 행 백필: 위젯 결제가 존재하기 이전이므로 전부 빌링이다.
UPDATE payments
   SET toss_key_set = 'BILLING'
 WHERE toss_key_set IS NULL;

COMMENT ON COLUMN payments.toss_key_set IS '결제를 생성한 토스 키 세트 — BILLING(ck/sk, 자동결제) | WIDGET(gck/gsk, 결제위젯). 취소·조회 시 같은 세트의 키를 써야 한다(섞으면 INVALID_API_KEY)';
