package com.bulc.homepage.oauth2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final String PKCE_PARAMS_COOKIE_NAME = "pkce_params";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.oauth2.redirect-uri:http://localhost:3000/oauth/callback}")
    private String redirectUri;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String errorMessage = exception.getMessage();
        log.error("OAuth2 로그인 실패: {}", errorMessage);

        // PKCE 브릿지: 데스크톱 앱에서 소셜 로그인 실패 시 login.html로 복귀
        String pkceParamsRaw = getCookieValue(request, PKCE_PARAMS_COOKIE_NAME);
        if (pkceParamsRaw != null) {
            // JS의 encodeURIComponent()로 인코딩된 쿠키값을 디코딩
            String pkceParamsJson = java.net.URLDecoder.decode(pkceParamsRaw, java.nio.charset.StandardCharsets.UTF_8);
            clearCookie(response, PKCE_PARAMS_COOKIE_NAME);
            handlePkceFailure(request, response, pkceParamsJson, errorMessage);
            return;
        }

        // 일반 웹 플로우 (기존 동작)
        if (errorMessage != null && errorMessage.contains("authorization_request_not_found")) {
            String homeUrl = redirectUri.replace("/oauth/callback", "/");
            getRedirectStrategy().sendRedirect(request, response, homeUrl);
            return;
        }

        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("error", exception.getLocalizedMessage())
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private void handlePkceFailure(HttpServletRequest request, HttpServletResponse response,
                                    String pkceParamsJson, String errorMessage) throws IOException {
        log.info("PKCE 소셜 로그인 실패 - login.html로 복귀");

        try {
            JsonNode pkceParams = objectMapper.readTree(pkceParamsJson);
            String clientId = getJsonText(pkceParams, "client_id");
            String pkceRedirectUri = getJsonText(pkceParams, "redirect_uri");
            String codeChallenge = getJsonText(pkceParams, "code_challenge");
            String codeChallengeMethod = getJsonText(pkceParams, "code_challenge_method");
            String state = getJsonText(pkceParams, "state");

            String displayError = "소셜 로그인에 실패했습니다. 다시 시도해주세요.";
            if (errorMessage != null && errorMessage.contains("이메일 정보가 필요합니다")) {
                displayError = "이메일 정보가 필요합니다. 이메일 제공에 동의해주세요.";
            }

            StringBuilder url = new StringBuilder("/oauth/login.html?");
            url.append("client_id=").append(urlEncode(clientId));
            url.append("&redirect_uri=").append(urlEncode(pkceRedirectUri));
            url.append("&code_challenge=").append(urlEncode(codeChallenge));
            url.append("&code_challenge_method=").append(urlEncode(codeChallengeMethod));
            if (state != null && !state.isEmpty()) {
                url.append("&state=").append(urlEncode(state));
            }
            url.append("&social_error=").append(urlEncode(displayError));

            getRedirectStrategy().sendRedirect(request, response, url.toString());
        } catch (Exception e) {
            log.warn("PKCE 파라미터 파싱 실패, 기본 에러 페이지로 이동: {}", e.getMessage());
            String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                    .queryParam("error", errorMessage)
                    .build().toUriString();
            getRedirectStrategy().sendRedirect(request, response, targetUrl);
        }
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
