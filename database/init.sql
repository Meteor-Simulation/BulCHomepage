-- =========================================================
-- BulC Homepage Database Schema
-- PostgreSQL 16
-- Generated: 2024-12-22
-- =========================================================

-- Extension for UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =========================================================
-- Drop existing tables (reverse dependency order)
-- =========================================================
DROP TABLE IF EXISTS revoked_offline_tokens CASCADE;
DROP TABLE IF EXISTS license_activations CASCADE;
DROP TABLE IF EXISTS license_plan_entitlements CASCADE;
DROP TABLE IF EXISTS licenses CASCADE;
DROP TABLE IF EXISTS license_plans CASCADE;
DROP TABLE IF EXISTS admin_logs CASCADE;
DROP TABLE IF EXISTS user_change_logs CASCADE;
DROP TABLE IF EXISTS activity_logs CASCADE;
DROP TABLE IF EXISTS email_verifications CASCADE;
DROP TABLE IF EXISTS payments CASCADE;
DROP TABLE IF EXISTS subscriptions CASCADE;
DROP TABLE IF EXISTS price_plans CASCADE;
DROP TABLE IF EXISTS products CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS user_roles CASCADE;

-- =========================================================
-- 1. user_roles (유저 등급 테이블)
-- =========================================================
CREATE TABLE user_roles (
    code            VARCHAR(10) PRIMARY KEY,
    role            VARCHAR(50) NOT NULL
);

COMMENT ON TABLE user_roles IS '유저 등급 테이블 - 사용자 권한 등급 정의';

-- 기본 역할 데이터
INSERT INTO user_roles (code, role) VALUES
    ('000', 'admin'),
    ('001', 'manager'),
    ('002', 'user');

-- =========================================================
-- 2. users (유저 테이블)
-- =========================================================
CREATE TABLE users (
    email           VARCHAR(255) PRIMARY KEY,
    password_hash   VARCHAR(255) NOT NULL,
    roles_code      VARCHAR(10) NOT NULL DEFAULT '002',
    name            VARCHAR(100) NULL,
    phone           VARCHAR(20) NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_users_role FOREIGN KEY (roles_code) REFERENCES user_roles(code)
);

COMMENT ON TABLE users IS '유저 테이블 - 사용자 기본 정보';
COMMENT ON COLUMN users.email IS '이메일 (기본키, 로그인 ID)';
COMMENT ON COLUMN users.password_hash IS '비밀번호 해시';
COMMENT ON COLUMN users.roles_code IS '역할 코드 (000:관리자, 001:매니저, 002:일반)';
COMMENT ON COLUMN users.name IS '이름 (결제 시 입력)';
COMMENT ON COLUMN users.phone IS '전화번호 (결제 시 입력)';

-- 기본 관리자 계정 (비밀번호: test1234!)
INSERT INTO users (email, password_hash, roles_code, name) VALUES
    ('msimul@gamil.com', '$2b$10$85r1YrG0Fqn10YgUffGbduJ1/Aif1WoFkH3eNWEUKzNZA3n/5hdDS', '000', '메테오');

