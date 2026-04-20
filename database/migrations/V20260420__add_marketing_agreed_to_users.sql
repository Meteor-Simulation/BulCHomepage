-- 광고성 정보 수신 동의 컬럼 추가
ALTER TABLE users ADD COLUMN IF NOT EXISTS marketing_agreed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS marketing_agreed_at TIMESTAMP NULL;

COMMENT ON COLUMN users.marketing_agreed IS '광고성 정보 수신 동의 여부';
COMMENT ON COLUMN users.marketing_agreed_at IS '광고성 정보 수신 동의 시점';
