-- =========================================================
-- MDP-509: 팝업 시스템 - popups 테이블 생성
-- 생성: 2026-05-26
-- =========================================================

CREATE TABLE IF NOT EXISTS popups (
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    type            VARCHAR(20) NOT NULL,
    title           VARCHAR(100) NOT NULL,
    content         TEXT NOT NULL,
    image_url       VARCHAR(500),
    triggers        VARCHAR(100) NOT NULL,
    close_options   VARCHAR(100) NOT NULL,
    priority        INT NOT NULL DEFAULT 0,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    start_at        TIMESTAMP,
    end_at          TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_popup_type CHECK (type IN ('IMAGE_TEXT', 'TEXT_ONLY'))
);

CREATE INDEX IF NOT EXISTS idx_popups_active ON popups(is_active);
CREATE INDEX IF NOT EXISTS idx_popups_priority ON popups(priority);
CREATE INDEX IF NOT EXISTS idx_popups_schedule ON popups(start_at, end_at);

COMMENT ON TABLE  popups               IS '관리자 등록 팝업 (홈 진입/로그인 직후 표시)';
COMMENT ON COLUMN popups.type          IS '콘텐츠 타입: IMAGE_TEXT(이미지+제목+본문) / TEXT_ONLY(제목+본문)';
COMMENT ON COLUMN popups.triggers      IS '표시 트리거 CSV: HOME_ENTRY,POST_LOGIN';
COMMENT ON COLUMN popups.close_options IS '허용 닫기 옵션 CSV: HIDE_TODAY,HIDE_FOREVER,CLOSE';
COMMENT ON COLUMN popups.priority      IS '표시 우선순위 (낮을수록 먼저 표시)';
COMMENT ON COLUMN popups.start_at      IS '노출 시작 (NULL이면 즉시)';
COMMENT ON COLUMN popups.end_at        IS '노출 종료 (NULL이면 무기한)';
