package com.bulc.homepage.oauth;

import com.bulc.homepage.entity.User;
import com.bulc.homepage.repository.UserRepository;
import com.bulc.homepage.security.JwtTokenProvider;
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
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    /**
     * Token Exchange Endpoint.
     *
     * Authorization Code + PKCE Verifier → JWT Access Token
     *
     * @param grantType "authorization_code" 고정
     * @param code Authorization Code
     * @param redirectUri 원래 redirect_uri (검증용)
     * @param clientId 클라이언트 ID (검증용)
     * @param codeVerifier PKCE code_verifier
     */
    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> token(
            @RequestParam("grant_type") String grantType,
            @RequestParam("code") String code,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam("client_id") String clientId,
            @RequestParam("code_verifier") String codeVerifier) {

        log.info("OAuth token 요청: client_id={}", clientId);

        // grant_type 검증
        if (!"authorization_code".equals(grantType)) {
            return errorResponse("unsupported_grant_type",
                    "Only 'authorization_code' grant_type is supported");
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

        // 사용자 조회
        Optional<User> userOpt = userRepository.findByEmail(authCode.getUserEmail());
        if (userOpt.isEmpty()) {
            log.error("OAuth token 실패: 사용자 없음, email={}", authCode.getUserEmail());
            return errorResponse("invalid_grant",
                    "User not found");
        }

        User user = userOpt.get();

        // JWT 토큰 생성
        String accessToken = jwtTokenProvider.generateAccessToken(user.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail());

        log.info("OAuth token 성공: email={}", user.getEmail());

        // RFC 6749 Token Response
        return ResponseEntity.ok(Map.of(
                "access_token", accessToken,
                "refresh_token", refreshToken,
                "token_type", "Bearer",
                "expires_in", accessTokenExpiration / 1000,
                "user", Map.of(
                        "id", user.getEmail(),
                        "email", user.getEmail(),
                        "roles_code", user.getRolesCode() != null ? user.getRolesCode() : "002"
                )
        ));
    }

    /**
     * Token Refresh Endpoint (선택적).
     *
     * Refresh Token → 새 Access Token
     */
    @PostMapping(value = "/token/refresh", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> refreshToken(
            @RequestParam("grant_type") String grantType,
            @RequestParam("refresh_token") String refreshToken) {

        log.info("OAuth token refresh 요청");

        // grant_type 검증
        if (!"refresh_token".equals(grantType)) {
            return errorResponse("unsupported_grant_type",
                    "Only 'refresh_token' grant_type is supported for this endpoint");
        }

        // Refresh Token 검증
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            return errorResponse("invalid_grant",
                    "Invalid or expired refresh token");
        }

        // 이메일 추출
        String email = jwtTokenProvider.getEmailFromToken(refreshToken);

        // 사용자 조회
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return errorResponse("invalid_grant",
                    "User not found");
        }

        User user = userOpt.get();

        // 새 토큰 발급
        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getEmail());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail());

        log.info("OAuth token refresh 성공: email={}", user.getEmail());

        return ResponseEntity.ok(Map.of(
                "access_token", newAccessToken,
                "refresh_token", newRefreshToken,
                "token_type", "Bearer",
                "expires_in", accessTokenExpiration / 1000
        ));
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
