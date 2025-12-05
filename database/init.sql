-- ===============================
-- BulC Homepage Database Initialization
-- PostgreSQL 16
-- ===============================

-- 확장 기능 설치 (필요 시)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ===============================
-- 1) 회원 계정 기본 정보: users
-- ===============================
CREATE TABLE users (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NULL,
    email_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    status          VARCHAR(50) NOT NULL DEFAULT 'active',
    sign_up_channel VARCHAR(50) NULL,
    locale          VARCHAR(10) NULL,
    timezone        VARCHAR(50) NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ===============================
-- 2) 회원 상세 프로필: user_profiles
-- ===============================
CREATE TABLE user_profiles (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id         BIGINT NOT NULL,
    name            VARCHAR(100) NULL,
    phone_number    VARCHAR(50) NULL,
    company_name    VARCHAR(255) NULL,
    department      VARCHAR(255) NULL,
    job_title       VARCHAR(255) NULL,
    company_size    VARCHAR(50) NULL,
    address_line1   VARCHAR(255) NULL,
    address_line2   VARCHAR(255) NULL,
    city            VARCHAR(100) NULL,
    state           VARCHAR(100) NULL,
    postal_code     VARCHAR(20) NULL,
    country         VARCHAR(100) NULL,
    extra_meta      JSON NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- ===============================
-- 3) 소셜 로그인 / 외부 인증: user_auth_providers
-- ===============================
CREATE TABLE user_auth_providers (
    id                  BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id             BIGINT NOT NULL,
    provider            VARCHAR(50) NOT NULL,
    provider_user_id    VARCHAR(255) NOT NULL,
    access_token        TEXT NULL,
    refresh_token       TEXT NULL,
    token_expires_at    TIMESTAMP NULL,
    raw_profile         JSON NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_auth_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uq_provider_user UNIQUE (provider, provider_user_id)
);

-- ===============================
-- 4) 역할 정의: user_roles
-- ===============================
CREATE TABLE user_roles (
    id          BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    code        VARCHAR(50) NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description TEXT NULL
);

-- ===============================
-- 5) 사용자-역할 매핑: user_role_mappings
-- ===============================
CREATE TABLE user_role_mappings (
    id          BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id     BIGINT NOT NULL,
    role_id     BIGINT NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_role_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_role_role FOREIGN KEY (role_id) REFERENCES user_roles(id),
    CONSTRAINT uq_user_role UNIQUE (user_id, role_id)
);

-- ===============================
-- 6) 회원 상태 변경 이력: user_status_logs
-- ===============================
CREATE TABLE user_status_logs (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id         BIGINT NOT NULL,
    from_status     VARCHAR(50) NULL,
    to_status       VARCHAR(50) NOT NULL,
    reason          TEXT NULL,
    changed_by      BIGINT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_statuslog_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- ===============================
-- 7) 솔루션(상품): products
-- ===============================
CREATE TABLE products (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    code            VARCHAR(50) NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    description     TEXT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ===============================
-- 8) 요금제/가격: price_plans
-- ===============================
CREATE TABLE price_plans (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    product_id      BIGINT NOT NULL,
    name            VARCHAR(255) NOT NULL,
    billing_type    VARCHAR(50) NOT NULL,
    billing_period  VARCHAR(50) NULL,
    amount          DECIMAL(18,2) NOT NULL,
    currency        VARCHAR(10) NOT NULL DEFAULT 'KRW',
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_price_product FOREIGN KEY (product_id) REFERENCES products(id)
);

-- ===============================
-- 9) 구독 정보: subscriptions
-- ===============================
CREATE TABLE subscriptions (
    id                   BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id              BIGINT NOT NULL,
    price_plan_id        BIGINT NOT NULL,
    status               VARCHAR(50) NOT NULL,
    current_period_start TIMESTAMP NOT NULL,
    current_period_end   TIMESTAMP NOT NULL,
    cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
    canceled_at          TIMESTAMP NULL,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sub_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_sub_plan FOREIGN KEY (price_plan_id) REFERENCES price_plans(id)
);

