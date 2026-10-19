-- V20261015__add_toss_key_set_to_payments.sql 되돌리기.
--
-- 주의: 이 컬럼을 지우면 위젯 결제의 취소가 빌링 키(sk)로 나가 INVALID_API_KEY 로 실패한다.
-- 애플리케이션을 이전 버전으로 되돌린 뒤에만 실행할 것.

ALTER TABLE payments
    DROP COLUMN IF EXISTS toss_key_set;
