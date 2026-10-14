-- 웹훅 검증용 토스 secret 저장.
--
-- ## 왜 필요한가 — 웹훅이 100% 거부되고 있었다
--
-- PaymentWebhookController 가 `Authorization: Basic base64(시크릿키:)` 헤더를 요구했는데,
-- 토스 웹훅은 그런 헤더를 보내지 않는다. 토스가 보내는 헤더는
-- tosspayments-webhook-transmission-{time,retried-count,id} 뿐이다.
--
-- 그래서 정상 웹훅이 전부 403 으로 거부되고 토스가 최대 7회 재시도하다 포기했다
-- (2026-10-13 로그: 같은 paymentKey 가 02:46·02:47·02:51 세 번 거부됨).
-- 코드 주석에는 "토스는 Authorization 헤더로 전송합니다" 라고 적혀 있었는데 사실과 달랐다.
--
-- 가상계좌는 입금 완료 판정이 전적으로 웹훅에 달려 있다. 계약이 열리는 순간 고객이 입금해도
-- 결제가 완료되지 않는 상태였다.
--
-- ## 올바른 검증 — secret 대조
--
-- 토스가 가상계좌·결제상태 웹훅에 안내하는 방법이다. 결제 생성 응답의 `secret`(최대 50자)을
-- 저장해 두고, 웹훅 본문의 secret 과 같은지 본다. 다르면 제3자가 보낸 데이터다.
--
-- 이것만으로 끝내지 않는다 — paymentKey 로 토스에 재조회해 상태를 확인하는 장치가 이미 있고
-- 그쪽이 더 강한 방어선이다(본문을 아예 신뢰하지 않는다). secret 대조는 재조회 전에 값싸게
-- 걸러내는 1차 관문이다.
--
-- 길이 64 = 상한 50 에 여유를 둔 값. CHAR 가 아니라 VARCHAR 를 쓴다(bpchar 불일치로
-- validate 환경에서 기동 실패한 전례가 있다).
--
-- 기존 행은 NULL 로 남는다. NULL 이면 secret 대조를 건너뛰고 재조회 검증만 적용한다 —
-- 과거 결제의 웹훅을 막아서는 안 된다.

ALTER TABLE payment_details
    ADD COLUMN IF NOT EXISTS toss_secret VARCHAR(64);

COMMENT ON COLUMN payment_details.toss_secret IS '웹훅 검증용 토스 secret (결제 생성 응답의 secret). 웹훅 본문의 secret 과 대조한다. NULL 이면 대조를 건너뛰고 재조회 검증만 적용';