-- ===============================
-- 10) 주문: orders
-- ===============================
CREATE TABLE orders (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id         BIGINT NOT NULL,
    order_number    VARCHAR(100) NOT NULL UNIQUE,
    status          VARCHAR(50) NOT NULL,
    order_type      VARCHAR(50) NOT NULL,
    total_amount    DECIMAL(18,2) NOT NULL,
    currency        VARCHAR(10) NOT NULL DEFAULT 'KRW',
    note            TEXT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- ===============================
-- 10-1) 주문 항목: order_items
-- ===============================
CREATE TABLE order_items (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    order_id        BIGINT NOT NULL,
    product_id      BIGINT NOT NULL,
    price_plan_id   BIGINT NULL,
    quantity        INT NOT NULL DEFAULT 1,
    unit_amount     DECIMAL(18,2) NOT NULL,
    total_amount    DECIMAL(18,2) NOT NULL,
    metadata        JSON NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_orderitems_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_orderitems_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_orderitems_priceplan FOREIGN KEY (price_plan_id) REFERENCES price_plans(id)
);

-- ===============================
-- 11) 결제: payments
-- ===============================
CREATE TABLE payments (
    id                  BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    order_id            BIGINT NOT NULL,
    user_id             BIGINT NOT NULL,
    provider            VARCHAR(50) NOT NULL,
    provider_payment_id VARCHAR(255) NOT NULL,
    amount              DECIMAL(18,2) NOT NULL,
    currency            VARCHAR(10) NOT NULL DEFAULT 'KRW',
    status              VARCHAR(50) NOT NULL,
    paid_at             TIMESTAMP NULL,
    failure_reason      TEXT NULL,
    raw_response        JSON NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_payments_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- ===============================
-- 12) 환불: refunds
-- ===============================
CREATE TABLE refunds (
    id                 BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    payment_id         BIGINT NOT NULL,
    refund_amount      DECIMAL(18,2) NOT NULL,
    reason             TEXT NULL,
    status             VARCHAR(50) NOT NULL,
    provider_refund_id VARCHAR(255) NULL,
    refunded_at        TIMESTAMP NULL,
    raw_response       JSON NULL,
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
);

-- ===============================
-- 13) 사용자 활동 로그: user_activity_logs
-- ===============================
CREATE TABLE user_activity_logs (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id         BIGINT NULL,
    action          VARCHAR(100) NOT NULL,
    resource_path   VARCHAR(500) NULL,
    http_method     VARCHAR(10) NULL,
    ip_address      VARCHAR(50) NULL,
    user_agent      TEXT NULL,
    referrer        VARCHAR(500) NULL,
    metadata        JSON NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_activity_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- ===============================
-- 14) 관리자 운영 로그: admin_operation_logs
-- ===============================
CREATE TABLE admin_operation_logs (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    admin_user_id   BIGINT NOT NULL,
    action          VARCHAR(100) NOT NULL,
    target_type     VARCHAR(50) NOT NULL,
    target_id       BIGINT NULL,
    target_user_id  BIGINT NULL,
    description     TEXT NULL,
    ip_address      VARCHAR(50) NULL,
    user_agent      TEXT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_adminlog_admin FOREIGN KEY (admin_user_id) REFERENCES users(id),
    CONSTRAINT fk_adminlog_targetuser FOREIGN KEY (target_user_id) REFERENCES users(id)
);

