-- 카드사 코드(issuerCode) 저장 컬럼 추가.
--
-- 토스페이먼츠는 예전에 카드사 한글명을 cardCompany 로 바로 내려줬는데, API 버전
-- 2024-06-01 부터 그 필드가 응답에서 삭제되고 두 자리 코드 card.issuerCode 만 온다.
-- 백엔드가 여전히 없어진 필드(card.company)를 읽고 있었던 탓에 card_company 가
-- 등록된 카드 4건 전부 NULL 이었다 (2026-10-06 운영 DB 실측, 마이페이지에 카드사명 대신
-- "카드" 라고만 표시되던 원인).
--
-- 이름만 저장하지 않고 코드를 함께 남기는 이유: 코드→이름 표(CardIssuer)는 우리가 들고 있는
-- 사본이라 새 카드사가 생기면 비게 된다. 그때 이름이 NULL 이어도 코드가 남아 있으면
-- 어느 카드사인지 추적해 표에 추가할 수 있다. 코드가 토스가 준 사실이고, 이름은 해석이다.
--
-- 길이 10 = 두 자리 코드에 여유를 둔 값. CHAR 가 아니라 VARCHAR 를 쓴다(bpchar 불일치로
-- validate 환경에서 기동 실패한 전례가 있다).

ALTER TABLE billing_keys
    ADD COLUMN IF NOT EXISTS card_issuer_code VARCHAR(10);

ALTER TABLE payment_details
    ADD COLUMN IF NOT EXISTS card_issuer_code VARCHAR(10);

COMMENT ON COLUMN billing_keys.card_issuer_code   IS '토스 카드사 코드 원본 (card.issuerCode, 예 61=현대). card_company 는 이를 변환한 이름';
COMMENT ON COLUMN payment_details.card_issuer_code IS '토스 카드사 코드 원본 (card.issuerCode). card_company 는 이를 변환한 이름';
