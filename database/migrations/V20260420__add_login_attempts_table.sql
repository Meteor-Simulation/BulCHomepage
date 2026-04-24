-- 로그인 시도 횟수 제한 테이블 (브루트포스 방지)
CREATE TABLE IF NOT EXISTS login_attempts (
    id                  BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    email               VARCHAR(255) NOT NULL UNIQUE,
    attempt_count       INT NOT NULL DEFAULT 0,
    first_attempt_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_until        TIMESTAMP NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_login_attempts_email ON login_attempts(email);

COMMENT ON TABLE login_attempts IS '로그인 시도 횟수 제한 테이블 - 브루트포스 방지';
COMMENT ON COLUMN login_attempts.attempt_count IS '15분 윈도우 내 실패 횟수';
COMMENT ON COLUMN login_attempts.locked_until IS '잠금 만료 시각 (5회 실패 시 30분 잠금)';