-- ===============================
-- 15) 인증/검증: verifications
-- ===============================
CREATE TABLE verifications (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id         BIGINT NULL,
    channel         VARCHAR(20) NOT NULL,
    target_value    VARCHAR(255) NOT NULL,
    purpose         VARCHAR(50) NOT NULL,
    code            VARCHAR(50) NOT NULL,
    expires_at      TIMESTAMP NOT NULL,
    verified_at     TIMESTAMP NULL,
    attempt_count   INT NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'pending',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_verifications_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- ===============================
-- 16) 로그인 시도 기록: auth_login_attempts
-- ===============================
CREATE TABLE auth_login_attempts (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id         BIGINT NULL,
    email           VARCHAR(255) NULL,
    success         BOOLEAN NOT NULL,
    failure_reason  VARCHAR(100) NULL,
    ip_address      VARCHAR(50) NULL,
    user_agent      TEXT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ===============================
-- 인덱스 정의
-- ===============================

-- users
CREATE UNIQUE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_email_verified ON users(email_verified);
CREATE INDEX idx_users_created_at ON users(created_at);

-- user_profiles
CREATE INDEX idx_user_profiles_user_id ON user_profiles(user_id);
CREATE INDEX idx_user_profiles_company_name ON user_profiles(company_name);

-- user_auth_providers
CREATE INDEX idx_auth_providers_user_id ON user_auth_providers(user_id);
CREATE UNIQUE INDEX idx_auth_providers_provider_user ON user_auth_providers(provider, provider_user_id);

-- user_roles
CREATE UNIQUE INDEX idx_user_roles_code ON user_roles(code);

-- user_role_mappings
CREATE INDEX idx_user_role_mappings_user_id ON user_role_mappings(user_id);
CREATE INDEX idx_user_role_mappings_role_id ON user_role_mappings(role_id);

-- user_status_logs
CREATE INDEX idx_status_logs_user_id ON user_status_logs(user_id);
CREATE INDEX idx_status_logs_user_created_at ON user_status_logs(user_id, created_at);

-- products
CREATE UNIQUE INDEX idx_products_code ON products(code);
CREATE INDEX idx_products_is_active ON products(is_active);

-- price_plans
CREATE INDEX idx_price_plans_product_id ON price_plans(product_id);
CREATE INDEX idx_price_plans_billing_type ON price_plans(billing_type);
CREATE INDEX idx_price_plans_is_active ON price_plans(is_active);

-- subscriptions
CREATE INDEX idx_subscriptions_user_id ON subscriptions(user_id);
CREATE INDEX idx_subscriptions_price_plan_id ON subscriptions(price_plan_id);
CREATE INDEX idx_subscriptions_status ON subscriptions(status);
CREATE INDEX idx_subscriptions_user_status ON subscriptions(user_id, status);
CREATE INDEX idx_subscriptions_current_period_end ON subscriptions(current_period_end);

-- orders
CREATE UNIQUE INDEX idx_orders_order_number ON orders(order_number);
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created_at ON orders(created_at);

-- order_items
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_product_id ON order_items(product_id);
CREATE INDEX idx_order_items_price_plan_id ON order_items(price_plan_id);

-- payments
CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_payments_user_id ON payments(user_id);
CREATE UNIQUE INDEX idx_payments_provider_payment_id ON payments(provider, provider_payment_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_created_at ON payments(created_at);

-- refunds
CREATE INDEX idx_refunds_payment_id ON refunds(payment_id);
CREATE INDEX idx_refunds_status ON refunds(status);

-- user_activity_logs
CREATE INDEX idx_activity_logs_user_id ON user_activity_logs(user_id);
CREATE INDEX idx_activity_logs_action ON user_activity_logs(action);
CREATE INDEX idx_activity_logs_created_at ON user_activity_logs(created_at);
CREATE INDEX idx_activity_logs_user_created ON user_activity_logs(user_id, created_at);

-- admin_operation_logs
CREATE INDEX idx_admin_logs_admin_user_id ON admin_operation_logs(admin_user_id);
CREATE INDEX idx_admin_logs_target ON admin_operation_logs(target_type, target_id);
CREATE INDEX idx_admin_logs_created_at ON admin_operation_logs(created_at);

-- verifications
CREATE INDEX idx_verifications_user_id ON verifications(user_id);
CREATE INDEX idx_verifications_lookup ON verifications(channel, target_value, purpose, status);
CREATE INDEX idx_verifications_created_at ON verifications(created_at);

-- auth_login_attempts
CREATE INDEX idx_login_attempts_user_id ON auth_login_attempts(user_id);
CREATE INDEX idx_login_attempts_email ON auth_login_attempts(email);
CREATE INDEX idx_login_attempts_created_at ON auth_login_attempts(created_at);
CREATE INDEX idx_login_attempts_ip_created ON auth_login_attempts(ip_address, created_at);

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

CREATE TRIGGER update_user_profiles_updated_at BEFORE UPDATE ON user_profiles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_user_auth_providers_updated_at BEFORE UPDATE ON user_auth_providers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_products_updated_at BEFORE UPDATE ON products
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_price_plans_updated_at BEFORE UPDATE ON price_plans
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_subscriptions_updated_at BEFORE UPDATE ON subscriptions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_orders_updated_at BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_payments_updated_at BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_refunds_updated_at BEFORE UPDATE ON refunds
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ===============================
-- 초기 데이터 삽입
-- ===============================

-- 기본 역할 데이터
INSERT INTO user_roles (code, name, description) VALUES
    ('admin', '관리자', '전체 시스템 관리자'),
    ('user', '일반 사용자', '일반 솔루션 이용자'),
    ('partner', '파트너', '비즈니스 파트너');

-- 기본 상품 데이터
INSERT INTO products (code, name, description, is_active) VALUES
    ('BULC', 'BulC 화재 시뮬레이션', '실제 화재 데이터 기반 연기 시뮬레이션', TRUE),
    ('METEOR', 'Meteor Simulation', '화재 확산 예측 시뮬레이션', TRUE),
    ('VR_TRAINING', 'VR 안전 교육', 'VR 기반 화재 대피 훈련', TRUE);

-- ===============================
-- 17) 라이선스 플랜 (정책 템플릿): license_plans
-- ===============================
CREATE TABLE license_plans (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id              UUID NOT NULL,                  -- 제품 ID (products 테이블 참조)
    code                    VARCHAR(64) NOT NULL,           -- 식별 코드 (ex: "TRIAL_7D", "PRO_SUB_1Y")
    name                    VARCHAR(255) NOT NULL,          -- 표시 이름
    description             TEXT NULL,                      -- 설명

    license_type            VARCHAR(32) NOT NULL,           -- 'TRIAL', 'SUBSCRIPTION', 'PERPETUAL'
    duration_days           INT NOT NULL,                   -- 기본 유효기간 (일)
    grace_days              INT NOT NULL DEFAULT 0,         -- 유예기간 (EXPIRED_GRACE용)
    max_activations         INT NOT NULL DEFAULT 1,         -- 최대 기기 수
    max_concurrent_sessions INT NOT NULL DEFAULT 1,         -- 동시 세션 제한
    allow_offline_days      INT NOT NULL DEFAULT 0,         -- 오프라인 허용 일수

    is_active               BOOLEAN NOT NULL DEFAULT TRUE,  -- 활성 상태
    is_deleted              BOOLEAN NOT NULL DEFAULT FALSE, -- 소프트 삭제

    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE license_plans IS '라이선스 플랜/정책 템플릿 (Admin UI에서 관리)';
COMMENT ON COLUMN license_plans.code IS '사람이 읽기 쉬운 식별자, Admin UI에서 선택/표시할 값';
COMMENT ON COLUMN license_plans.duration_days IS '기본 유효기간 (일 단위)';
COMMENT ON COLUMN license_plans.grace_days IS 'EXPIRED_GRACE 상태로 전환 후 유예기간';
COMMENT ON COLUMN license_plans.allow_offline_days IS '오프라인 허용 일수 (0이면 항상 온라인 필요)';

-- ===============================
-- 17-1) 라이선스 플랜 기능/권한: license_plan_entitlements
-- ===============================
CREATE TABLE license_plan_entitlements (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    plan_id         UUID NOT NULL,
    entitlement_key VARCHAR(100) NOT NULL,              -- 기능 키 (ex: "core-simulation", "export-csv")
    CONSTRAINT fk_plan_entitlement_plan FOREIGN KEY (plan_id) REFERENCES license_plans(id) ON DELETE CASCADE
);

COMMENT ON TABLE license_plan_entitlements IS '플랜별 활성화 기능 목록';
COMMENT ON COLUMN license_plan_entitlements.entitlement_key IS '기능 식별자 (core-simulation, advanced-visualization 등)';

-- ===============================
-- 18) 라이선스: licenses
-- ===============================
CREATE TABLE licenses (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_type          VARCHAR(20) NOT NULL,           -- 'USER', 'ORG'
    owner_id            UUID NOT NULL,                  -- users.id 또는 orgs.id (UUID로 참조)
    product_id          UUID NOT NULL,                  -- 제품 ID (UUID)
    plan_id             UUID NULL,                      -- 요금제 ID (선택)
    license_type        VARCHAR(20) NOT NULL,           -- 'TRIAL', 'SUBSCRIPTION', 'PERPETUAL'
    usage_category      VARCHAR(30) NOT NULL,           -- 'COMMERCIAL', 'RESEARCH_NON_COMMERCIAL', 'EDUCATION', 'INTERNAL_EVAL'
    status              VARCHAR(20) NOT NULL,           -- 'PENDING', 'ACTIVE', 'EXPIRED_GRACE', 'EXPIRED_HARD', 'SUSPENDED', 'REVOKED'
    issued_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valid_from          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valid_until         TIMESTAMP NULL,                 -- Perpetual인 경우 NULL
    policy_snapshot     JSONB NULL,                     -- 정책 스냅샷 (maxActivations, gracePeriodDays 등)
    license_key         VARCHAR(50) UNIQUE,             -- 외부 노출용 시리얼 키
    source_order_id     UUID NULL,                      -- 주문 ID (Billing 연동)
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE licenses IS '라이선스 정보 (Licensing BC Aggregate Root)';
COMMENT ON COLUMN licenses.owner_type IS '소유자 유형: USER(개인), ORG(조직)';
COMMENT ON COLUMN licenses.usage_category IS '사용 용도: 상업용, 연구용, 교육용, 내부평가용';
COMMENT ON COLUMN licenses.policy_snapshot IS '발급 시점의 정책 스냅샷 (JSON)';

-- ===============================
-- 18) 라이선스 활성화: license_activations
-- ===============================
CREATE TABLE license_activations (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    license_id              UUID NOT NULL,
    device_fingerprint      VARCHAR(255) NOT NULL,      -- 기기 식별 해시
    status                  VARCHAR(20) NOT NULL,       -- 'ACTIVE', 'STALE', 'DEACTIVATED', 'EXPIRED'
    activated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    client_version          VARCHAR(50) NULL,
    client_os               VARCHAR(100) NULL,
    last_ip                 VARCHAR(45) NULL,           -- IPv6 지원
    offline_token           VARCHAR(2000) NULL,         -- 오프라인 사용 토큰
    offline_token_expires_at TIMESTAMP NULL,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_activation_license FOREIGN KEY (license_id) REFERENCES licenses(id) ON DELETE CASCADE
);