-- =========================================================
-- 3. email_verifications (이메일 인증 테이블)
-- =========================================================
CREATE TABLE email_verifications (
    id                  BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    email               VARCHAR(255) NOT NULL UNIQUE,
    verification_code   VARCHAR(6) NOT NULL,
    expires_at          TIMESTAMP NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE email_verifications IS '이메일 인증 테이블 - 인증 코드 관리';
COMMENT ON COLUMN email_verifications.email IS '인증할 이메일 (UNIQUE - 이메일당 1개 코드)';

-- =========================================================
-- 4. products (상품 종류 테이블)
-- =========================================================
CREATE TABLE products (
    code            VARCHAR(3) PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    description     TEXT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE products IS '상품 종류 테이블 - 판매 상품 정의';
COMMENT ON COLUMN products.code IS '상품 코드 (000~999), PK';

-- 기본 상품 데이터
INSERT INTO products (code, name, description) VALUES
    ('001', 'BULC', '화재시뮬레이션');

-- =========================================================
-- 5. price_plans (상품 가격 테이블)
-- =========================================================
CREATE TABLE price_plans (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    product_code    VARCHAR(3) NOT NULL,
    name            VARCHAR(100) NOT NULL,
    price           DECIMAL(18,2) NOT NULL,
    currency        VARCHAR(10) NOT NULL DEFAULT 'KRW',
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_price_plans_product FOREIGN KEY (product_code) REFERENCES products(code)
);

COMMENT ON TABLE price_plans IS '상품 가격 테이블 - 상품별 요금제 정의';

-- 기본 요금제 데이터
INSERT INTO price_plans (product_code, name, price, currency) VALUES
    ('001', 'BUL:C PRO', 4000000, 'KRW'),
    ('001', 'BUL:C PRO', 2700, 'USD'),
    ('001', 'BUL:C 3D Premium', 5100000, 'KRW'),
    ('001', 'BUL:C 3D Premium', 3500, 'USD');

-- =========================================================
-- 6. subscriptions (유저 구독 관리 테이블)
-- =========================================================
CREATE TABLE subscriptions (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_email      VARCHAR(255) NULL,
    product_code    VARCHAR(3) NOT NULL,
    price_plan_id   BIGINT NOT NULL,
    status          VARCHAR(1) NOT NULL DEFAULT 'A',
    start_date      TIMESTAMP NOT NULL,
    end_date        TIMESTAMP NOT NULL,
    auto_renew      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_subscriptions_user FOREIGN KEY (user_email) REFERENCES users(email),
    CONSTRAINT fk_subscriptions_product FOREIGN KEY (product_code) REFERENCES products(code),
    CONSTRAINT fk_subscriptions_price_plan FOREIGN KEY (price_plan_id) REFERENCES price_plans(id)
);

COMMENT ON TABLE subscriptions IS '유저 구독 관리 테이블 - 사용자의 구독 현황';
COMMENT ON COLUMN subscriptions.status IS 'A: 활성(Active), E: 만료(Expired), C: 취소(Canceled)';

-- =========================================================
-- 7. payments (결제 테이블)
-- =========================================================
CREATE TABLE payments (
    id                  BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_email_fk       VARCHAR(255) NULL,
    user_email          VARCHAR(255) NOT NULL,
    user_name           VARCHAR(100) NULL,
    subscription_id     BIGINT NULL,
    price_plan_id       BIGINT NULL,
    amount              DECIMAL(18,2) NOT NULL,
    currency            VARCHAR(10) NOT NULL DEFAULT 'KRW',
    status              VARCHAR(1) NOT NULL DEFAULT 'P',
    order_name          VARCHAR(255) NULL,
    paid_at             TIMESTAMP NULL,
    refunded_at         TIMESTAMP NULL,
    refund_amount       DECIMAL(18,2) NULL,
    refund_reason       TEXT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_payments_user FOREIGN KEY (user_email_fk) REFERENCES users(email),
    CONSTRAINT fk_payments_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions(id),
    CONSTRAINT fk_payments_price_plan FOREIGN KEY (price_plan_id) REFERENCES price_plans(id)
);

COMMENT ON TABLE payments IS '결제 테이블 - 결제/환불 정보 관리';
COMMENT ON COLUMN payments.status IS 'P: 대기(Pending), C: 완료(Completed), F: 실패(Failed), R: 환불(Refunded)';

-- =========================================================
-- 8. payment_details (결제 상세 테이블)
-- =========================================================
CREATE TABLE payment_details (
    payment_id          BIGINT PRIMARY KEY,
    payment_method      VARCHAR(50) NULL,
    payment_provider    VARCHAR(50) NULL,
    order_id            VARCHAR(100) NULL,
    payment_key         VARCHAR(255) NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_payment_details_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
);

COMMENT ON TABLE payment_details IS '결제 상세 테이블 - PG사 연동 정보';
COMMENT ON COLUMN payment_details.order_id IS '토스페이먼츠 주문 ID';
COMMENT ON COLUMN payment_details.payment_key IS '토스페이먼츠 결제 키';

-- =========================================================
-- 9. activity_logs (활동 로그 테이블)
-- =========================================================
CREATE TABLE activity_logs (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_email      VARCHAR(255) NULL,
    action          VARCHAR(50) NOT NULL,
    target_type     VARCHAR(50) NULL,
    target_id       BIGINT NULL,
    description     TEXT NULL,
    ip_address      VARCHAR(50) NULL,
    user_agent      TEXT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_activity_logs_user FOREIGN KEY (user_email) REFERENCES users(email)
);

COMMENT ON TABLE activity_logs IS '활동 로그 테이블 - 로그인, 구매, 환불 등 기록';
COMMENT ON COLUMN activity_logs.action IS 'login, logout, purchase, refund, subscription_start, subscription_cancel 등';

-- =========================================================
-- 9. user_change_logs (유저 정보 변경 로그 테이블)
-- =========================================================
CREATE TABLE user_change_logs (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_email      VARCHAR(255) NULL,
    changed_field   VARCHAR(50) NOT NULL,
    old_value       TEXT NULL,
    new_value       TEXT NULL,
    changed_by_email VARCHAR(255) NULL,
    change_reason   TEXT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE user_change_logs IS '유저 정보 변경 로그 - 사용자 정보 변경 이력';

-- =========================================================
-- 10. admin_logs (관리자 로그 테이블)
-- =========================================================
CREATE TABLE admin_logs (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    admin_email     VARCHAR(255) NOT NULL,
    action          VARCHAR(100) NOT NULL,
    target_type     VARCHAR(50) NOT NULL,
    target_id       BIGINT NULL,
    description     TEXT NULL,
    ip_address      VARCHAR(50) NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_admin_logs_admin FOREIGN KEY (admin_email) REFERENCES users(email)
);

COMMENT ON TABLE admin_logs IS '관리자 로그 테이블 - 관리자 작업 이력';

-- =========================================================
-- 11. license_plans (라이선스 플랜 테이블)
-- =========================================================
CREATE TABLE license_plans (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id              UUID NOT NULL,
    code                    VARCHAR(64) NOT NULL,
    name                    VARCHAR(255) NOT NULL,
    description             TEXT NULL,
    license_type            VARCHAR(32) NOT NULL,
    duration_days           INT NOT NULL,
    grace_days              INT NOT NULL DEFAULT 0,
    max_activations         INT NOT NULL DEFAULT 1,
    max_concurrent_sessions INT NOT NULL DEFAULT 1,
    allow_offline_days      INT NOT NULL DEFAULT 0,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted              BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE license_plans IS '라이선스 플랜/정책 템플릿 (Admin UI에서 관리)';
COMMENT ON COLUMN license_plans.code IS '사람이 읽기 쉬운 식별자, Admin UI에서 선택/표시할 값';
COMMENT ON COLUMN license_plans.duration_days IS '기본 유효기간 (일 단위)';
COMMENT ON COLUMN license_plans.grace_days IS 'EXPIRED_GRACE 상태로 전환 후 유예기간';
COMMENT ON COLUMN license_plans.allow_offline_days IS '오프라인 허용 일수 (0이면 항상 온라인 필요)';

-- =========================================================
-- 12. license_plan_entitlements (플랜 기능 권한 테이블)
-- =========================================================
CREATE TABLE license_plan_entitlements (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    plan_id         UUID NOT NULL,
    entitlement_key VARCHAR(100) NOT NULL,

    CONSTRAINT fk_plan_entitlement_plan FOREIGN KEY (plan_id) REFERENCES license_plans(id) ON DELETE CASCADE
);

COMMENT ON TABLE license_plan_entitlements IS '플랜별 활성화 기능 목록';
COMMENT ON COLUMN license_plan_entitlements.entitlement_key IS '기능 식별자 (core-simulation, advanced-visualization 등)';

-- =========================================================
-- 13. licenses (라이선스 테이블)
-- =========================================================
CREATE TABLE licenses (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_type      VARCHAR(20) NOT NULL,
    owner_id        UUID NOT NULL,
    product_id      UUID NOT NULL,
    plan_id         UUID NULL,
    license_type    VARCHAR(20) NOT NULL,
    usage_category  VARCHAR(30) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    issued_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valid_from      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valid_until     TIMESTAMP NULL,
    policy_snapshot JSONB NULL,
    license_key     VARCHAR(50) UNIQUE,
    source_order_id UUID NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE licenses IS '라이선스 정보 (Licensing BC Aggregate Root)';
COMMENT ON COLUMN licenses.owner_type IS '소유자 유형: USER(개인), ORG(조직)';
COMMENT ON COLUMN licenses.usage_category IS '사용 용도: 상업용, 연구용, 교육용, 내부평가용';
COMMENT ON COLUMN licenses.policy_snapshot IS '발급 시점의 정책 스냅샷 (JSON)';

-- =========================================================
-- 14. license_activations (라이선스 활성화 테이블)
-- =========================================================
CREATE TABLE license_activations (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    license_id              UUID NOT NULL,
    device_fingerprint      VARCHAR(255) NOT NULL,
    status                  VARCHAR(20) NOT NULL,
    activated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    client_version          VARCHAR(50) NULL,
    client_os               VARCHAR(100) NULL,
    last_ip                 VARCHAR(45) NULL,
    offline_token           VARCHAR(2000) NULL,
    offline_token_expires_at TIMESTAMP NULL,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_activation_license FOREIGN KEY (license_id) REFERENCES licenses(id) ON DELETE CASCADE
);

COMMENT ON TABLE license_activations IS '기기 활성화 정보 (라이선스별 기기 슬롯)';
COMMENT ON COLUMN license_activations.device_fingerprint IS 'HW ID, OS 등을 조합한 기기 식별 해시';
COMMENT ON COLUMN license_activations.offline_token IS '오프라인 환경용 서명된 토큰';

-- =========================================================
-- 15. revoked_offline_tokens (무효화된 오프라인 토큰 테이블)
-- =========================================================
CREATE TABLE revoked_offline_tokens (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    license_id          UUID NOT NULL,
    activation_id       UUID NULL,
    device_fingerprint  VARCHAR(255) NULL,
    token_hash          VARCHAR(255) NOT NULL,
    revoked_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason              VARCHAR(255) NULL,

    CONSTRAINT fk_revoked_token_license FOREIGN KEY (license_id) REFERENCES licenses(id) ON DELETE CASCADE
);

COMMENT ON TABLE revoked_offline_tokens IS '무효화된 오프라인 토큰 목록 (탈취 대응)';

-- =========================================================
-- 인덱스 정의
-- =========================================================

-- users
CREATE INDEX idx_users_roles_code ON users(roles_code);

-- email_verifications
CREATE INDEX idx_email_verifications_email ON email_verifications(email);
CREATE INDEX idx_email_verifications_expires_at ON email_verifications(expires_at);

-- products (code가 PK이므로 별도 인덱스 불필요)
CREATE INDEX idx_products_is_active ON products(is_active);

-- price_plans
CREATE INDEX idx_price_plans_product_code ON price_plans(product_code);
CREATE INDEX idx_price_plans_is_active ON price_plans(is_active);

-- subscriptions
CREATE INDEX idx_subscriptions_user_email ON subscriptions(user_email);
CREATE INDEX idx_subscriptions_product_code ON subscriptions(product_code);
CREATE INDEX idx_subscriptions_status ON subscriptions(status);
CREATE INDEX idx_subscriptions_end_date ON subscriptions(end_date);

-- payments
CREATE INDEX idx_payments_user_email_fk ON payments(user_email_fk);
CREATE INDEX idx_payments_subscription_id ON payments(subscription_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_created_at ON payments(created_at);

-- payment_details (payment_id가 PK이므로 별도 인덱스 불필요)
CREATE INDEX idx_payment_details_order_id ON payment_details(order_id);
CREATE INDEX idx_payment_details_payment_key ON payment_details(payment_key);

-- activity_logs
CREATE INDEX idx_activity_logs_user_email ON activity_logs(user_email);
CREATE INDEX idx_activity_logs_action ON activity_logs(action);
CREATE INDEX idx_activity_logs_created_at ON activity_logs(created_at);

-- user_change_logs
CREATE INDEX idx_user_change_logs_user_email ON user_change_logs(user_email);
CREATE INDEX idx_user_change_logs_created_at ON user_change_logs(created_at);

-- admin_logs
CREATE INDEX idx_admin_logs_admin_email ON admin_logs(admin_email);
CREATE INDEX idx_admin_logs_action ON admin_logs(action);
CREATE INDEX idx_admin_logs_created_at ON admin_logs(created_at);

-- license_plans
CREATE INDEX idx_license_plans_product ON license_plans(product_id);
CREATE INDEX idx_license_plans_active ON license_plans(is_active) WHERE (is_deleted = false);
CREATE UNIQUE INDEX idx_license_plans_code ON license_plans(code) WHERE (is_deleted = false);

-- license_plan_entitlements
CREATE INDEX idx_plan_entitlements_plan ON license_plan_entitlements(plan_id);
CREATE UNIQUE INDEX idx_plan_entitlements_unique ON license_plan_entitlements(plan_id, entitlement_key);

-- licenses
CREATE UNIQUE INDEX idx_licenses_key ON licenses(license_key);
CREATE INDEX idx_licenses_owner ON licenses(owner_type, owner_id);
CREATE INDEX idx_licenses_product ON licenses(product_id);
CREATE INDEX idx_licenses_status ON licenses(status);
CREATE INDEX idx_licenses_valid_until ON licenses(valid_until) WHERE (valid_until IS NOT NULL);
CREATE INDEX idx_licenses_source_order ON licenses(source_order_id);

-- license_activations
CREATE INDEX idx_activations_license ON license_activations(license_id);
CREATE INDEX idx_activations_device ON license_activations(license_id, device_fingerprint);
CREATE INDEX idx_activations_status ON license_activations(status);
CREATE INDEX idx_activations_last_seen ON license_activations(last_seen_at);

-- revoked_offline_tokens
CREATE INDEX idx_revoked_tokens_license ON revoked_offline_tokens(license_id);
CREATE INDEX idx_revoked_tokens_hash ON revoked_offline_tokens(token_hash);

-- =========================================================
-- updated_at 자동 갱신 트리거
-- =========================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

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

CREATE TRIGGER update_payment_details_updated_at BEFORE UPDATE ON payment_details
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_license_plans_updated_at BEFORE UPDATE ON license_plans
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_licenses_updated_at BEFORE UPDATE ON licenses
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_license_activations_updated_at BEFORE UPDATE ON license_activations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
