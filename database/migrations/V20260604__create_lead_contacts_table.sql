-- MDP-549: 회원가입 없이 외부(전시회·세미나·박람회 등)에서 수집한 메일링 컨택 관리 테이블

CREATE TABLE IF NOT EXISTS lead_contacts (
    id                    BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    email                 VARCHAR(255) NOT NULL,
    contact_name          VARCHAR(100),
    company_name          VARCHAR(100),
    role                  VARCHAR(100),
    source_event          VARCHAR(200),
    source_date           DATE,
    collected_by          VARCHAR(100),
    consent_method        VARCHAR(50),
    consent_date          DATE,
    consent_evidence      TEXT,
    opt_in_marketing      BOOLEAN NOT NULL DEFAULT FALSE,
    opt_in_transactional  BOOLEAN NOT NULL DEFAULT TRUE,
    tags                  VARCHAR(500),
    notes                 TEXT,
    unsubscribe_token     UUID NOT NULL DEFAULT gen_random_uuid(),
    unsubscribed_at       TIMESTAMP NULL,
    unsubscribe_reason    VARCHAR(500),
    created_by            UUID NOT NULL,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_lead_contacts_email UNIQUE (email),
    CONSTRAINT uq_lead_contacts_unsubscribe_token UNIQUE (unsubscribe_token)
);

CREATE INDEX IF NOT EXISTS idx_lead_contacts_company ON lead_contacts(company_name);
CREATE INDEX IF NOT EXISTS idx_lead_contacts_unsubscribed_at ON lead_contacts(unsubscribed_at);
CREATE INDEX IF NOT EXISTS idx_lead_contacts_source_event ON lead_contacts(source_event);

COMMENT ON TABLE lead_contacts IS '회원가입 없이 외부에서 수집한 메일링 컨택 (전시회·세미나·박람회 등 B2B 영업 수집)';
COMMENT ON COLUMN lead_contacts.email IS '수신자 이메일 (UNIQUE)';
COMMENT ON COLUMN lead_contacts.contact_name IS '담당자 이름';
COMMENT ON COLUMN lead_contacts.company_name IS '소속 회사';
COMMENT ON COLUMN lead_contacts.role IS '직책/직급';
COMMENT ON COLUMN lead_contacts.source_event IS '수집 행사 (예: 2026 소방안전 전시회)';
COMMENT ON COLUMN lead_contacts.source_date IS '수집 일자';
COMMENT ON COLUMN lead_contacts.collected_by IS '수집한 직원 이름';
COMMENT ON COLUMN lead_contacts.consent_method IS '동의 방식: business_card / booth_signup / verbal / web_form / import';
COMMENT ON COLUMN lead_contacts.consent_date IS '동의 받은 날짜';
COMMENT ON COLUMN lead_contacts.consent_evidence IS '동의 증빙 자유 텍스트 (명함 파일명, 부스 사인업 URL 등)';
COMMENT ON COLUMN lead_contacts.opt_in_marketing IS '광고성 메일 수신 동의 (정보통신망법 50조)';
COMMENT ON COLUMN lead_contacts.opt_in_transactional IS '안내성/거래 메일 수신 동의';
COMMENT ON COLUMN lead_contacts.tags IS '태그 쉼표 구분 (예: fire_safety, hospital, 2026_kfse)';
COMMENT ON COLUMN lead_contacts.notes IS '자유 메모';
COMMENT ON COLUMN lead_contacts.unsubscribe_token IS '1-Click 해지 토큰 (메일 본문 링크에 삽입)';
COMMENT ON COLUMN lead_contacts.unsubscribed_at IS 'NULL=구독 중, NOT NULL=해지된 시각';
COMMENT ON COLUMN lead_contacts.unsubscribe_reason IS '해지 사유 (선택)';
COMMENT ON COLUMN lead_contacts.created_by IS '등록한 관리자 user.id';
