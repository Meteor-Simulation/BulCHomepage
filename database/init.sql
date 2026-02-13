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
DROP TABLE IF EXISTS refresh_tokens CASCADE;
DROP TABLE IF EXISTS token_blacklist CASCADE;
DROP TABLE IF EXISTS refresh_tokens CASCADE;
DROP TABLE IF EXISTS password_reset_tokens CASCADE;
DROP TABLE IF EXISTS email_verifications CASCADE;
DROP TABLE IF EXISTS payment_details CASCADE;
DROP TABLE IF EXISTS payments CASCADE;
DROP TABLE IF EXISTS subscriptions CASCADE;
DROP TABLE IF EXISTS promotions CASCADE;
DROP TABLE IF EXISTS price_plans CASCADE;
DROP TABLE IF EXISTS products CASCADE;
DROP TABLE IF EXISTS user_social_accounts CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS countries CASCADE;
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
-- 2. countries (국가 테이블)
-- =========================================================
CREATE TABLE countries (
    code            VARCHAR(10) PRIMARY KEY,
    name            VARCHAR(50) NOT NULL,
    currency        VARCHAR(10) NOT NULL DEFAULT 'USD',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE countries IS '국가 테이블 - 지원 국가 목록 (통화, 언어 설정 등에 사용)';
COMMENT ON COLUMN countries.code IS '국가 코드 (KR, US, JP 등)';
COMMENT ON COLUMN countries.name IS '국가명';
COMMENT ON COLUMN countries.currency IS '결제 통화 (KRW, USD)';

-- 기본 국가 데이터
INSERT INTO countries (code, name, currency) VALUES
    ('KR', '대한민국', 'KRW'),
    ('CN', '중국', 'USD'),
    ('JP', '일본', 'USD'),
    ('US', '미국', 'USD'),
    ('EU', '유럽', 'USD'),
    ('RU', '러시아', 'USD');

-- =========================================================
-- 3. users (유저 테이블)
-- =========================================================
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NULL,
    roles_code      VARCHAR(10) NOT NULL DEFAULT '002',
    name            VARCHAR(100) NULL,
    phone           VARCHAR(20) NULL,
    country_code    VARCHAR(10) NULL DEFAULT 'KR',
    language_code   VARCHAR(5) NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    deactivated_at  TIMESTAMP NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_users_role FOREIGN KEY (roles_code) REFERENCES user_roles(code),
    CONSTRAINT fk_users_country FOREIGN KEY (country_code) REFERENCES countries(code)
);

CREATE INDEX idx_users_is_active ON users(is_active);
CREATE INDEX idx_users_email ON users(email);

COMMENT ON TABLE users IS '유저 테이블 - 사용자 기본 정보';
COMMENT ON COLUMN users.id IS 'UUID 기본키';
COMMENT ON COLUMN users.email IS '이메일 (UNIQUE, 로그인 ID)';
COMMENT ON COLUMN users.password_hash IS '비밀번호 해시 (소셜 로그인 사용자는 NULL)';
COMMENT ON COLUMN users.roles_code IS '역할 코드 (000:관리자, 001:매니저, 002:일반)';
COMMENT ON COLUMN users.name IS '이름 (결제 시 입력)';
COMMENT ON COLUMN users.phone IS '전화번호 (결제 시 입력)';
COMMENT ON COLUMN users.country_code IS '국가 코드 (FK → countries.code)';
COMMENT ON COLUMN users.language_code IS '언어 코드 (ko/en, NULL이면 IP 감지 사용)';
COMMENT ON COLUMN users.is_active IS '계정 활성화 상태 (기본: true)';
COMMENT ON COLUMN users.deactivated_at IS '계정 비활성화 시점';

-- 기본 계정 (비밀번호: meteor2025!)
-- 관리자 계정
INSERT INTO users (id, email, password_hash, roles_code, name) VALUES
    ('00000000-0000-0000-0000-000000000001', 'meteor@msimul.com', '$2a$10$xbdkjM61f.0H67Ag3wOO0enQf/VbKtEFYlgWAmcqlnIedcZFrKpP6', '000', '메테오'),
    ('00000000-0000-0000-0000-000000000002', 'simul@msimul.com', '$2a$10$xbdkjM61f.0H67Ag3wOO0enQf/VbKtEFYlgWAmcqlnIedcZFrKpP6', '000', '김지태');

