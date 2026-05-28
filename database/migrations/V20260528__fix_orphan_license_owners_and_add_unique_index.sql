-- =============================================================================
-- 2026-05-28: 라이선스 owner_id 정합성 복구 + USER 중복 구매 차단 unique index 추가
-- =============================================================================
--
-- 배경:
--   PaymentService에서 라이선스 발급 시 owner_id를
--     UUID userId = UUID.nameUUIDFromBytes(userEmail.getBytes(UTF_8));
--   로 계산하던 버그가 있었음. 그러나 controller에서 넘기는 `userEmail`은 사실
--   `authentication.getName()` = UUID 문자열이므로, 위 코드는 "UUID 문자열의
--   바이트를 해시"하여 가짜 v3 UUID를 만들어 owner_id로 저장했음.
--   결과: licenses.owner_id가 users.id와 일치하지 않는 라이선스 다수 발생 →
--   `/me/licenses` 조회 실패, 본 PR의 중복 차단 쿼리도 매칭 실패.
--
--   동일 패턴으로 payments.user_id도 NULL이거나 잘못된 값으로 저장되었을 가능성
--   (savePaymentInfo에서 userRepository.findByEmail(uuidString) → empty → null).
--
-- 본 마이그레이션:
--   1) 임시 pg_temp.name_uuid_v3() 정의 — Java UUID.nameUUIDFromBytes 동등
--   2) orphan license.owner_id를 payments 조인으로 보정
--      - 매칭 키: license.source_order_id = name_uuid_v3(payment_details.order_id)
--      - 실제 user.id: payments.user_email (UUID 문자열) → users.id로 캐스트 매칭
--   3) payments.user_id NULL 보정 (user_email이 valid UUID인 경우)
--   4) 보정 후 남은 orphan / USER 중복 그룹 진단 (RAISE NOTICE/WARNING)
--   5) 중복이 없으면 partial unique index 생성. 중복 있으면 skip + 안내.
--
-- 재실행 안전: 모든 UPDATE는 idempotent. CREATE INDEX는 IF NOT EXISTS.
-- =============================================================================

BEGIN;

-- ----------------------------------------------------------
-- 1) 임시 함수: Java UUID.nameUUIDFromBytes(UTF-8 bytes) 동등 (v3 UUID)
--    pg_temp 스키마에 생성 → 세션 종료 시 자동 제거
--
--    알고리즘 (java.util.UUID#nameUUIDFromBytes):
--      md5 = MD5(input)               -- 16 bytes
--      md5[6] = (md5[6] & 0x0f) | 0x30  -- 버전 3 설정
--      md5[8] = (md5[8] & 0x3f) | 0x80  -- RFC 4122 IETF variant 설정
--      return UUID(md5)
-- ----------------------------------------------------------
CREATE OR REPLACE FUNCTION pg_temp.name_uuid_v3(input TEXT) RETURNS UUID AS $func$
DECLARE
    raw bytea;
    hex TEXT;
BEGIN
    raw := decode(md5(input), 'hex');  -- MD5 16바이트 (bytea)
    raw := set_byte(raw, 6, (get_byte(raw, 6) & 15) | 48);   -- 버전 3
    raw := set_byte(raw, 8, (get_byte(raw, 8) & 63) | 128);  -- IETF variant
    hex := encode(raw, 'hex');
    RETURN (substr(hex, 1, 8)  || '-' ||
            substr(hex, 9, 4)  || '-' ||
            substr(hex, 13, 4) || '-' ||
            substr(hex, 17, 4) || '-' ||
            substr(hex, 21))::UUID;
END;
$func$ LANGUAGE plpgsql IMMUTABLE;

-- ----------------------------------------------------------
-- 2~5) 보정 + 진단 + 인덱스 생성 (DO 블록으로 카운트/조건 처리)
-- ----------------------------------------------------------
DO $migrate$
DECLARE
    recovered_count INT;
    payments_updated INT;
    orphan_count INT;
    dup_group_count INT;
