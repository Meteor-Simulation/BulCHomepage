package com.bulc.homepage.oauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PKCE (Proof Key for Code Exchange) 유틸리티.
 *
 * RFC 7636 스펙에 따른 구현:
 * - code_verifier: 43-128자의 랜덤 문자열 (A-Z, a-z, 0-9, -, ., _, ~)
 * - code_challenge: code_verifier의 SHA-256 해시 (Base64 URL-safe 인코딩)
 * - code_challenge_method: "S256" (SHA-256)
 */
public final class PkceUtils {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int VERIFIER_LENGTH = 64;

    private PkceUtils() {
        // Utility class
    }

    /**
     * code_verifier를 SHA-256 해시하여 code_challenge 생성.
     *
     * @param codeVerifier 클라이언트에서 생성한 code_verifier
     * @return Base64 URL-safe 인코딩된 code_challenge
     */
    public static String generateCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * PKCE 검증: code_verifier로 code_challenge 재생성 후 비교.
     *
     * @param codeVerifier 클라이언트에서 제출한 code_verifier
     * @param storedCodeChallenge 저장된 code_challenge
     * @param codeChallengeMethod 챌린지 메서드 ("S256" 또는 "plain")
     * @return 검증 성공 여부
     */
    public static boolean verifyCodeChallenge(String codeVerifier, String storedCodeChallenge,
                                               String codeChallengeMethod) {
        if (codeVerifier == null || storedCodeChallenge == null) {
            return false;
        }

        if ("plain".equalsIgnoreCase(codeChallengeMethod)) {
            // plain: code_verifier == code_challenge
            return codeVerifier.equals(storedCodeChallenge);
        } else {
            // S256 (default): SHA256(code_verifier) == code_challenge
            String computedChallenge = generateCodeChallenge(codeVerifier);
            return computedChallenge.equals(storedCodeChallenge);
        }
    }

    /**
     * code_verifier 생성 (테스트/문서화 목적).
     * 실제로는 클라이언트에서 생성해야 함.
     *
     * @return 랜덤 code_verifier
     */
    public static String generateCodeVerifier() {
        byte[] randomBytes = new byte[VERIFIER_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * code_challenge_method 검증.
     *
     * @param method 요청된 메서드
     * @return 지원되는 메서드인지 여부
     */
    public static boolean isSupportedMethod(String method) {
        return "S256".equalsIgnoreCase(method) || "plain".equalsIgnoreCase(method);
    }
}