-- 매니저 계정
INSERT INTO users (id, email, password_hash, roles_code, name) VALUES
    ('00000000-0000-0000-0000-000000000003', 'juwon@msimul.com', '$2a$10$xbdkjM61f.0H67Ag3wOO0enQf/VbKtEFYlgWAmcqlnIedcZFrKpP6', '001', '강주원'),
    ('00000000-0000-0000-0000-000000000004', 'kjh4387@msimul.com', '$2a$10$xbdkjM61f.0H67Ag3wOO0enQf/VbKtEFYlgWAmcqlnIedcZFrKpP6', '001', '김자현'),
    ('00000000-0000-0000-0000-000000000005', 'laplace@msimul.com', '$2a$10$xbdkjM61f.0H67Ag3wOO0enQf/VbKtEFYlgWAmcqlnIedcZFrKpP6', '001', '황지인'),
    ('00000000-0000-0000-0000-000000000006', 'qogkstj02@msimul.com', '$2a$10$xbdkjM61f.0H67Ag3wOO0enQf/VbKtEFYlgWAmcqlnIedcZFrKpP6', '001', '배한서');

-- 일반 사용자 계정
INSERT INTO users (id, email, password_hash, roles_code, name, phone) VALUES
    ('00000000-0000-0000-0000-000000000007', 'wndnjs6455@naver.com', '$2a$10$qmXYi4vpbKx34mCAwErpb.88ybQarAoIK4iec5PlBN107EeBoxEWy', '002', '강주원', '010-2366-6455');

-- =========================================================
-- 3-1. user_social_accounts (소셜 계정 연동 테이블)
-- =========================================================
CREATE TABLE user_social_accounts (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id         UUID NOT NULL,
    provider        VARCHAR(20) NOT NULL,
    provider_id     VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_social_accounts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_social_accounts_provider UNIQUE (provider, provider_id)
);

COMMENT ON TABLE user_social_accounts IS '소셜 계정 연동 테이블 - OAuth 제공자별 사용자 ID 저장';
COMMENT ON COLUMN user_social_accounts.user_id IS '사용자 UUID (FK → users.id)';
COMMENT ON COLUMN user_social_accounts.provider IS '소셜 로그인 제공자 (NAVER, KAKAO, GOOGLE)';
COMMENT ON COLUMN user_social_accounts.provider_id IS '소셜 플랫폼에서 제공하는 사용자 고유 ID';

