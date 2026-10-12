-- 결제 멱등키 + 환불 요청 테이블.
--
-- ## 왜 멱등키가 필요한가 — 앞선 방어가 구조적으로 무용했다
--
-- 2026-10-07 에 넣은 중복 차단(existsRecentCompleted)은 "최근 완료된 결제가 있나" 를 본다.
-- 그런데 결제 행은 토스 승인이 끝난 뒤에야 저장된다. 토스 응답이 7~9초 걸리므로:
--
--   01:22:30.855  요청 A  → 완료 결제 없음 → 토스 호출
--   01:22:32.942  요청 B  → 여전히 없음(A 미커밋) → 토스 호출   ← 둘 다 승인
--   01:22:38.056  A·B 동시 저장
--
-- 검사가 지켜야 할 바로 그 구간이 비어 있었다. 2026-10-12 에 100원이 또 2건 청구됐다.
-- 프론트 방어(isSubmittingPayment)는 배포돼 있었는데도 뚫렸다 — 클라이언트 상태에
-- 의존하는 방어는 캐시된 구버전 번들·탭 중복 등으로 언제든 무력화된다.
--
-- 그래서 DB 유니크 제약으로 바꾼다. 두 요청이 동시에 와도 INSERT 단계에서 하나가 지고,
-- 진 쪽은 토스를 부르지 못한다. "검사 후 실행" 이 아니라 "예약에 성공한 쪽만 실행" 이다.
--
-- 길이 300 = 토스 Idempotency-Key 상한과 맞췄다. 같은 값을 토스에도 보내 2중으로 막는다
-- (토스는 같은 키면 재처리 없이 원래 응답을 돌려준다, 15일 유효).
--
-- 기존 행은 NULL 로 남는다. UNIQUE 는 NULL 을 중복으로 보지 않으므로 과거 데이터에
-- 영향이 없고, 부분 인덱스로 NULL 을 아예 제외해 둔다.

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(300);

COMMENT ON COLUMN payments.idempotency_key IS '결제 멱등키 — 같은 키로 두 번 요청해도 한 번만 승인된다. 토스 Idempotency-Key 로도 함께 전달';

CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_idempotency_key
    ON payments (idempotency_key)
 WHERE idempotency_key IS NOT NULL;

-- ## 환불 요청 (고객 신청 → 관리자 처리)
--
-- 고객이 직접 환불을 실행하게 두지 않는다. 환불은 돈이 나가는 일이고 사유 확인·중복
-- 검토가 필요하다. 그래서 요청만 받고, 실제 승인취소는 관리자가 결제관리에서 수행한다.
--
-- payment_id 에 FK 를 걸되 ON DELETE 를 두지 않는다 — 결제 기록은 회계 자료라 지우지
-- 않는 것이 전제다. 혹시 지워야 할 일이 생기면 그때 요청도 함께 판단해야 한다.
--
-- 한 결제에 처리 대기 중인 요청이 둘 있으면 관리자가 무엇을 처리할지 알 수 없다.
-- 부분 유니크 인덱스로 PENDING 은 결제당 1건만 허용한다. 거절·완료된 뒤에는 다시 낼 수 있다.

CREATE TABLE IF NOT EXISTS refund_requests (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    payment_id      BIGINT NOT NULL,
    user_id         UUID NOT NULL,
    reason          TEXT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    admin_note      TEXT NULL,
    processed_by    UUID NULL,
    processed_at    TIMESTAMP NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_refund_requests_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
    CONSTRAINT fk_refund_requests_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ck_refund_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

COMMENT ON TABLE refund_requests IS '환불 요청 — 고객이 신청하고 관리자가 결제관리에서 처리한다';
COMMENT ON COLUMN refund_requests.reason IS '고객이 적은 환불 사유';
COMMENT ON COLUMN refund_requests.status IS 'PENDING 대기 · APPROVED 환불완료 · REJECTED 거절';
COMMENT ON COLUMN refund_requests.admin_note IS '관리자 메모 (거절 사유 등). 고객에게 보여 줄 수 있다';
COMMENT ON COLUMN refund_requests.processed_by IS '처리한 관리자 user id';

-- 결제당 대기 중 요청은 1건만 (거절·완료 뒤에는 다시 낼 수 있다)
CREATE UNIQUE INDEX IF NOT EXISTS uq_refund_requests_pending_payment
    ON refund_requests (payment_id)
 WHERE status = 'PENDING';

-- 관리자 화면: 대기 중인 요청을 먼저 본다
CREATE INDEX IF NOT EXISTS idx_refund_requests_status_created
    ON refund_requests (status, created_at DESC);

-- 마이페이지: 내 요청 조회
CREATE INDEX IF NOT EXISTS idx_refund_requests_user
    ON refund_requests (user_id, created_at DESC);
