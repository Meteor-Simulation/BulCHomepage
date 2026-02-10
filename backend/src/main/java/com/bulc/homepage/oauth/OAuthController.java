package com.bulc.homepage.oauth;

import com.bulc.homepage.entity.User;
import com.bulc.homepage.repository.UserRepository;
import com.bulc.homepage.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * OAuth 2.0 Authorization Controller.
 *
 * OAuth 2.0 Authorization Code Flow with PKCE를 처리합니다.
 * 세션 없이 동작 (STATELESS).
 *
 * 플로우:
 * 1. 클라이언트 → GET /oauth/authorize (PKCE 파라미터 포함)
 * 2. 서버 → 검증 후 로그인 파라미터 반환
 * 3. 클라이언트 → 시스템 브라우저에서 로그인 UI 표시
 * 4. 사용자 → POST /oauth/login-process (ID/PW + OAuth 파라미터)
 * 5. 서버 → 인증 성공 시 redirect_uri?code=xxx 로 리다이렉트
 * 6. 클라이언트 → POST /oauth/token (code + code_verifier)
 * 7. 서버 → JWT 발급
 */
@Slf4j
@RestController
@RequestMapping("/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final AuthService authService;
    private final AuthorizationCodeStore codeStore;
    private final OAuthClientProperties clientProperties;
    private final UserRepository userRepository;

    /**
     * OAuth 2.0 Authorization Endpoint.
     *
     * 클라이언트 앱에서 로그인 시작 시 호출.
     * PKCE 파라미터를 검증하고, 로그인에 필요한 정보를 반환합니다.
     *
     * 브라우저 요청 (Accept: text/html)인 경우 로그인 페이지로 리다이렉트,
     * API 요청 (Accept: application/json)인 경우 JSON 응답을 반환합니다.
     *
     * @param clientId 클라이언트 ID
     * @param redirectUri 인증 완료 후 리다이렉트할 URI (custom scheme 가능)
     * @param responseType "code" 고정
     * @param codeChallenge PKCE code_challenge
     * @param codeChallengeMethod PKCE code_challenge_method ("S256" 또는 "plain")
     * @param state CSRF 방지용 상태값 (선택)
     * @param request HTTP 요청 (Accept 헤더 확인용)
     */
    @GetMapping("/authorize")
    public ResponseEntity<?> authorize(
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam("response_type") String responseType,
            @RequestParam("code_challenge") String codeChallenge,
            @RequestParam(value = "code_challenge_method", defaultValue = "S256") String codeChallengeMethod,
            @RequestParam(value = "state", required = false) String state,
            HttpServletRequest request) {

        log.info("OAuth authorize 요청: client_id={}, redirect_uri={}", clientId, redirectUri);

        // client_id 검증 (등록된 클라이언트인지 확인)
        if (!isRegisteredClient(clientId)) {
            log.warn("등록되지 않은 client_id: {}", clientId);
            return errorResponse("unauthorized_client",
                    "Unknown or unregistered client_id");
        }

        // response_type 검증
        if (!"code".equals(responseType)) {
            return errorResponse("unsupported_response_type",
                    "Only 'code' response_type is supported");
        }

        // PKCE code_challenge_method 검증
        if (!PkceUtils.isSupportedMethod(codeChallengeMethod)) {
            return errorResponse("invalid_request",
                    "Unsupported code_challenge_method. Use 'S256' or 'plain'");
        }

        // PKCE code_challenge 검증
        if (codeChallenge == null || codeChallenge.length() < 43) {
            return errorResponse("invalid_request",
                    "code_challenge is required and must be at least 43 characters");
        }

        // redirect_uri 검증 (사전 등록된 URI만 허용)
        if (!isValidRedirectUri(clientId, redirectUri)) {
            return errorResponse("invalid_request",
                    "Invalid redirect_uri. The URI must be registered for this client");
        }

        // ========================================================
        // SSO 자동 로그인: 이미 인증된 사용자가 있는지 확인
        // 브라우저 쿠키(AUTH_TOKEN)를 통해 인증된 상태면 로그인 화면 생략
        // ========================================================
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserDetails) {

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            // username은 이제 userId.toString()
            UUID userId = UUID.fromString(userDetails.getUsername());

            // 사용자가 활성 상태인지 확인
            User user = userRepository.findById(userId).orElse(null);
            if (user != null && user.getIsActive()) {
                String email = user.getEmail();
                log.info("OAuth SSO 자동 로그인 감지: email={}, client_id={}", email, clientId);

                // SSO 동의 페이지로 리다이렉트 (사용자 확인 필요)
                String consentPageUrl = buildConsentPageUrl(
                        email,
                        clientId,
                        redirectUri,
                        codeChallenge,
                        codeChallengeMethod,
                        state
                );
                log.info("OAuth SSO 동의 페이지로 리다이렉트: {}", consentPageUrl);
                return ResponseEntity
                        .status(HttpStatus.FOUND)
                        .location(URI.create(consentPageUrl))
                        .build();
            }
        }

        // ========================================================
        // 로그인 필요: 인증되지 않은 상태
        // ========================================================

        // 브라우저 요청인 경우 로그인 페이지로 리다이렉트
        String acceptHeader = request.getHeader("Accept");
        if (acceptHeader != null && acceptHeader.contains("text/html")) {
            String loginPageUrl = buildLoginPageUrl(clientId, redirectUri, codeChallenge, codeChallengeMethod, state);
            log.info("브라우저 요청 - 로그인 페이지로 리다이렉트: {}", loginPageUrl);
            return ResponseEntity
                    .status(HttpStatus.FOUND)
                    .location(URI.create(loginPageUrl))
                    .build();
        }

        // API 요청인 경우 JSON 응답 반환
        // 로그인 페이지 정보 반환 (클라이언트에서 로그인 UI 표시)
        // OAuth 파라미터를 그대로 반환하여 login-process 시 함께 전송하도록 함
        return ResponseEntity.ok(Map.of(
                "action", "login_required",
                "login_endpoint", "/oauth/login-process",
                "login_page", buildLoginPageUrl(clientId, redirectUri, codeChallenge, codeChallengeMethod, state),
                "oauth_params", Map.of(
                        "client_id", clientId,
                        "redirect_uri", redirectUri,
                        "code_challenge", codeChallenge,
                        "code_challenge_method", codeChallengeMethod,
                        "state", state != null ? state : ""
                ),
                "message", "사용자 인증이 필요합니다"
        ));
    }

    /**
     * SSO 동의 확인 처리.
     *
     * 사용자가 동의 페이지에서 "계속" 버튼을 누르면 호출됩니다.
     * 인증된 사용자에 대해 Authorization Code를 발급하고 리다이렉트합니다.
     */
    @PostMapping(value = "/sso-confirm", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> ssoConfirm(
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam("code_challenge") String codeChallenge,
            @RequestParam(value = "code_challenge_method", defaultValue = "S256") String codeChallengeMethod,
            @RequestParam(value = "state", required = false) String state) {

        log.info("OAuth SSO 확인 요청: client_id={}", clientId);

        // 현재 인증된 사용자 확인
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserDetails)) {
            log.warn("SSO 확인 실패: 인증되지 않은 사용자");
            // 로그인 페이지로 리다이렉트
            String loginPageUrl = buildLoginPageUrl(clientId, redirectUri, codeChallenge, codeChallengeMethod, state);
            return ResponseEntity
                    .status(HttpStatus.FOUND)
                    .location(URI.create(loginPageUrl))
                    .build();
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        // username은 이제 userId.toString()
        UUID userId = UUID.fromString(userDetails.getUsername());

        // 사용자가 활성 상태인지 확인
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.getIsActive()) {
            log.warn("SSO 확인 실패: 비활성 사용자 userId={}", userId);
            String loginPageUrl = buildLoginPageUrl(clientId, redirectUri, codeChallenge, codeChallengeMethod, state);
            return ResponseEntity
                    .status(HttpStatus.FOUND)
                    .location(URI.create(loginPageUrl))
                    .build();
        }
        String email = user.getEmail();

        // client_id 검증
        if (!isRegisteredClient(clientId)) {
            return errorResponse("unauthorized_client", "Unknown or unregistered client_id");
        }

        // redirect_uri 검증
        if (!isValidRedirectUri(clientId, redirectUri)) {
            return errorResponse("invalid_request", "Invalid redirect_uri");
        }

        // PKCE 파라미터 검증
        if (!PkceUtils.isSupportedMethod(codeChallengeMethod)) {
            return errorResponse("invalid_request", "Unsupported code_challenge_method");
        }
        if (codeChallenge == null || codeChallenge.length() < 43) {
            return errorResponse("invalid_request", "code_challenge is required");
        }

        // Authorization Code 발급
        String code = codeStore.createAndStore(
                email,
                clientId,
                redirectUri,
                codeChallenge,
                codeChallengeMethod
        );

        log.info("OAuth SSO 확인 성공: email={}, client_id={}", email, clientId);

        // Custom scheme인 경우 성공 페이지를 통해 리다이렉트
        if (isCustomScheme(redirectUri)) {
            String callbackPageUrl = buildCallbackPageUrl(redirectUri, code, state, clientId);
            log.info("OAuth SSO 성공, 성공 페이지로 리다이렉트: {}", callbackPageUrl);
            return ResponseEntity
                    .status(HttpStatus.FOUND)
                    .location(URI.create(callbackPageUrl))
                    .build();
        }

        // HTTP 기반 redirect_uri는 직접 리다이렉트
        String redirectUrl = buildRedirectUrl(redirectUri, code, state);
        log.info("OAuth SSO 성공, 리다이렉트: {}", maskUrl(redirectUrl));
        return ResponseEntity
                .status(HttpStatus.FOUND)
                .location(URI.create(redirectUrl))
                .build();
    }

    /**
     * SSO 동의 페이지 URL 생성.
     */
    private String buildConsentPageUrl(String email, String clientId, String redirectUri,
                                       String codeChallenge, String codeChallengeMethod, String state) {
        String appName = clientProperties.getDisplayName(clientId);

        StringBuilder url = new StringBuilder("/oauth/consent.html?");
        url.append("email=").append(urlEncode(email));
        url.append("&app_name=").append(urlEncode(appName));
        url.append("&client_id=").append(urlEncode(clientId));
        url.append("&redirect_uri=").append(urlEncode(redirectUri));
        url.append("&code_challenge=").append(urlEncode(codeChallenge));
        url.append("&code_challenge_method=").append(urlEncode(codeChallengeMethod));
        if (state != null && !state.isEmpty()) {
            url.append("&state=").append(urlEncode(state));
        }
        return url.toString();
    }

    /**
     * 로그인 페이지 URL 생성.
     */
    private String buildLoginPageUrl(String clientId, String redirectUri, String codeChallenge,
                                     String codeChallengeMethod, String state) {
        StringBuilder url = new StringBuilder("/oauth/login.html?");
        url.append("client_id=").append(urlEncode(clientId));
        url.append("&redirect_uri=").append(urlEncode(redirectUri));
        url.append("&code_challenge=").append(urlEncode(codeChallenge));
        url.append("&code_challenge_method=").append(urlEncode(codeChallengeMethod));
        if (state != null && !state.isEmpty()) {
            url.append("&state=").append(urlEncode(state));
        }
        return url.toString();
    }

    /**
     * URL 인코딩.
     */
    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * OAuth 2.0 로그인 처리.
     *
     * 사용자가 ID/PW와 OAuth 파라미터를 함께 제출하면
     * 인증 후 Authorization Code를 발급하고, redirect_uri로 리다이렉트합니다.
     *
     * @param email 사용자 이메일
     * @param password 비밀번호
     * @param clientId 클라이언트 ID
     * @param redirectUri 리다이렉트 URI
     * @param codeChallenge PKCE code_challenge
     * @param codeChallengeMethod PKCE code_challenge_method
     * @param state CSRF 방지용 상태값
     */
    @PostMapping(value = "/login-process", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> loginProcess(
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam("code_challenge") String codeChallenge,
            @RequestParam(value = "code_challenge_method", defaultValue = "S256") String codeChallengeMethod,
            @RequestParam(value = "state", required = false) String state) {

        log.info("OAuth login-process 요청: email={}, client_id={}", email, clientId);

        // client_id 검증 (등록된 클라이언트인지 확인)
        if (!isRegisteredClient(clientId)) {
            return errorResponse("unauthorized_client",
                    "Unknown or unregistered client_id");
        }

        // PKCE 파라미터 재검증
        if (!PkceUtils.isSupportedMethod(codeChallengeMethod)) {
            return errorResponse("invalid_request",
                    "Unsupported code_challenge_method");
        }

        if (codeChallenge == null || codeChallenge.length() < 43) {
            return errorResponse("invalid_request",
                    "code_challenge is required");
        }

        // redirect_uri 검증 (사전 등록된 URI만 허용)
        if (!isValidRedirectUri(clientId, redirectUri)) {
            return errorResponse("invalid_request",
                    "Invalid redirect_uri");
        }

        try {
            // 사용자 인증
            User user = authService.authenticateUser(email, password);

            // Authorization Code 생성
            String code = codeStore.createAndStore(
                    user.getEmail(),
                    clientId,
                    redirectUri,
                    codeChallenge,
                    codeChallengeMethod
            );

            // Custom scheme인 경우 성공 페이지를 통해 리다이렉트 (브라우저 호환성)
            // localhost는 직접 리다이렉트
            if (isCustomScheme(redirectUri)) {
                String callbackPageUrl = buildCallbackPageUrl(redirectUri, code, state, clientId);
                log.info("OAuth 인증 성공, 성공 페이지로 리다이렉트: {}", callbackPageUrl);

                return ResponseEntity
                        .status(HttpStatus.FOUND)
                        .location(URI.create(callbackPageUrl))
                        .build();
            }

            // localhost 등 HTTP 기반 redirect_uri는 직접 리다이렉트
            String redirectUrl = buildRedirectUrl(redirectUri, code, state);
            log.info("OAuth 인증 성공, 리다이렉트: {}", maskUrl(redirectUrl));

            return ResponseEntity
                    .status(HttpStatus.FOUND)
                    .location(URI.create(redirectUrl))
                    .build();

        } catch (RuntimeException e) {
            log.warn("OAuth 인증 실패: email={}, error={}", email, e.getMessage());

            // 인증 실패 시 에러 응답 (리다이렉트하지 않음)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "access_denied",
                            "error_description", e.getMessage()
                    ));
        }
    }

    /**
     * Custom scheme 여부 확인.
     */
    private boolean isCustomScheme(String redirectUri) {
        if (redirectUri == null) {
            return false;
        }
        // http, https가 아닌 모든 scheme은 custom scheme
        return !redirectUri.startsWith("http://") && !redirectUri.startsWith("https://");
    }

    /**
     * 성공 페이지 URL 생성 (custom scheme용).
     */
    private String buildCallbackPageUrl(String redirectUri, String code, String state, String clientId) {
        StringBuilder url = new StringBuilder("/oauth/callback.html?");
        url.append("redirect_uri=").append(urlEncode(redirectUri));
        url.append("&code=").append(urlEncode(code));
        url.append("&client_id=").append(urlEncode(clientId));
        if (state != null && !state.isEmpty()) {
            url.append("&state=").append(urlEncode(state));
        }
        return url.toString();
    }

    /**
     * client_id 검증.
     * 등록된 클라이언트인지 확인합니다.
     */
    private boolean isRegisteredClient(String clientId) {
        if (clientId == null) {
            return false;
        }
        return clientProperties.isRegisteredClient(clientId);
    }

    /**
     * redirect_uri 검증.
     * 해당 client_id에 대해 사전 등록된 redirect_uri인지 확인합니다.
     *
     * @param clientId 클라이언트 ID
     * @param redirectUri 검증할 redirect URI
     * @return 등록된 URI이면 true
     */
    private boolean isValidRedirectUri(String clientId, String redirectUri) {
        if (clientId == null || redirectUri == null) {
            return false;
        }

        boolean isAllowed = clientProperties.isAllowedRedirectUri(clientId, redirectUri);

        if (!isAllowed) {
            log.warn("redirect_uri 검증 실패: client_id={}, redirect_uri={}", clientId, redirectUri);
        }

        return isAllowed;
    }

    /**
     * 리다이렉트 URL 생성.
     */
    private String buildRedirectUrl(String redirectUri, String code, String state) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("code", code);

        if (state != null && !state.isEmpty()) {
            builder.queryParam("state", state);
        }

        return builder.toUriString();
    }

    /**
     * 에러 응답 생성.
     */
    private ResponseEntity<Map<String, String>> errorResponse(String error, String description) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", error,
                "error_description", description
        ));
    }

    /**
     * URL 마스킹 (로깅용).
     */
    private String maskUrl(String url) {
        if (url == null) {
            return null;
        }
        // code 파라미터 마스킹
        return url.replaceAll("code=[^&]+", "code=****");
    }
}
