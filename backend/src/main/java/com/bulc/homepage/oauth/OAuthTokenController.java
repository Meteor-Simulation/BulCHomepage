package com.bulc.homepage.oauth;

import com.bulc.homepage.dto.response.AuthResponse;
import com.bulc.homepage.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * OAuth 2.0 Token Endpoint.
 *
 * Authorization Code를 JWT Access Token으로 교환합니다.
 * PKCE (code_verifier) 검증을 수행합니다.
 *
 * RFC 6749 Token Endpoint:
 * - POST /oauth/token
 * - Content-Type: application/x-www-form-urlencoded
 * - grant_type=authorization_code
 * - code=xxx
 * - redirect_uri=xxx
 * - client_id=xxx
 * - code_verifier=xxx (PKCE)
 */
@Slf4j
@RestController
@RequestMapping("/oauth")
@RequiredArgsConstructor
public class OAuthTokenController {

    private final AuthorizationCodeStore codeStore;
    private final AuthService authService;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    /**
     * Token Exchange Endpoint.
     *
     * 두 가지 grant_type 지원:
     * 1. authorization_code: Authorization Code + PKCE → JWT Access Token
     * 2. refresh_token: Refresh Token → 새 Access Token + 새 Refresh Token (RTR)
     *
     * @param grantType "authorization_code" 또는 "refresh_token"
     * @param code Authorization Code (grant_type=authorization_code일 때 필수)
     * @param redirectUri 원래 redirect_uri (grant_type=authorization_code일 때 필수)
     * @param clientId 클라이언트 ID
     * @param codeVerifier PKCE code_verifier (grant_type=authorization_code일 때 필수)
     * @param refreshToken Refresh Token (grant_type=refresh_token일 때 필수)
     */
    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> token(
            @RequestParam("grant_type") String grantType,
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "redirect_uri", required = false) String redirectUri,
            @RequestParam(value = "client_id", required = false) String clientId,
            @RequestParam(value = "code_verifier", required = false) String codeVerifier,
            @RequestParam(value = "refresh_token", required = false) String refreshToken) {

        log.info("OAuth token 요청: grant_type={}, client_id={}", grantType, clientId);

        // grant_type에 따른 분기 처리
        if ("refresh_token".equals(grantType)) {
            return handleRefreshToken(refreshToken);
        } else if (!"authorization_code".equals(grantType)) {
            return errorResponse("unsupported_grant_type",
                    "Supported grant_type: 'authorization_code', 'refresh_token'");
        }

        // authorization_code grant_type 처리 - 필수 파라미터 검증
        if (code == null || code.isBlank()) {
            return errorResponse("invalid_request", "code is required for authorization_code grant");
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            return errorResponse("invalid_request", "redirect_uri is required for authorization_code grant");
        }
        if (codeVerifier == null || codeVerifier.isBlank()) {
            return errorResponse("invalid_request", "code_verifier is required for authorization_code grant");
        }

        // code_verifier 검증
        if (codeVerifier == null || codeVerifier.length() < 43) {
            return errorResponse("invalid_request",
                    "code_verifier is required and must be at least 43 characters");
        }

        // Authorization Code 조회 및 소비
        Optional<AuthorizationCode> authCodeOpt = codeStore.consumeCode(code);
        if (authCodeOpt.isEmpty()) {
            log.warn("OAuth token 실패: 유효하지 않은 Authorization Code");
            return errorResponse("invalid_grant",
                    "Invalid or expired authorization code");
        }

        AuthorizationCode authCode = authCodeOpt.get();

        // redirect_uri 검증
        if (!redirectUri.equals(authCode.getRedirectUri())) {
            log.warn("OAuth token 실패: redirect_uri 불일치");
            return errorResponse("invalid_grant",
                    "redirect_uri does not match");
        }

        // client_id 검증 (optional, 향후 다중 클라이언트 지원 시)
        if (authCode.getClientId() != null && !clientId.equals(authCode.getClientId())) {
            log.warn("OAuth token 실패: client_id 불일치");
            return errorResponse("invalid_grant",
                    "client_id does not match");
        }

        // PKCE 검증
        boolean pkceValid = PkceUtils.verifyCodeChallenge(
                codeVerifier,
                authCode.getCodeChallenge(),
                authCode.getCodeChallengeMethod()
        );

        if (!pkceValid) {
            log.warn("OAuth token 실패: PKCE 검증 실패, email={}", authCode.getUserEmail());
            return errorResponse("invalid_grant",
                    "PKCE verification failed. code_verifier does not match code_challenge");
        }

        // [RTR] AuthService를 통해 토큰 발급 (DB 저장 + 1년 만료)
        try {
            AuthResponse response = authService.issueTokensForOAuth(
                    authCode.getUserEmail(), clientId);

            log.info("OAuth token 성공: email={}", authCode.getUserEmail());

            // RFC 6749 Token Response
            return ResponseEntity.ok(Map.of(
                    "access_token", response.getAccessToken(),
                    "refresh_token", response.getRefreshToken(),
                    "token_type", "Bearer",
                    "expires_in", accessTokenExpiration / 1000,
                    "user", Map.of(
                            "id", response.getUser().getId(),
                            "email", response.getUser().getEmail(),
                            "roles_code", response.getUser().getRolesCode() != null
                                    ? response.getUser().getRolesCode() : "002"
                    )
            ));
        } catch (RuntimeException e) {
            log.error("OAuth token 실패: {}", e.getMessage());
            return errorResponse("invalid_grant", e.getMessage());
        }
    }

    /**
     * Refresh Token 처리 (내부 메서드).
     *
     * Refresh Token → 새 Access Token + 새 Refresh Token (RTR 적용)
     * Token Theft Detection 적용: 탈취된 토큰 재사용 시 모든 세션 무효화
     */
    private ResponseEntity<?> handleRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return errorResponse("invalid_request", "refresh_token is required for refresh_token grant");
        }

        log.info("OAuth token refresh 처리");

        try {
            AuthResponse response = authService.refreshTokenForOAuth(refreshToken);

            log.info("OAuth token refresh 성공: email={}", response.getUser().getEmail());

            return ResponseEntity.ok(Map.of(
                    "access_token", response.getAccessToken(),
                    "refresh_token", response.getRefreshToken(),
                    "token_type", "Bearer",
                    "expires_in", accessTokenExpiration / 1000
            ));
        } catch (RuntimeException e) {
            log.warn("OAuth token refresh 실패: {}", e.getMessage());
            return errorResponse("invalid_grant", e.getMessage());
        }
    }

    /**
     * Token Refresh Endpoint (레거시, /oauth/token으로 통합됨).
     *
     * @deprecated /oauth/token 엔드포인트에서 grant_type=refresh_token 사용 권장
     */
    @Deprecated
    @PostMapping(value = "/token/refresh", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> refreshToken(
            @RequestParam("grant_type") String grantType,
            @RequestParam("refresh_token") String refreshToken) {

        log.info("OAuth token refresh 요청 (레거시 엔드포인트)");

        // grant_type 검증
        if (!"refresh_token".equals(grantType)) {
            return errorResponse("unsupported_grant_type",
                    "Only 'refresh_token' grant_type is supported for this endpoint");
        }

        return handleRefreshToken(refreshToken);
    }

    /**
     * 에러 응답 생성 (RFC 6749 형식).
     */
    private ResponseEntity<Map<String, String>> errorResponse(String error, String description) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", error,
                "error_description", description
        ));
    }
}
