package com.bulc.homepage.oauth2;

import com.bulc.homepage.entity.User;
import com.bulc.homepage.entity.UserSocialAccount;
import com.bulc.homepage.oauth.AuthorizationCodeStore;
import com.bulc.homepage.oauth.OAuthClientProperties;
import com.bulc.homepage.oauth.PkceUtils;
import com.bulc.homepage.repository.UserRepository;
import com.bulc.homepage.repository.UserSocialAccountRepository;
import com.bulc.homepage.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final String PKCE_PARAMS_COOKIE_NAME = "pkce_params";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final AuthorizationCodeStore authorizationCodeStore;
    private final OAuthClientProperties oAuthClientProperties;
    private final UserSocialAccountRepository socialAccountRepository;

    @Value("${app.oauth2.redirect-uri:http://localhost:3000/oauth/callback}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();

        // PKCE 브릿지: 데스크톱 앱에서 소셜 로그인 시 쿠키로 PKCE 파라미터 전달
        String pkceParamsJson = getCookieValue(request, PKCE_PARAMS_COOKIE_NAME);
        if (pkceParamsJson != null) {
            clearCookie(response, PKCE_PARAMS_COOKIE_NAME);
            handlePkceSocialLogin(request, response, oAuth2User, pkceParamsJson);
            return;
        }

        // 일반 웹 플로우 (기존 동작)
        handleWebSocialLogin(request, response, oAuth2User);
    }

    /**
     * PKCE 데스크톱 플로우: 소셜 로그인 후 Authorization Code 발급.
     */
    private void handlePkceSocialLogin(HttpServletRequest request, HttpServletResponse response,
                                        CustomOAuth2User oAuth2User, String pkceParamsJson) throws IOException {
        String email = oAuth2User.getEmail();
        String provider = oAuth2User.getProvider();
        String providerId = oAuth2User.getProviderId();

        log.info("PKCE 소셜 로그인 처리 - Email: {}, Provider: {}", email, provider);

        // PKCE 파라미터 파싱
        JsonNode pkceParams;
        try {
            pkceParams = objectMapper.readTree(pkceParamsJson);
        } catch (Exception e) {
            log.warn("PKCE 파라미터 파싱 실패: {}", e.getMessage());
            getRedirectStrategy().sendRedirect(request, response,
                    "/oauth/login.html?social_error=" + urlEncode("잘못된 인증 요청입니다. 앱에서 다시 시도해주세요."));
            return;
        }

        String clientId = getJsonText(pkceParams, "client_id");
        String pkceRedirectUri = getJsonText(pkceParams, "redirect_uri");
        String codeChallenge = getJsonText(pkceParams, "code_challenge");
        String codeChallengeMethod = getJsonText(pkceParams, "code_challenge_method");
        String state = getJsonText(pkceParams, "state");

        // PKCE 파라미터 검증
        if (!oAuthClientProperties.isRegisteredClient(clientId)) {
            log.warn("PKCE 소셜 로그인 - 등록되지 않은 client_id: {}", clientId);
            getRedirectStrategy().sendRedirect(request, response,
                    "/oauth/login.html?social_error=" + urlEncode("잘못된 인증 요청입니다. 앱에서 다시 시도해주세요."));
            return;
        }

        if (!oAuthClientProperties.isAllowedRedirectUri(clientId, pkceRedirectUri)) {
            log.warn("PKCE 소셜 로그인 - 허용되지 않은 redirect_uri: {}", pkceRedirectUri);
            String loginPageUrl = buildPkceLoginPageUrl(clientId, pkceRedirectUri, codeChallenge,
                    codeChallengeMethod, state, "잘못된 인증 요청입니다. 앱에서 다시 시도해주세요.");
            getRedirectStrategy().sendRedirect(request, response, loginPageUrl);
            return;
        }

        if (!PkceUtils.isSupportedMethod(codeChallengeMethod)) {
            log.warn("PKCE 소셜 로그인 - 미지원 code_challenge_method: {}", codeChallengeMethod);
            String loginPageUrl = buildPkceLoginPageUrl(clientId, pkceRedirectUri, codeChallenge,
                    codeChallengeMethod, state, "잘못된 인증 요청입니다. 앱에서 다시 시도해주세요.");
            getRedirectStrategy().sendRedirect(request, response, loginPageUrl);
            return;
        }

        if (codeChallenge == null || codeChallenge.length() < 43) {
            log.warn("PKCE 소셜 로그인 - 유효하지 않은 code_challenge");
            String loginPageUrl = buildPkceLoginPageUrl(clientId, pkceRedirectUri, codeChallenge != null ? codeChallenge : "",
                    codeChallengeMethod, state, "잘못된 인증 요청입니다. 앱에서 다시 시도해주세요.");
            getRedirectStrategy().sendRedirect(request, response, loginPageUrl);
            return;
        }

        // 사용자 존재 여부 확인
        Optional<User> existingUser = userRepository.findByEmail(email);

        if (existingUser.isEmpty() || !existingUser.get().getIsActive()) {
            // 계정이 없거나 비활성 → login.html로 돌아가며 안내
            log.info("PKCE 소셜 로그인 - 계정 미존재 또는 비활성: {}", email);
            String loginPageUrl = buildPkceLoginPageUrl(clientId, pkceRedirectUri, codeChallenge,
                    codeChallengeMethod, state, "계정이 없습니다. 먼저 회원가입을 진행해주세요.");
            getRedirectStrategy().sendRedirect(request, response, loginPageUrl);
            return;
        }

        // 소셜 계정 연동 (아직 연동되지 않은 경우)
        linkSocialAccountIfNeeded(existingUser.get(), provider, providerId);

        // Authorization Code 발급
        String code = authorizationCodeStore.createAndStore(
                email, clientId, pkceRedirectUri, codeChallenge, codeChallengeMethod);

        log.info("PKCE 소셜 로그인 성공 - Email: {}, Provider: {}, client_id: {}", email, provider, clientId);

        // 데스크톱 앱으로 리다이렉트
        if (isCustomScheme(pkceRedirectUri)) {
            String callbackPageUrl = buildCallbackPageUrl(pkceRedirectUri, code, state, clientId);
            getRedirectStrategy().sendRedirect(request, response, callbackPageUrl);
        } else {
            String targetUrl = buildRedirectUrl(pkceRedirectUri, code, state);
            getRedirectStrategy().sendRedirect(request, response, targetUrl);
        }
    }

    /**
     * 일반 웹 플로우: 기존 동작 유지.
     */
    private void handleWebSocialLogin(HttpServletRequest request, HttpServletResponse response,
                                       CustomOAuth2User oAuth2User) throws IOException, ServletException {
        String email = oAuth2User.getEmail();
        String provider = oAuth2User.getProvider();

        if (oAuth2User.isNewUser()) {
            log.info("신규 OAuth2 사용자 - 비밀번호 설정 페이지로 이동: {}", email);

            String tempToken = jwtTokenProvider.generateTempToken(email, provider, oAuth2User.getProviderId());

            String baseUri = redirectUri.replace("/oauth/callback", "/oauth/setup-password");
            String targetUrl = UriComponentsBuilder.fromUriString(baseUri)
                    .queryParam("token", tempToken)
                    .queryParam("email", URLEncoder.encode(email, StandardCharsets.UTF_8))
                    .queryParam("name", URLEncoder.encode(oAuth2User.getUserName() != null ? oAuth2User.getUserName() : "", StandardCharsets.UTF_8))
                    .queryParam("mobile", URLEncoder.encode(oAuth2User.getMobile() != null ? oAuth2User.getMobile() : "", StandardCharsets.UTF_8))
                    .queryParam("provider", provider)
                    .build().toUriString();

            getRedirectStrategy().sendRedirect(request, response, targetUrl);
        } else {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + email));

            String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getEmail());

            log.info("OAuth2 로그인 성공 - Email: {}, Provider: {}", email, provider);

            String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                    .queryParam("accessToken", accessToken)
                    .queryParam("refreshToken", refreshToken)
                    .build().toUriString();

            getRedirectStrategy().sendRedirect(request, response, targetUrl);
        }
    }

    private void linkSocialAccountIfNeeded(User user, String provider, String providerId) {
        Optional<UserSocialAccount> existing =
                socialAccountRepository.findByProviderAndProviderId(provider, providerId);
        if (existing.isEmpty()) {
            UserSocialAccount socialAccount = UserSocialAccount.builder()
                    .userId(user.getId())
                    .provider(provider)
                    .providerId(providerId)
                    .build();
            socialAccountRepository.save(socialAccount);
            log.info("소셜 계정 자동 연동: email={}, provider={}", user.getEmail(), provider);
        }
    }

    private boolean isCustomScheme(String uri) {
        return uri != null && !uri.startsWith("http://") && !uri.startsWith("https://");
    }

    private String buildCallbackPageUrl(String pkceRedirectUri, String code, String state, String clientId) {
        StringBuilder url = new StringBuilder("/oauth/callback.html?");
        url.append("redirect_uri=").append(urlEncode(pkceRedirectUri));
        url.append("&code=").append(urlEncode(code));
        url.append("&client_id=").append(urlEncode(clientId));
        if (state != null && !state.isEmpty()) {
            url.append("&state=").append(urlEncode(state));
        }
        return url.toString();
    }

    private String buildRedirectUrl(String pkceRedirectUri, String code, String state) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(pkceRedirectUri)
                .queryParam("code", code);
        if (state != null && !state.isEmpty()) {
            builder.queryParam("state", state);
        }
        return builder.toUriString();
    }

    private String buildPkceLoginPageUrl(String clientId, String redirectUri, String codeChallenge,
                                          String codeChallengeMethod, String state, String errorMessage) {
        StringBuilder url = new StringBuilder("/oauth/login.html?");
        url.append("client_id=").append(urlEncode(clientId));
        url.append("&redirect_uri=").append(urlEncode(redirectUri));
        url.append("&code_challenge=").append(urlEncode(codeChallenge));
        url.append("&code_challenge_method=").append(urlEncode(codeChallengeMethod));
        if (state != null && !state.isEmpty()) {
            url.append("&state=").append(urlEncode(state));
        }
        url.append("&social_error=").append(urlEncode(errorMessage));
        return url.toString();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(name)) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private void clearCookie(HttpServletResponse response, String name) {
        Cookie cookie = new Cookie(name, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private String getJsonText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value != null && !value.isNull()) ? value.asText() : "";
    }
}
