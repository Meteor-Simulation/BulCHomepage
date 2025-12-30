package com.bulc.homepage.oauth2;

import com.bulc.homepage.security.JwtTokenProvider;
import jakarta.servlet.ServletException;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.oauth2.redirect-uri:http://localhost:3000/oauth/callback}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();

        String email = oAuth2User.getEmail();
        String provider = oAuth2User.getProvider();

        if (oAuth2User.isNewUser()) {
            // 신규 사용자 - 비밀번호 설정 페이지로 리다이렉트
            log.info("신규 OAuth2 사용자 - 비밀번호 설정 페이지로 이동: {}", email);

            // 임시 토큰 생성 (10분 유효)
            String tempToken = jwtTokenProvider.generateTempToken(email, provider, oAuth2User.getProviderId());

            // 비밀번호 설정 페이지 URL 생성
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
            // 기존 사용자 - 바로 로그인
            String accessToken = jwtTokenProvider.generateAccessToken(email);
            String refreshToken = jwtTokenProvider.generateRefreshToken(email);

            log.info("OAuth2 로그인 성공 - Email: {}, Provider: {}", email, provider);

            String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                    .queryParam("accessToken", accessToken)
                    .queryParam("refreshToken", refreshToken)
                    .build().toUriString();

            getRedirectStrategy().sendRedirect(request, response, targetUrl);
        }
    }
}
