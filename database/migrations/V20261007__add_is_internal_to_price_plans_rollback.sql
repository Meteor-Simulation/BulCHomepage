-- 점검용 내부 요금제 치우기 — 테스트가 끝난 뒤 손으로 실행한다.
--
-- ⚠️ 이 파일은 deploy.sh 가 자동 적용하지 않는다 (*_rollback.sql 은 건너뛴다).
--    자동 적용되면 요금제를 심은 그 배포에서 곧바로 지워져 테스트가 불가능해진다.
--
-- 실행:
--   BULC_SSH_TARGET=bulc-prod 로 접속한 뒤
--   docker exec -i bulc-db-prod psql -U bulc_prod_user -d bulc_homepage_db \
--     < database/migrations/V20261007__add_is_internal_to_price_plans_rollback.sql
--
-- ## DELETE 가 아니라 is_active = FALSE 인 이유
--
-- payments.price_plan_id 가 price_plans 를 FK 로 참조한다. 점검 결제가 한 건이라도 남아 있으면
-- DELETE 는 FK 위반으로 실패한다. 게다가 결제 기록은 회계 자료라 참조가 끊겨선 안 된다 —
-- 어떤 요금제로 결제됐는지 사후에 재구성할 수 없게 된다.
--
-- is_active = FALSE 면 목록 조회(ProductController)와 결제 양쪽에서 빠진다. 점검을 다시 할 때는
-- 이 파일 맨 아래 주석의 UPDATE 로 되살리면 되고, 요금제를 새로 심을 필요가 없다.
--
-- is_internal 컬럼 자체는 남겨 둔다. 다음 점검에도 쓰이고, 지우면 코드(PricePlan.isInternal)와
-- 어긋나 백엔드가 validate 단계에서 기동 실패한다.

BEGIN;

UPDATE price_plans
   SET is_active  = FALSE,
       updated_at = CURRENT_TIMESTAMP
 WHERE is_internal = TRUE
   AND is_active   = TRUE;

-- 남아 있는 점검 요금제와 각각에 묶인 결제 건수를 확인한다.
-- 결제 건수가 0 이 아니어도 정상이다 — 점검한 흔적이다.
SELECT p.id,
       p.name,
       p.price,
       p.is_active,
       (SELECT count(*) FROM payments pay WHERE pay.price_plan_id = p.id) AS payment_count
  FROM price_plans p
 WHERE p.is_internal = TRUE
 ORDER BY p.id;

COMMIT;

-- ## 다시 점검할 때 되살리기
--   UPDATE price_plans SET is_active = TRUE, updated_at = CURRENT_TIMESTAMP WHERE is_internal = TRUE;
--
-- ## 기록까지 완전히 지우려면 (권장하지 않음)
-- 결제 기록을 먼저 지워야 FK 가 풀린다. 회계 자료를 지우는 일이므로 정말 필요한지 따져 볼 것.
--   DELETE FROM payment_details WHERE payment_id IN (
--       SELECT pay.id FROM payments pay
--        JOIN price_plans p ON p.id = pay.price_plan_id
--       WHERE p.is_internal = TRUE);
--   DELETE FROM payments WHERE price_plan_id IN (SELECT id FROM price_plans WHERE is_internal = TRUE);
--   DELETE FROM price_plans WHERE is_internal = TRUE;