COMMENT ON TABLE license_activations IS '기기 활성화 정보 (라이선스별 기기 슬롯)';
COMMENT ON COLUMN license_activations.device_fingerprint IS 'HW ID, OS 등을 조합한 기기 식별 해시';
COMMENT ON COLUMN license_activations.offline_token IS '오프라인 환경용 서명된 토큰';

-- ===============================
-- 19) 오프라인 토큰 무효화 목록: revoked_offline_tokens
-- ===============================
CREATE TABLE revoked_offline_tokens (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    license_id          UUID NOT NULL,
    activation_id       UUID NULL,
    device_fingerprint  VARCHAR(255) NULL,
    token_hash          VARCHAR(255) NOT NULL,          -- 무효화된 토큰의 해시
    revoked_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason              VARCHAR(255) NULL,
    CONSTRAINT fk_revoked_token_license FOREIGN KEY (license_id) REFERENCES licenses(id) ON DELETE CASCADE
);

COMMENT ON TABLE revoked_offline_tokens IS '무효화된 오프라인 토큰 목록 (탈취 대응)';

-- ===============================
-- 라이선싱 인덱스
-- ===============================

-- license_plans
CREATE UNIQUE INDEX idx_license_plans_code ON license_plans(code) WHERE is_deleted = FALSE;
CREATE INDEX idx_license_plans_product ON license_plans(product_id);
CREATE INDEX idx_license_plans_active ON license_plans(is_active) WHERE is_deleted = FALSE;

