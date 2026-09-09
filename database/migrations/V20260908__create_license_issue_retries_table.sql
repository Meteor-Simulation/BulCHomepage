-- MDP-832: 결제 성공 후 라이선스 발급 실패 자동 복구
--
-- 결제 승인과 라이선스 발급은 별개 단계다. 결제가 성공한 뒤 발급이 실패하면
-- 지금까지는 payments.fail_reason 에 사유만 남기고 끝나서, 돈은 빠져나갔는데
-- 라이선스는 없는 상태가 되고 복구 수단이 고객센터 문의뿐이었다.
-- (구독 자동갱신 경로는 로그만 남아 아무도 모른 채 라이선스가 만료된다)
--
-- 실패를 이 테이블에 적재하고 스케줄러가 재시도한다.
-- 발급 멱등 키(source_order_id)가 결정적이라 재시도해도 중복 발급되지 않는다.
--
-- 재시도에 필요한 값만 담는다. 발급 4개 경로(결제창·빌링키·웹훅·구독갱신)는
-- 공통 데코레이터에서 적재되어 호출 경로를 알 수 없으므로 origin 컬럼을 두지 않았다.
-- 경로 구분이 필요하면 로그의 [결제]/[빌링결제]/[웹훅] 태그와 source_order_id 로 대조한다.

CREATE TABLE IF NOT EXISTS license_issue_retries (
    id                  BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id             UUID NOT NULL,
    license_plan_id     UUID NOT NULL,
    source_order_id     UUID NOT NULL,
    operation           VARCHAR(10) NOT NULL,
    valid_until         TIMESTAMP NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count         INT NOT NULL DEFAULT 0,
    last_error          TEXT NULL,
    last_attempted_at   TIMESTAMP NULL,
    resolved_at         TIMESTAMP NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_license_issue_retries_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 같은 주문에 대한 복구 레코드는 하나만 둔다.
-- 재시도 중 같은 실패가 또 들어와도 새 행이 쌓이지 않고 기존 행을 갱신한다.
CREATE UNIQUE INDEX IF NOT EXISTS uq_license_issue_retries_source_order
    ON license_issue_retries(source_order_id, operation);

-- 스케줄러 조회용
CREATE INDEX IF NOT EXISTS idx_license_issue_retries_status_retry
    ON license_issue_retries(status, retry_count);

COMMENT ON TABLE license_issue_retries IS '결제 성공 후 라이선스 발급/연장 실패 건의 재시도 큐 (MDP-832)';
COMMENT ON COLUMN license_issue_retries.operation IS 'ISSUE: 신규 발급, RENEW: 구독 갱신 연장';
COMMENT ON COLUMN license_issue_retries.source_order_id IS '발급 멱등 키. 동일 값 재호출 시 중복 발급되지 않는다';
COMMENT ON COLUMN license_issue_retries.valid_until IS 'RENEW 전용 — 연장 후 만료 시각. ISSUE 는 NULL';
COMMENT ON COLUMN license_issue_retries.status IS 'PENDING: 재시도 대기, SUCCESS: 복구 완료, EXHAUSTED: 재시도 소진(운영 개입 필요)';
