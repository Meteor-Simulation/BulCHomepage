package com.bulc.homepage.oauth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authorization Code 저장소.
 *
 * 인메모리 구현 (운영 환경에서는 Redis 권장).
 * - 코드 발급 및 저장
 * - 코드 검증 및 소비
 * - 만료된 코드 자동 정리
 */
@Slf4j
@Component
public class AuthorizationCodeStore {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int CODE_LENGTH = 32;
    private static final long CODE_TTL_SECONDS = 600; // 10분

    /**
     * Authorization Code 저장소 (code -> AuthorizationCode)
     */
    private final Map<String, AuthorizationCode> codeStore = new ConcurrentHashMap<>();

    /**
     * Authorization Code 생성 및 저장.
     *
     * @param userEmail 인증된 사용자 이메일
     * @param clientId 클라이언트 ID
     * @param redirectUri 리다이렉트 URI
     * @param codeChallenge PKCE code_challenge
     * @param codeChallengeMethod PKCE code_challenge_method
     * @return 생성된 Authorization Code 문자열
     */
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

    /**
     * Authorization Code 조회 및 검증.
     * 코드가 유효하면 반환, 유효하지 않으면 Optional.empty()
     *
     * @param code Authorization Code
     * @return AuthorizationCode (유효한 경우)
     */
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

    /**
     * Authorization Code 소비 (사용 처리).
     * 코드를 사용 완료 상태로 변경하고 저장소에서 제거.
     *
     * @param code Authorization Code
     * @return 소비된 AuthorizationCode (성공 시)
     */
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
     * 만료된 코드 정리 (5분마다 실행).
     */
    @Scheduled(fixedRate = 300000) // 5분
    public void cleanupExpiredCodes() {
        int beforeSize = codeStore.size();
        codeStore.entrySet().removeIf(entry -> !entry.getValue().isValid());
        int removed = beforeSize - codeStore.size();

        if (removed > 0) {
            log.info("만료된 Authorization Code 정리: {} 건 제거", removed);
        }
    }

    /**
     * 랜덤 Authorization Code 생성.
     */
    private String generateCode() {
        byte[] randomBytes = new byte[CODE_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * 코드 마스킹 (로깅용).
     */
    private String maskCode(String code) {
        if (code == null || code.length() < 8) {
            return "****";
        }
        return code.substring(0, 4) + "****" + code.substring(code.length() - 4);
    }
}
