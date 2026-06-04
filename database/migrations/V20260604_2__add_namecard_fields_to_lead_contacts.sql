-- MDP-549: lead_contacts 명함 임포트 양식에 맞춰 5개 컬럼 추가
-- (부서·근무지 주소·근무처 전화·근무처 팩스·휴대폰)

ALTER TABLE lead_contacts ADD COLUMN IF NOT EXISTS department    VARCHAR(100);
ALTER TABLE lead_contacts ADD COLUMN IF NOT EXISTS address       TEXT;
ALTER TABLE lead_contacts ADD COLUMN IF NOT EXISTS work_phone    VARCHAR(50);
ALTER TABLE lead_contacts ADD COLUMN IF NOT EXISTS work_fax      VARCHAR(50);
ALTER TABLE lead_contacts ADD COLUMN IF NOT EXISTS mobile_phone  VARCHAR(50);

COMMENT ON COLUMN lead_contacts.department   IS '부서';
COMMENT ON COLUMN lead_contacts.address      IS '근무지 주소 번지';
COMMENT ON COLUMN lead_contacts.work_phone   IS '근무처 전화';
COMMENT ON COLUMN lead_contacts.work_fax     IS '근무처 팩스';
COMMENT ON COLUMN lead_contacts.mobile_phone IS '휴대폰';
