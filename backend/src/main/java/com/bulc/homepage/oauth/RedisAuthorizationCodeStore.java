package com.bulc.homepage.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Redis 기반 Authorization Code 저장소 (MDP-793 · 계약 v1.2.0 §4).
 *
 * <p>{@code bulc.oauth.code-store=redis} 일 때만 활성화된다 (프로필 게이트).
 * 미설정 시 {@link InMemoryAuthorizationCodeStore} 가 기본이므로, 본 구현을 추가해도
 * Redis 인프라가 강제되지 않는다 — 운영 다중 인스턴스에서만 명시적으로 켠다.</p>
 *
 * <p>code 는 네이티브 TTL(10분)로 자동 만료되며, 소비는 {@code getAndDelete} 로
 * 원자적으로 1회만 성공한다 (다중 인스턴스 안전).</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "bulc.oauth.code-store", havingValue = "redis")
public class RedisAuthorizationCodeStore implements AuthorizationCodeStore {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int CODE_LENGTH = 32;
    private static final Duration CODE_TTL = Duration.ofSeconds(600); // 10분
    private static final String KEY_PREFIX = "oauth:authcode:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisAuthorizationCodeStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        log.info("AuthorizationCodeStore: Redis 구현 활성화 (다중 인스턴스 안전 · TTL {}s)", CODE_TTL.getSeconds());
    }

    @Override
    public String createAndStore(String userEmail, String clientId, String redirectUri,
                                 String codeChallenge, String codeChallengeMethod) {
        String code = generateCode();
        Instant now = Instant.now();

        CodeSnapshot snapshot = new CodeSnapshot(
                code, userEmail, clientId, redirectUri,
                codeChallenge, codeChallengeMethod != null ? codeChallengeMethod : "S256",
                now.toEpochMilli(), now.plus(CODE_TTL).toEpochMilli()
        );

        try {
            String json = objectMapper.writeValueAsString(snapshot);
            redisTemplate.opsForValue().set(keyFor(code), json, CODE_TTL);
        } catch (Exception e) {
            throw new IllegalStateException("Authorization Code 저장 실패", e);
        }

        log.debug("Authorization Code 생성(redis): userEmail={}, clientId={}", userEmail, clientId);
        return code;
    }

    @Override
    public Optional<AuthorizationCode> findValidCode(String code) {
        String json = redisTemplate.opsForValue().get(keyFor(code));
        if (json == null) {
            log.warn("Authorization Code 없음(redis): code={}", maskCode(code));
            return Optional.empty();
        }
        AuthorizationCode authCode = deserialize(json);
        if (authCode == null || !authCode.isValid()) {
            return Optional.empty();
        }
        return Optional.of(authCode);
    }

    @Override
    public Optional<AuthorizationCode> consumeCode(String code) {
        // 원자적 소비 — 조회와 동시에 삭제하여 다중 인스턴스에서 1회만 성공
        String json = redisTemplate.opsForValue().getAndDelete(keyFor(code));
        if (json == null) {
            return Optional.empty();
        }
        AuthorizationCode authCode = deserialize(json);
        if (authCode == null || !authCode.isValid()) {
            return Optional.empty();
        }
        authCode.markAsUsed();
        log.debug("Authorization Code 소비(redis): userEmail={}", authCode.getUserEmail());
        return Optional.of(authCode);
    }

    private AuthorizationCode deserialize(String json) {
        try {
            CodeSnapshot s = objectMapper.readValue(json, CodeSnapshot.class);
            return AuthorizationCode.builder()
                    .code(s.code())
                    .userEmail(s.userEmail())
                    .clientId(s.clientId())
                    .redirectUri(s.redirectUri())
                    .codeChallenge(s.codeChallenge())
                    .codeChallengeMethod(s.codeChallengeMethod())
                    .issuedAt(Instant.ofEpochMilli(s.issuedAtEpochMs()))
                    .expiresAt(Instant.ofEpochMilli(s.expiresAtEpochMs()))
                    .build();
        } catch (Exception e) {
            log.warn("Authorization Code 역직렬화 실패", e);
            return null;
        }
    }

    private String keyFor(String code) {
        return KEY_PREFIX + code;
    }

    private String generateCode() {
        byte[] randomBytes = new byte[CODE_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String maskCode(String code) {
        if (code == null || code.length() < 8) {
            return "****";
        }
        return code.substring(0, 4) + "****" + code.substring(code.length() - 4);
    }

    /**
     * Redis 직렬화용 스냅샷. Instant 는 epoch millis(long)로 저장해 직렬화 모호성을 제거한다.
     * used 플래그는 저장하지 않는다 — 소비 시 키가 삭제되므로 저장 중에는 항상 미사용 상태다.
     */
    private record CodeSnapshot(
            String code,
            String userEmail,
            String clientId,
            String redirectUri,
            String codeChallenge,
            String codeChallengeMethod,
            long issuedAtEpochMs,
            long expiresAtEpochMs
    ) {}
}
