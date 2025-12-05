-- ===============================
-- BulC Homepage Database Schema
-- PostgreSQL 16
-- ===============================

-- 기존 테이블 삭제 (의존성 순서대로)
DROP TABLE IF EXISTS admin_logs CASCADE;
DROP TABLE IF EXISTS user_change_logs CASCADE;
DROP TABLE IF EXISTS activity_logs CASCADE;
DROP TABLE IF EXISTS payments CASCADE;
DROP TABLE IF EXISTS subscriptions CASCADE;
DROP TABLE IF EXISTS price_plans CASCADE;
DROP TABLE IF EXISTS products CASCADE;
DROP TABLE IF EXISTS user_roles CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- ===============================
-- 1. users (유저 테이블)
-- ===============================
CREATE TABLE users (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    email           VARCHAR(255) NOT NULL UNIQUE,       -- 이메일 (로그인 ID)
    password_hash   VARCHAR(255) NULL,                  -- 비밀번호 해시
    name            VARCHAR(100) NULL,                  -- 이름
    phone_number    VARCHAR(50) NULL,                   -- 전화번호
    status          VARCHAR(20) NOT NULL DEFAULT 'active',  -- 계정 상태: active, inactive, suspended
    role_id         BIGINT NULL,                        -- 유저 등급 FK
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE users IS '유저 테이블 - 사용자 기본 정보';
COMMENT ON COLUMN users.status IS 'active: 활성, inactive: 비활성, suspended: 정지';

-- ===============================
-- 2. user_roles (유저 등급 테이블)
-- ===============================
CREATE TABLE user_roles (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    code            VARCHAR(50) NOT NULL UNIQUE,        -- 등급 코드: admin, user, premium
    name            VARCHAR(100) NOT NULL,              -- 등급명: 관리자, 일반 사용자, 프리미엄
    description     TEXT NULL,                          -- 설명
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE user_roles IS '유저 등급 테이블 - 사용자 권한 등급 정의';

-- users 테이블에 FK 추가
ALTER TABLE users ADD CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES user_roles(id);

-- ===============================
-- 3. products (상품 종류 테이블)
-- ===============================
CREATE TABLE products (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    code            VARCHAR(50) NOT NULL UNIQUE,        -- 상품 코드: BULC, VR_TRAINING
    name            VARCHAR(255) NOT NULL,              -- 상품명
    description     TEXT NULL,                          -- 상품 설명
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,      -- 활성화 여부
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE products IS '상품 종류 테이블 - 판매 상품 정의';

-- ===============================
-- 4. price_plans (상품 가격 테이블)
-- ===============================
CREATE TABLE price_plans (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    product_id      BIGINT NOT NULL,                    -- 상품 FK
    name            VARCHAR(100) NOT NULL,              -- 요금제명: 1개월, 3개월, 6개월, 12개월
    duration_months INT NOT NULL,                       -- 기간(개월)
    price           DECIMAL(18,2) NOT NULL,             -- 가격
    currency        VARCHAR(10) NOT NULL DEFAULT 'KRW', -- 통화
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,      -- 활성화 여부
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_price_plans_product FOREIGN KEY (product_id) REFERENCES products(id)
);

COMMENT ON TABLE price_plans IS '상품 가격 테이블 - 상품별 요금제 정의';
COMMENT ON COLUMN price_plans.duration_months IS '구독 기간 (개월 단위)';

-- ===============================
-- 5. subscriptions (유저 구독 관리 테이블)
-- ===============================
CREATE TABLE subscriptions (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id         BIGINT NOT NULL,                    -- 유저 FK
    product_id      BIGINT NOT NULL,                    -- 상품 FK
    price_plan_id   BIGINT NOT NULL,                    -- 요금제 FK
    status          VARCHAR(20) NOT NULL DEFAULT 'active',  -- 구독 상태: active, expired, canceled
    start_date      TIMESTAMP NOT NULL,                 -- 구독 시작일
    end_date        TIMESTAMP NOT NULL,                 -- 구독 종료일
    auto_renew      BOOLEAN NOT NULL DEFAULT FALSE,     -- 자동 갱신 여부
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_subscriptions_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_subscriptions_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_subscriptions_price_plan FOREIGN KEY (price_plan_id) REFERENCES price_plans(id)
);

COMMENT ON TABLE subscriptions IS '유저 구독 관리 테이블 - 사용자의 구독 현황';
COMMENT ON COLUMN subscriptions.status IS 'active: 활성, expired: 만료, canceled: 취소';

-- ===============================
-- 6. payments (결제 테이블 - 구독 정보 테이블)
-- ===============================
CREATE TABLE payments (
    id                  BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id             BIGINT NOT NULL,                    -- 유저 FK
    user_email          VARCHAR(255) NOT NULL,              -- 결제 시점 이메일
    user_name           VARCHAR(100) NULL,                  -- 결제 시점 이름
    subscription_id     BIGINT NULL,                        -- 구독 FK
    price_plan_id       BIGINT NOT NULL,                    -- 요금제 FK
    amount              DECIMAL(18,2) NOT NULL,             -- 결제 금액
    currency            VARCHAR(10) NOT NULL DEFAULT 'KRW', -- 통화
    status              VARCHAR(20) NOT NULL DEFAULT 'pending', -- 결제 상태
    payment_method      VARCHAR(50) NULL,                   -- 결제 수단: card, bank, kakao
    payment_provider    VARCHAR(50) NULL,                   -- PG사: toss, inicis
    transaction_id      VARCHAR(255) NULL,                  -- PG 거래 ID
    paid_at             TIMESTAMP NULL,                     -- 결제 완료 시각
    refunded_at         TIMESTAMP NULL,                     -- 환불 시각
    refund_amount       DECIMAL(18,2) NULL,                 -- 환불 금액
    refund_reason       TEXT NULL,                          -- 환불 사유
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_payments_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_payments_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions(id),
    CONSTRAINT fk_payments_price_plan FOREIGN KEY (price_plan_id) REFERENCES price_plans(id)
);

COMMENT ON TABLE payments IS '결제 테이블 - 결제/환불 정보 관리';
COMMENT ON COLUMN payments.status IS 'pending: 대기, completed: 완료, failed: 실패, refunded: 환불';

-- ===============================
-- 7. activity_logs (로그 테이블 - 로그인, 구매, 환불 등)
-- ===============================
CREATE TABLE activity_logs (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id         BIGINT NULL,                        -- 유저 FK (비로그인 시 NULL)
    action          VARCHAR(50) NOT NULL,               -- 활동 유형
    target_type     VARCHAR(50) NULL,                   -- 대상 타입: payment, subscription
    target_id       BIGINT NULL,                        -- 대상 ID
    description     TEXT NULL,                          -- 상세 설명
    ip_address      VARCHAR(50) NULL,                   -- IP 주소
    user_agent      TEXT NULL,                          -- 브라우저 정보
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_activity_logs_user FOREIGN KEY (user_id) REFERENCES users(id)
);

COMMENT ON TABLE activity_logs IS '활동 로그 테이블 - 로그인, 구매, 환불 등 기록';
COMMENT ON COLUMN activity_logs.action IS 'login, logout, purchase, refund, subscription_start, subscription_cancel 등';

-- ===============================
-- 8. user_change_logs (유저 정보 변경 로그 테이블)
-- ===============================
CREATE TABLE user_change_logs (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id         BIGINT NOT NULL,                    -- 유저 FK
    changed_field   VARCHAR(100) NOT NULL,              -- 변경된 필드명
    old_value       TEXT NULL,                          -- 이전 값
    new_value       TEXT NULL,                          -- 새 값
    changed_by      BIGINT NULL,                        -- 변경한 사람 (관리자 또는 본인)
    change_reason   TEXT NULL,                          -- 변경 사유
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_user_change_logs_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_user_change_logs_changed_by FOREIGN KEY (changed_by) REFERENCES users(id)
);

COMMENT ON TABLE user_change_logs IS '유저 정보 변경 로그 - 사용자 정보 변경 이력';

-- ===============================
-- 9. admin_logs (관리자 로그 테이블)
-- ===============================
CREATE TABLE admin_logs (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    admin_id        BIGINT NOT NULL,                    -- 관리자 FK
    action          VARCHAR(100) NOT NULL,              -- 작업 유형
    target_type     VARCHAR(50) NOT NULL,               -- 대상 타입: user, product, subscription
    target_id       BIGINT NULL,                        -- 대상 ID
    description     TEXT NULL,                          -- 작업 설명
    ip_address      VARCHAR(50) NULL,                   -- IP 주소
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_admin_logs_admin FOREIGN KEY (admin_id) REFERENCES users(id)
);

COMMENT ON TABLE admin_logs IS '관리자 로그 테이블 - 관리자 작업 이력';

-- ===============================
-- 인덱스 정의
-- ===============================

-- users
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_role_id ON users(role_id);

-- products
CREATE INDEX idx_products_code ON products(code);
CREATE INDEX idx_products_is_active ON products(is_active);

-- price_plans
CREATE INDEX idx_price_plans_product_id ON price_plans(product_id);
CREATE INDEX idx_price_plans_is_active ON price_plans(is_active);

-- subscriptions
CREATE INDEX idx_subscriptions_user_id ON subscriptions(user_id);
CREATE INDEX idx_subscriptions_product_id ON subscriptions(product_id);
CREATE INDEX idx_subscriptions_status ON subscriptions(status);
CREATE INDEX idx_subscriptions_end_date ON subscriptions(end_date);

-- payments
CREATE INDEX idx_payments_user_id ON payments(user_id);
CREATE INDEX idx_payments_subscription_id ON payments(subscription_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_created_at ON payments(created_at);

-- activity_logs
CREATE INDEX idx_activity_logs_user_id ON activity_logs(user_id);
CREATE INDEX idx_activity_logs_action ON activity_logs(action);
CREATE INDEX idx_activity_logs_created_at ON activity_logs(created_at);

-- user_change_logs
CREATE INDEX idx_user_change_logs_user_id ON user_change_logs(user_id);
CREATE INDEX idx_user_change_logs_created_at ON user_change_logs(created_at);

-- admin_logs
CREATE INDEX idx_admin_logs_admin_id ON admin_logs(admin_id);
CREATE INDEX idx_admin_logs_action ON admin_logs(action);
CREATE INDEX idx_admin_logs_created_at ON admin_logs(created_at);

-- ===============================
-- updated_at 자동 갱신 트리거
-- ===============================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_products_updated_at BEFORE UPDATE ON products
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_price_plans_updated_at BEFORE UPDATE ON price_plans
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_subscriptions_updated_at BEFORE UPDATE ON subscriptions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_payments_updated_at BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
