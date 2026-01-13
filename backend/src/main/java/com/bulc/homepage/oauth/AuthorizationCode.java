package com.bulc.homepage.oauth;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * OAuth 2.0 Authorization Code.
 *
 * 인증 성공 시 발급되어 임시 저장되며,
 * /oauth/token 요청 시 JWT로 교환됩니다.
 */
@Getter
@Builder
public class AuthorizationCode {

    /**
     * 발급된 Authorization Code (랜덤 문자열)
     */
    private final String code;

    /**
     * 인증된 사용자 이메일
     */
    private final String userEmail;

    /**
     * 클라이언트 ID (향후 다중 클라이언트 지원용)
     */
    private final String clientId;

    /**
     * 리다이렉트 URI (검증용)
     */
    private final String redirectUri;

    /**
     * PKCE code_challenge (SHA-256 해시된 값)
     */
    private final String codeChallenge;

    /**
     * PKCE code_challenge_method ("S256" 또는 "plain")
     */
    private final String codeChallengeMethod;

    /**
     * 발급 시각
     */
    private final Instant issuedAt;

    /**
     * 만료 시각 (기본 10분)
     */
    private final Instant expiresAt;

    /**
     * 사용 여부 (한 번만 사용 가능)
     */
    private boolean used;

    /**
     * 코드 사용 처리
     */
    public void markAsUsed() {
        this.used = true;
    }

    /**
     * 만료 여부 확인
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * 유효성 확인 (미사용 + 미만료)
     */
    public boolean isValid() {
        return !used && !isExpired();
    }
}
