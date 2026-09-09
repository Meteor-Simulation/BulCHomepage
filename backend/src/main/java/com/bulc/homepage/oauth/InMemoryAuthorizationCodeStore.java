package com.bulc.homepage.oauth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 인메모리 Authorization Code 저장소 (MDP-793 기본 구현).
 *
 * <p>{@code bulc.oauth.code-store} 가 미설정이거나 {@code memory} 일 때 활성화된다 — 기본값.
 * 단일 인스턴스·개발/테스트용. 서버 재기동·다중 인스턴스에서는 code 가 유실되므로
 * 운영 다중 인스턴스는 {@code redis} 로 전환한다.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "bulc.oauth.code-store", havingValue = "memory", matchIfMissing = true)
public class InMemoryAuthorizationCodeStore implements AuthorizationCodeStore {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int CODE_LENGTH = 32;
    private static final long CODE_TTL_SECONDS = 600; // 10분

    private final Map<String, AuthorizationCode> codeStore = new ConcurrentHashMap<>();

    public InMemoryAuthorizationCodeStore() {
        log.info("AuthorizationCodeStore: 인메모리 구현 활성화 (단일 인스턴스용 · 운영 다중 인스턴스는 redis 권장)");
    }

    @Override
    public String createAndStore(String userEmail, String clientId, String redirectUri,
                                 String codeChallenge, String codeChallengeMethod) {
        String code = generateCode();
        Instant now = Instant.now();

        AuthorizationCode authCode = AuthorizationCode.builder()
                .code(code)
                .userEmail(userEmail)
                .clientId(clientId)
                .redirectUri(redirectUri)
                .codeChallenge(codeChallenge)
                .codeChallengeMethod(codeChallengeMethod != null ? codeChallengeMethod : "S256")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(CODE_TTL_SECONDS))
                .build();

        codeStore.put(code, authCode);
        log.debug("Authorization Code 생성: userEmail={}, clientId={}", userEmail, clientId);

        return code;
    }

    @Override
    public Optional<AuthorizationCode> findValidCode(String code) {
        AuthorizationCode authCode = codeStore.get(code);

        if (authCode == null) {
            log.warn("Authorization Code 없음: code={}", maskCode(code));
            return Optional.empty();
        }

        if (!authCode.isValid()) {
            log.warn("Authorization Code 무효 (사용됨={}, 만료={}): code={}",
                    authCode.isUsed(), authCode.isExpired(), maskCode(code));
            return Optional.empty();
        }

        return Optional.of(authCode);
    }

    @Override
    public Optional<AuthorizationCode> consumeCode(String code) {
        AuthorizationCode authCode = codeStore.remove(code);

        if (authCode == null) {
            return Optional.empty();
        }

        if (!authCode.isValid()) {
            return Optional.empty();
        }

        authCode.markAsUsed();
        log.debug("Authorization Code 소비: userEmail={}", authCode.getUserEmail());

        return Optional.of(authCode);
    }

    /**
     * 만료된 코드 정리 (5분마다 실행). Redis 구현은 네이티브 TTL 을 쓰므로 불필요.
     */
    @Scheduled(fixedRate = 300000)
    public void cleanupExpiredCodes() {
        int beforeSize = codeStore.size();
        codeStore.entrySet().removeIf(entry -> !entry.getValue().isValid());
        int removed = beforeSize - codeStore.size();

        if (removed > 0) {
            log.info("만료된 Authorization Code 정리: {} 건 제거", removed);
        }
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
}