BEGIN
    -- 2) license.owner_id 보정
    WITH recovered AS (
        UPDATE licenses l
        SET owner_id = u.id,
            updated_at = CURRENT_TIMESTAMP
        FROM payment_details pd
        JOIN payments p ON p.id = pd.payment_id
        JOIN users u ON u.id::text = p.user_email
        WHERE l.owner_type = 'USER'
          AND l.source_order_id IS NOT NULL
          AND l.source_order_id = pg_temp.name_uuid_v3(pd.order_id)
          AND NOT EXISTS (SELECT 1 FROM users WHERE id = l.owner_id)
        RETURNING 1
    )
    SELECT COUNT(*) INTO recovered_count FROM recovered;
    RAISE NOTICE '[license-migration] license.owner_id 보정: % 건', recovered_count;

    -- 3) payments.user_id 보정 (NULL & user_email이 valid UUID 인 경우만)
    WITH updated_p AS (
        UPDATE payments p
        SET user_id = u.id,
            updated_at = CURRENT_TIMESTAMP
        FROM users u
        WHERE p.user_id IS NULL
          AND u.id::text = p.user_email
        RETURNING 1
    )
    SELECT COUNT(*) INTO payments_updated FROM updated_p;
    RAISE NOTICE '[license-migration] payments.user_id 보정: % 건', payments_updated;

    -- 4) 남은 orphan 진단
    SELECT COUNT(*) INTO orphan_count
    FROM licenses l
    WHERE l.owner_type = 'USER'
      AND NOT EXISTS (SELECT 1 FROM users u WHERE u.id = l.owner_id);

    IF orphan_count > 0 THEN
        RAISE WARNING '[license-migration] 보정되지 않은 orphan license % 건 — source_order_id 매칭 실패. 수동 검토 필요.', orphan_count;
    ELSE
        RAISE NOTICE '[license-migration] orphan license 없음 OK';
    END IF;

    -- 5) USER 중복 유상 라이선스 그룹 진단
    SELECT COUNT(*) INTO dup_group_count
    FROM (
        SELECT owner_id, product_id
        FROM licenses
        WHERE owner_type = 'USER'
          AND license_type <> 'TRIAL'
          AND status IN ('ACTIVE', 'PENDING')
        GROUP BY owner_id, product_id
        HAVING COUNT(*) > 1
    ) d;

    IF dup_group_count > 0 THEN
        RAISE WARNING
            '[license-migration] USER 중복 ACTIVE/PENDING 유상 라이선스 % 그룹 발견. partial unique index 생성을 건너뜁니다. 운영팀이 수동 정리 후 본 마이그레이션 재실행 필요.',
            dup_group_count;
        RAISE WARNING '[license-migration] 진단 쿼리는 본 파일 하단의 주석 참조.';
    ELSE
        EXECUTE
            'CREATE UNIQUE INDEX IF NOT EXISTS uk_licenses_user_product_active_paid
                ON licenses (owner_id, product_id)
                WHERE owner_type = ''USER''
                  AND license_type <> ''TRIAL''
                  AND status IN (''ACTIVE'', ''PENDING'')';
        RAISE NOTICE '[license-migration] partial unique index 생성 완료 OK';
    END IF;
END;
$migrate$;

COMMIT;

-- =============================================================================
-- 운영자용 진단/정리 쿼리 (필요 시 수동 실행)
-- =============================================================================
--
-- (a) 보정되지 않은 orphan license 상세 확인:
--   SELECT l.id, l.owner_id, l.product_id, l.source_order_id,
--          l.license_key, l.created_at
--   FROM licenses l
--   WHERE l.owner_type = 'USER'
--     AND NOT EXISTS (SELECT 1 FROM users u WHERE u.id = l.owner_id);
--
-- (b) USER 중복 ACTIVE/PENDING 유상 라이선스 상세:
--   SELECT owner_id, product_id,
--          array_agg(id ORDER BY created_at) AS license_ids,
--          array_agg(status ORDER BY created_at) AS statuses,
--          array_agg(created_at ORDER BY created_at) AS created_ats,
--          array_agg(valid_until ORDER BY created_at) AS valid_untils
--   FROM licenses
--   WHERE owner_type = 'USER'
--     AND license_type <> 'TRIAL'
--     AND status IN ('ACTIVE','PENDING')
--   GROUP BY owner_id, product_id
--   HAVING COUNT(*) > 1;
--
-- (c) 중복 정리 권고:
--    - 동일 owner+product의 중복 라이선스 중 가장 늦은 valid_until 1개만 ACTIVE 유지
--    - 나머지는 admin 판단으로 SUSPENDED 또는 REVOKED 처리 (정책 결정 필요)
--    - 정리 후 본 마이그레이션 재실행하면 partial unique index가 생성됨
-- =============================================================================
