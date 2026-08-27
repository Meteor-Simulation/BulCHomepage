-- MDP-772: 마케팅 수신 동의 상태 3단계화
--
-- 기존에는 marketing_agreed BOOLEAN 하나뿐이라 "거절함(false)"과 "아직 안 물어봄(false)"이
-- 같은 값이었다. 그래서 거절해도 로그인할 때마다 동의 팝업이 다시 떴다.
-- (프론트가 localStorage 90일 표식으로 버티던 탓에 브라우저 데이터 삭제·다른 기기에서 재노출)
--
-- 값: Y=동의(발송 대상), N=거절(발송 제외, 팝업 안 뜸), P=미선택(발송 제외, 팝업 노출)
-- A/U/N 대신 Y/N/P 를 쓴 이유: U(Unsubscribed)가 Undecided 로, N(None)이 No 로 읽혀
-- 의미가 정확히 뒤집힐 수 있기 때문. 발송 대상을 가르는 값이라 오독 여지를 없앤다.

-- VARCHAR(1) 이어야 한다. CHAR(1) 은 PostgreSQL 에서 bpchar 로 잡혀
-- JPA @Column(length=1) 이 기대하는 varchar(1) 과 달라 스키마 검증에 실패한다.
ALTER TABLE users ADD COLUMN IF NOT EXISTS marketing_consent VARCHAR(1) NOT NULL DEFAULT 'P';

COMMENT ON COLUMN users.marketing_consent IS '광고성 수신 상태 — Y:동의, N:거절, P:미선택';

-- 기존 동의자만 Y 로 이관한다.
-- 나머지는 거절/미선택 구분이 불가능하므로 안전하게 P(미선택)로 두고, 팝업에서 한 번 더 받는다.
UPDATE users SET marketing_consent = 'Y' WHERE marketing_agreed = TRUE;

-- marketing_agreed 는 롤백 대비로 남겨둔다.
-- ddl-auto: validate 는 엔티티에 없는 여분 컬럼을 문제 삼지 않으므로 안정화 후 별도 정리.

-- 이미 CHAR(1) 로 만든 환경 정정용 (운영 2026-08-27 적용).
-- CHAR 로 생성되면 백엔드가 스키마 검증 실패로 기동하지 못한다.
ALTER TABLE users ALTER COLUMN marketing_consent TYPE VARCHAR(1);