-- license_plan_entitlements
CREATE UNIQUE INDEX idx_plan_entitlements_unique ON license_plan_entitlements(plan_id, entitlement_key);
CREATE INDEX idx_plan_entitlements_plan ON license_plan_entitlements(plan_id);

-- licenses
CREATE INDEX idx_licenses_owner ON licenses(owner_type, owner_id);
CREATE INDEX idx_licenses_product ON licenses(product_id);
CREATE INDEX idx_licenses_status ON licenses(status);
CREATE UNIQUE INDEX idx_licenses_key ON licenses(license_key);
CREATE INDEX idx_licenses_source_order ON licenses(source_order_id);
CREATE INDEX idx_licenses_valid_until ON licenses(valid_until) WHERE valid_until IS NOT NULL;

-- license_activations
CREATE INDEX idx_activations_license ON license_activations(license_id);
CREATE INDEX idx_activations_device ON license_activations(license_id, device_fingerprint);
CREATE INDEX idx_activations_status ON license_activations(status);
CREATE INDEX idx_activations_last_seen ON license_activations(last_seen_at);

-- revoked_offline_tokens
CREATE INDEX idx_revoked_tokens_license ON revoked_offline_tokens(license_id);
CREATE INDEX idx_revoked_tokens_hash ON revoked_offline_tokens(token_hash);

-- ===============================
-- 라이선싱 updated_at 트리거
-- ===============================
CREATE TRIGGER update_license_plans_updated_at BEFORE UPDATE ON license_plans
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_licenses_updated_at BEFORE UPDATE ON licenses
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_license_activations_updated_at BEFORE UPDATE ON license_activations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- 초기화 완료 메시지
DO $$
BEGIN
    RAISE NOTICE 'Database initialization completed successfully!';
END $$;