CREATE INDEX idx_social_accounts_user ON user_social_accounts(user_id);
CREATE INDEX idx_social_accounts_provider ON user_social_accounts(provider, provider_id);

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
-- 3-1. password_reset_tokens (비밀번호 재설정 토큰 테이블)
-- =========================================================
CREATE TABLE password_reset_tokens (
    id                  BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    email               VARCHAR(255) NOT NULL UNIQUE,
    reset_code          VARCHAR(6) NOT NULL,
    expires_at          TIMESTAMP NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE password_reset_tokens IS '비밀번호 재설정 토큰 테이블 - 비밀번호 재설정 인증 코드 관리';
COMMENT ON COLUMN password_reset_tokens.email IS '재설정할 계정 이메일 (UNIQUE - 이메일당 1개 코드)';
COMMENT ON COLUMN password_reset_tokens.reset_code IS '6자리 영숫자 인증 코드';
COMMENT ON COLUMN password_reset_tokens.expires_at IS '코드 만료 시간 (기본 10분)';

-- =========================================================
-- 4. products (상품 종류 테이블)
-- =========================================================
CREATE TABLE products (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code            VARCHAR(3) NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    description     TEXT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE products IS '상품 종류 테이블 - 판매 상품 정의';
COMMENT ON COLUMN products.id IS 'UUID 기본키';
COMMENT ON COLUMN products.code IS '상품 코드 (000~999), UNIQUE';

-- 기본 상품 데이터 (deterministic UUID for compatibility)
INSERT INTO products (id, code, name, description) VALUES
    ('a0000000-0000-0000-0000-000000000001', '001', 'BUL:C', '화재시뮬레이션');

-- =========================================================
-- 5. price_plans (상품 가격 테이블)
-- =========================================================
CREATE TABLE price_plans (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    product_code    VARCHAR(3) NOT NULL,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(100) NULL,
    price           DECIMAL(18,2) NOT NULL,
    currency        VARCHAR(10) NOT NULL DEFAULT 'KRW',
    license_plan_id UUID NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_price_plans_product FOREIGN KEY (product_code) REFERENCES products(code) ON UPDATE CASCADE
);

COMMENT ON TABLE price_plans IS '상품 가격 테이블 - 상품별 요금제 정의';
COMMENT ON COLUMN price_plans.description IS '요금제 설명 (예: 1년, 6개월 등)';
COMMENT ON COLUMN price_plans.license_plan_id IS '연결된 라이선스 플랜 ID (결제 완료 시 해당 플랜으로 라이선스 발급)';

-- 기본 요금제 데이터 (license_plan_id는 license_plans 테이블 생성 후 업데이트)
INSERT INTO price_plans (product_code, name, description, price, currency) VALUES
    ('001', 'BUL:C PRO', '1년/365일', 4000000, 'KRW'),
    ('001', 'BUL:C PRO', '1년/365일', 2700, 'USD'),
    ('001', 'BUL:C 3D Premium', '1년/365일', 6000000, 'KRW'),
    ('001', 'BUL:C 3D Premium', '1년/365일', 4000, 'USD');

-- price_plans와 license_plans 연결 (별도 UPDATE 쿼리로 처리 - 테이블 생성 순서 때문)

-- =========================================================
-- 5-2. promotions (프로모션/쿠폰 테이블)
-- =========================================================
CREATE TABLE promotions (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    code            VARCHAR(50) NOT NULL UNIQUE,
    name            VARCHAR(100) NOT NULL,
    discount_type   INTEGER NOT NULL,
    discount_value  DECIMAL(18,2) NOT NULL,
    product_code    VARCHAR(3) NULL,
    usage_limit     INTEGER NULL,
    usage_count     INTEGER NOT NULL DEFAULT 0,
    valid_from      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valid_until     TIMESTAMP NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_promotions_product FOREIGN KEY (product_code) REFERENCES products(code) ON UPDATE CASCADE
);

COMMENT ON TABLE promotions IS '프로모션/쿠폰 테이블 - 할인 쿠폰 및 프로모션 관리';
COMMENT ON COLUMN promotions.code IS '프로모션 코드 (사용자 입력용)';
COMMENT ON COLUMN promotions.discount_type IS '할인 유형 (퍼센트 값, 예: 10 = 10% 할인)';
COMMENT ON COLUMN promotions.discount_value IS '할인 값';
COMMENT ON COLUMN promotions.product_code IS '특정 상품에만 적용 (NULL이면 전체 상품)';
COMMENT ON COLUMN promotions.usage_limit IS '사용 횟수 제한 (NULL이면 무제한)';
COMMENT ON COLUMN promotions.usage_count IS '현재까지 사용된 횟수';

-- =========================================================
-- 6. subscriptions (유저 구독 관리 테이블)
-- =========================================================
CREATE TABLE subscriptions (
    id                  BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id             UUID NULL,
    product_code        VARCHAR(3) NOT NULL,
    price_plan_id       BIGINT NOT NULL,
    status              VARCHAR(1) NOT NULL DEFAULT 'A',
    start_date          TIMESTAMP NOT NULL,
    end_date            TIMESTAMP NOT NULL,
    auto_renew          BOOLEAN NOT NULL DEFAULT FALSE,
    billing_key_id      BIGINT NULL,
    next_billing_date   TIMESTAMP NULL,
    billing_cycle       VARCHAR(20) NULL DEFAULT 'YEARLY',
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_subscriptions_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_subscriptions_product FOREIGN KEY (product_code) REFERENCES products(code) ON UPDATE CASCADE,
    CONSTRAINT fk_subscriptions_price_plan FOREIGN KEY (price_plan_id) REFERENCES price_plans(id)
    -- fk_subscriptions_billing_key는 billing_keys 테이블 생성 후 ALTER TABLE로 추가됨
);

COMMENT ON TABLE subscriptions IS '유저 구독 관리 테이블 - 사용자의 구독 현황';
COMMENT ON COLUMN subscriptions.user_id IS '사용자 UUID (FK → users.id)';
COMMENT ON COLUMN subscriptions.status IS 'A: 활성(Active), E: 만료(Expired), C: 취소(Canceled)';
COMMENT ON COLUMN subscriptions.billing_key_id IS '자동결제에 사용할 빌링키 ID';
COMMENT ON COLUMN subscriptions.next_billing_date IS '다음 결제 예정일';
COMMENT ON COLUMN subscriptions.billing_cycle IS 'MONTHLY, QUARTERLY, YEARLY';

-- =========================================================
-- 6-1. billing_keys (빌링키 테이블 - 자동결제용 카드 정보)
-- =========================================================
CREATE TABLE billing_keys (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id         UUID NOT NULL,
    billing_key     VARCHAR(255) NOT NULL,
    customer_key    VARCHAR(255) NOT NULL,
    card_company    VARCHAR(50) NULL,
    card_number     VARCHAR(20) NULL,
    card_type       VARCHAR(20) NULL,
    owner_type      VARCHAR(20) NULL,
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_billing_keys_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

COMMENT ON TABLE billing_keys IS '빌링키 테이블 - 자동결제용 카드 정보 저장';
COMMENT ON COLUMN billing_keys.user_id IS '사용자 UUID (FK → users.id)';
COMMENT ON COLUMN billing_keys.billing_key IS '토스페이먼츠 빌링키';
COMMENT ON COLUMN billing_keys.customer_key IS '고객 식별키 (UUID)';
COMMENT ON COLUMN billing_keys.card_company IS '카드사명';
COMMENT ON COLUMN billing_keys.card_number IS '마스킹된 카드번호 (앞6자리****뒤4자리)';
COMMENT ON COLUMN billing_keys.is_default IS '기본 결제 수단 여부';

CREATE INDEX idx_billing_keys_user_id ON billing_keys(user_id);
CREATE INDEX idx_billing_keys_is_active ON billing_keys(is_active);

-- subscriptions 테이블에 billing_keys 외래키 추가
ALTER TABLE subscriptions ADD CONSTRAINT fk_subscriptions_billing_key
    FOREIGN KEY (billing_key_id) REFERENCES billing_keys(id);

-- =========================================================
-- 6-2. subscription_payments (구독 결제 이력)
-- =========================================================
CREATE TABLE subscription_payments (
    id                  BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    subscription_id     BIGINT NOT NULL,
    billing_key_id      BIGINT NULL,
    payment_key         VARCHAR(255) NULL,
    order_id            VARCHAR(255) NOT NULL,
    amount              DECIMAL(15, 2) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    billing_date        DATE NOT NULL,
    paid_at             TIMESTAMP NULL,
    failure_reason      TEXT NULL,
    retry_count         INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_subscription_payments_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions(id),
    CONSTRAINT fk_subscription_payments_billing_key FOREIGN KEY (billing_key_id) REFERENCES billing_keys(id)
);

COMMENT ON TABLE subscription_payments IS '구독 결제 이력 테이블';
COMMENT ON COLUMN subscription_payments.status IS 'PENDING, SUCCESS, FAILED, CANCELED';
COMMENT ON COLUMN subscription_payments.retry_count IS '결제 재시도 횟수';

CREATE INDEX idx_subscription_payments_subscription_id ON subscription_payments(subscription_id);
CREATE INDEX idx_subscription_payments_billing_date ON subscription_payments(billing_date);
CREATE INDEX idx_subscription_payments_status ON subscription_payments(status);

-- =========================================================
-- 7. payments (결제 테이블)
-- =========================================================
CREATE TABLE payments (
    id                  BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id             UUID NULL,
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

    CONSTRAINT fk_payments_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_payments_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions(id),
    CONSTRAINT fk_payments_price_plan FOREIGN KEY (price_plan_id) REFERENCES price_plans(id)
);

COMMENT ON TABLE payments IS '결제 테이블 - 결제/환불 정보 관리';
COMMENT ON COLUMN payments.user_id IS '사용자 UUID (FK → users.id)';
COMMENT ON COLUMN payments.user_email IS '결제 시점 이메일 스냅샷 (FK 아님)';
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
    -- 카드 결제 상세 정보
    card_company        VARCHAR(50) NULL,
    card_number         VARCHAR(50) NULL,
    installment_months  INT NULL,
    approve_no          VARCHAR(50) NULL,
    -- 간편결제 제공자
    easy_pay_provider   VARCHAR(50) NULL,
    -- 가상계좌/계좌이체 상세 정보
    bank_code           VARCHAR(20) NULL,
    bank_name           VARCHAR(50) NULL,
    account_number      VARCHAR(50) NULL,
    due_date            TIMESTAMP NULL,
    depositor_name      VARCHAR(100) NULL,
    settlement_status   VARCHAR(20) NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_payment_details_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
);

COMMENT ON TABLE payment_details IS '결제 상세 테이블 - PG사 연동 정보';
COMMENT ON COLUMN payment_details.order_id IS '토스페이먼츠 주문 ID';
COMMENT ON COLUMN payment_details.payment_key IS '토스페이먼츠 결제 키';
COMMENT ON COLUMN payment_details.card_company IS '카드사명';
COMMENT ON COLUMN payment_details.card_number IS '마스킹된 카드번호';
COMMENT ON COLUMN payment_details.installment_months IS '할부 개월수 (0: 일시불)';
COMMENT ON COLUMN payment_details.approve_no IS '카드 승인번호';
COMMENT ON COLUMN payment_details.easy_pay_provider IS '간편결제 제공자 (토스페이, 네이버페이, 카카오페이 등)';
COMMENT ON COLUMN payment_details.bank_code IS '은행 코드 (가상계좌/계좌이체)';
COMMENT ON COLUMN payment_details.bank_name IS '은행명';
COMMENT ON COLUMN payment_details.account_number IS '가상계좌 번호';
COMMENT ON COLUMN payment_details.due_date IS '입금 기한 (가상계좌)';
COMMENT ON COLUMN payment_details.depositor_name IS '입금자명 (가상계좌)';
COMMENT ON COLUMN payment_details.settlement_status IS '정산 상태 (계좌이체)';

-- =========================================================
-- 9. activity_logs (활동 로그 테이블)
-- =========================================================
CREATE TABLE activity_logs (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id         UUID NULL,
    action          VARCHAR(50) NOT NULL,
    target_type     VARCHAR(50) NULL,
    target_id       BIGINT NULL,
    description     TEXT NULL,
    ip_address      VARCHAR(50) NULL,
    user_agent      TEXT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_activity_logs_user FOREIGN KEY (user_id) REFERENCES users(id)
);

COMMENT ON TABLE activity_logs IS '활동 로그 테이블 - 로그인, 구매, 환불 등 기록';
COMMENT ON COLUMN activity_logs.user_id IS '사용자 UUID (FK → users.id)';
COMMENT ON COLUMN activity_logs.action IS 'login, logout, purchase, refund, subscription_start, subscription_cancel 등';

-- =========================================================
-- 9-1. token_blacklist (토큰 블랙리스트 테이블)
-- =========================================================
CREATE TABLE token_blacklist (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    token           VARCHAR(500) NOT NULL,
    user_id         UUID NOT NULL,
    expires_at      TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE token_blacklist IS '토큰 블랙리스트 테이블 - 로그아웃된 JWT 토큰 관리';
COMMENT ON COLUMN token_blacklist.token IS '블랙리스트에 등록된 JWT 토큰';
COMMENT ON COLUMN token_blacklist.user_id IS '토큰 소유자 UUID';
COMMENT ON COLUMN token_blacklist.expires_at IS '토큰 만료 시간 (만료 후 자동 삭제)';

CREATE INDEX idx_token_blacklist_token ON token_blacklist(token);
CREATE INDEX idx_token_blacklist_expires_at ON token_blacklist(expires_at);

-- =========================================================
-- 9-2. refresh_tokens (리프레시 토큰 테이블)
-- =========================================================
CREATE TABLE refresh_tokens (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id         UUID NOT NULL,
    token           VARCHAR(500) NOT NULL,
    device_id       VARCHAR(255) NULL,
    device_info     VARCHAR(500) NULL,
    expires_at      TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at    TIMESTAMP NULL,

    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

COMMENT ON TABLE refresh_tokens IS '리프레시 토큰 테이블 - RTR (Refresh Token Rotation) 지원';
COMMENT ON COLUMN refresh_tokens.user_id IS '사용자 UUID';
COMMENT ON COLUMN refresh_tokens.token IS '현재 유효한 리프레시 토큰';
COMMENT ON COLUMN refresh_tokens.device_id IS '디바이스 식별자 (멀티 디바이스 지원)';
COMMENT ON COLUMN refresh_tokens.device_info IS '디바이스 정보 (User-Agent 등)';
COMMENT ON COLUMN refresh_tokens.expires_at IS '토큰 만료 시간';
COMMENT ON COLUMN refresh_tokens.last_used_at IS '마지막 사용 시간';

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_device_id ON refresh_tokens(device_id);

-- =========================================================
-- 10. user_change_logs (유저 정보 변경 로그 테이블)
-- =========================================================
CREATE TABLE user_change_logs (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id         UUID NULL,
    changed_field   VARCHAR(50) NOT NULL,
    old_value       TEXT NULL,
    new_value       TEXT NULL,
    changed_by_id   UUID NULL,
    change_reason   TEXT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE user_change_logs IS '유저 정보 변경 로그 - 사용자 정보 변경 이력';
COMMENT ON COLUMN user_change_logs.user_id IS '변경 대상 사용자 UUID';
COMMENT ON COLUMN user_change_logs.changed_by_id IS '변경 수행자 UUID';

-- =========================================================
-- 11. admin_logs (관리자 로그 테이블)
-- =========================================================
CREATE TABLE admin_logs (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    admin_id        UUID NOT NULL,
    action          VARCHAR(100) NOT NULL,
    target_type     VARCHAR(50) NOT NULL,
    target_id       BIGINT NULL,
    description     TEXT NULL,
    ip_address      VARCHAR(50) NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_admin_logs_admin FOREIGN KEY (admin_id) REFERENCES users(id)
);

COMMENT ON TABLE admin_logs IS '관리자 로그 테이블 - 관리자 작업 이력';
COMMENT ON COLUMN admin_logs.admin_id IS '관리자 UUID (FK → users.id)';

-- =========================================================
-- 12. license_plans (라이선스 플랜 테이블)
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
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_license_plans_product FOREIGN KEY (product_id) REFERENCES products(id)
);

COMMENT ON TABLE license_plans IS '라이선스 플랜/정책 템플릿 (Admin UI에서 관리)';
COMMENT ON COLUMN license_plans.code IS '사람이 읽기 쉬운 식별자, Admin UI에서 선택/표시할 값';
COMMENT ON COLUMN license_plans.duration_days IS '기본 유효기간 (일 단위)';
COMMENT ON COLUMN license_plans.grace_days IS 'EXPIRED_GRACE 상태로 전환 후 유예기간';
COMMENT ON COLUMN license_plans.allow_offline_days IS '오프라인 허용 일수 (0이면 항상 온라인 필요)';

-- =========================================================
-- 13. license_plan_entitlements (플랜 기능 권한 테이블)
-- =========================================================
CREATE TABLE license_plan_entitlements (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    plan_id         UUID NOT NULL,
    entitlement_key VARCHAR(100) NOT NULL,

    CONSTRAINT fk_plan_entitlement_plan FOREIGN KEY (plan_id) REFERENCES license_plans(id) ON DELETE CASCADE
);

COMMENT ON TABLE license_plan_entitlements IS '플랜별 활성화 기능 목록';
COMMENT ON COLUMN license_plan_entitlements.entitlement_key IS '기능 식별자 (core-simulation, advanced-visualization 등)';

-- 기본 라이선스 플랜 데이터
INSERT INTO license_plans (id, product_id, code, name, description, license_type, duration_days, grace_days, max_activations, max_concurrent_sessions, allow_offline_days) VALUES
    ('a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d', uuid_generate_v5(uuid_ns_url(), 'product:001'), 'BULC-PRO-1Y', 'BUL:C PRO 1년', 'BUL:C PRO 버전 1년 구독 라이선스', 'SUBSCRIPTION', 365, 7, 3, 1, 7),
    ('b2c3d4e5-f6a7-4b5c-9d0e-1f2a3b4c5d6e', uuid_generate_v5(uuid_ns_url(), 'product:001'), 'BULC-3D-PREMIUM-1Y', 'BUL:C 3D Premium 1년', 'BUL:C 3D Premium 버전 1년 구독 라이선스', 'SUBSCRIPTION', 365, 7, 3, 1, 7);

-- 라이선스 플랜 기능 권한 데이터
INSERT INTO license_plan_entitlements (id, plan_id, entitlement_key) VALUES
    (uuid_generate_v4(), 'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d', 'core-simulation'),
    (uuid_generate_v4(), 'b2c3d4e5-f6a7-4b5c-9d0e-1f2a3b4c5d6e', 'core-simulation'),
    (uuid_generate_v4(), 'b2c3d4e5-f6a7-4b5c-9d0e-1f2a3b4c5d6e', '3d-visualization');

-- =========================================================
-- 14. licenses (라이선스 테이블)
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
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_licenses_product FOREIGN KEY (product_id) REFERENCES products(id)
);

COMMENT ON TABLE licenses IS '라이선스 정보 (Licensing BC Aggregate Root)';
COMMENT ON COLUMN licenses.owner_type IS '소유자 유형: USER(개인), ORG(조직)';
COMMENT ON COLUMN licenses.usage_category IS '사용 용도: 상업용, 연구용, 교육용, 내부평가용';
COMMENT ON COLUMN licenses.policy_snapshot IS '발급 시점의 정책 스냅샷 (JSON)';

-- =========================================================
-- 15. license_activations (라이선스 활성화 테이블)
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
    device_display_name     VARCHAR(100) NULL,
    deactivated_at          TIMESTAMP NULL,
    deactivated_reason      VARCHAR(50) NULL,
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
-- 16. revoked_offline_tokens (무효화된 오프라인 토큰 테이블)
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

-- password_reset_tokens
CREATE INDEX idx_password_reset_tokens_email ON password_reset_tokens(email);
CREATE INDEX idx_password_reset_tokens_expires_at ON password_reset_tokens(expires_at);

-- products
CREATE INDEX idx_products_is_active ON products(is_active);

-- price_plans
CREATE INDEX idx_price_plans_product_code ON price_plans(product_code);
CREATE INDEX idx_price_plans_is_active ON price_plans(is_active);

-- promotions
CREATE INDEX idx_promotions_code ON promotions(code);
CREATE INDEX idx_promotions_product_code ON promotions(product_code);
CREATE INDEX idx_promotions_is_active ON promotions(is_active);
CREATE INDEX idx_promotions_valid_from ON promotions(valid_from);
CREATE INDEX idx_promotions_valid_until ON promotions(valid_until);

-- subscriptions
CREATE INDEX idx_subscriptions_user_id ON subscriptions(user_id);
CREATE INDEX idx_subscriptions_product_code ON subscriptions(product_code);
CREATE INDEX idx_subscriptions_status ON subscriptions(status);
CREATE INDEX idx_subscriptions_end_date ON subscriptions(end_date);

-- payments
CREATE INDEX idx_payments_user_id ON payments(user_id);
CREATE INDEX idx_payments_subscription_id ON payments(subscription_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_created_at ON payments(created_at);

-- payment_details (payment_id가 PK이므로 별도 인덱스 불필요)
CREATE INDEX idx_payment_details_order_id ON payment_details(order_id);
CREATE INDEX idx_payment_details_payment_key ON payment_details(payment_key);

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

CREATE TRIGGER update_promotions_updated_at BEFORE UPDATE ON promotions
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

-- =========================================================
-- 마지막 단계: price_plans ↔ license_plans 연결
-- (테이블 생성 순서 때문에 맨 마지막에 실행)
-- =========================================================
UPDATE price_plans SET license_plan_id = 'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d' WHERE name = 'BUL:C PRO';
UPDATE price_plans SET license_plan_id = 'b2c3d4e5-f6a7-4b5c-9d0e-1f2a3b4c5d6e' WHERE name = 'BUL:C 3D Premium';
