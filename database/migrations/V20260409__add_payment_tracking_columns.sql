-- 결제 추적 컬럼 추가
-- 2026-04-09: 결제 디버깅 및 감사 추적용 컬럼

-- payments 테이블: 실패 사유, 클라이언트 IP
ALTER TABLE payments ADD COLUMN IF NOT EXISTS fail_reason TEXT NULL;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS client_ip VARCHAR(45) NULL;

COMMENT ON COLUMN payments.fail_reason IS '결제 실패/오류 사유 (디버깅용)';
COMMENT ON COLUMN payments.client_ip IS '결제 요청 클라이언트 IP (감사 추적용)';

-- payment_details 테이블: 토스 결제 상태, 토스 원본 응답
ALTER TABLE payment_details ADD COLUMN IF NOT EXISTS toss_status VARCHAR(50) NULL;
ALTER TABLE payment_details ADD COLUMN IF NOT EXISTS toss_response_summary TEXT NULL;

COMMENT ON COLUMN payment_details.toss_status IS '토스페이먼츠 결제 상태 (DONE, WAITING_FOR_DEPOSIT, CANCELED 등)';
COMMENT ON COLUMN payment_details.toss_response_summary IS '토스 API 응답 요약 (디버깅용, 민감정보 제외)';
