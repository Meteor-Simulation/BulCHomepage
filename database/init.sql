-- PostgreSQL 초기화 스크립트
-- 이 파일은 컨테이너 최초 실행 시 자동 실행됩니다.

-- 확장 기능 설치 (필요 시)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 초기화 완료 메시지
DO $$
BEGIN
    RAISE NOTICE 'Database initialization completed successfully!';
END $$;
