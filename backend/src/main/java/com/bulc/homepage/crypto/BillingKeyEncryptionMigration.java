package com.bulc.homepage.crypto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 기존 평문 billing_key 행 1회 암호화 (idempotent).
 *
 * 컨버터 적용 후 첫 기동 시, 'v1:' prefix가 없는(=평문) 행을 찾아 암호화해 재저장한다.
 * raw JDBC로 읽고/쓰므로 컨버터·Hibernate dirty-check를 우회한다
 * (엔티티 필드값이 안 바뀌면 Hibernate가 UPDATE를 생략하는 문제 방지).
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class BillingKeyEncryptionMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    "SELECT id, billing_key FROM billing_keys "
                            + "WHERE billing_key IS NOT NULL AND billing_key NOT LIKE 'v1:%'");
        } catch (Exception e) {
            log.warn("[빌링키 암호화] 마이그레이션 조회 실패 (건너뜀): {}", e.getMessage());
            return;
        }

        if (rows.isEmpty()) {
            log.info("[빌링키 암호화] 마이그레이션 대상 없음 (모두 암호화됨)");
            return;
        }

        BillingKeyCipher cipher = BillingKeyCipher.getInstance();
        if (cipher == null) {
            log.error("[빌링키 암호화] cipher 미초기화 — 마이그레이션 중단");
            return;
        }

        log.info("[빌링키 암호화] 평문 {}건 암호화 시작", rows.size());
        int done = 0;
        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            String plain = (String) row.get("billing_key");
            String encrypted = cipher.encrypt(plain);
            jdbcTemplate.update("UPDATE billing_keys SET billing_key = ? WHERE id = ?", encrypted, id);
            done++;
        }
        log.info("[빌링키 암호화] 마이그레이션 완료: {}건 암호화", done);
    }
}
