package com.bulc.homepage.oauth;

import com.bulc.homepage.entity.User;
import com.bulc.homepage.licensing.config.TestKeyConfig;
import com.bulc.homepage.repository.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OAuth 2.0 with PKCE 통합 테스트.
 *
 * 전체 OAuth 플로우를 엔드투엔드로 검증합니다:
 * 1. GET /oauth/authorize → 로그인 정보 반환
 * 2. POST /oauth/login-process → Authorization Code 발급 + 리다이렉트
 * 3. POST /oauth/token → JWT Access Token 발급
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestKeyConfig.class)
@Transactional
class OAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthorizationCodeStore codeStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String TEST_ROLE_CODE = "001";
    private static final String TEST_EMAIL = "oauth-test@example.com";
    private static final String TEST_PASSWORD = "TestPassword123!";
    private static final String CLIENT_ID = "meteor-app";
    private static final String REDIRECT_URI = "meteor://oauth/callback";

    @BeforeEach
    void setUp() {
        // 테스트 역할 생성 (user_roles 테이블에 직접 삽입)
        jdbcTemplate.update(
                "MERGE INTO user_roles (code, role) KEY(code) VALUES (?, ?)",
                TEST_ROLE_CODE, "ADMIN"
        );

        // 테스트 사용자 생성
        if (userRepository.findByEmail(TEST_EMAIL).isEmpty()) {
            User user = User.builder()
                    .email(TEST_EMAIL)
                    .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                    .rolesCode(TEST_ROLE_CODE)
                    .build();
            userRepository.save(user);
        }
    }

    // ==========================================
    // /oauth/authorize 테스트
    // ==========================================

    @Nested
    @DisplayName("GET /oauth/authorize")
    class AuthorizeEndpoint {

        @Test
        @DisplayName("유효한 요청 시 로그인 정보 반환")
        void shouldReturnLoginInfoForValidRequest() throws Exception {
            // given
            String codeVerifier = PkceUtils.generateCodeVerifier();
            String codeChallenge = PkceUtils.generateCodeChallenge(codeVerifier);

            // when & then
            mockMvc.perform(get("/oauth/authorize")
                            .param("client_id", CLIENT_ID)
                            .param("redirect_uri", REDIRECT_URI)
                            .param("response_type", "code")
                            .param("code_challenge", codeChallenge)
                            .param("code_challenge_method", "S256")
                            .param("state", "random-state-value"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.action").value("login_required"))
                    .andExpect(jsonPath("$.login_endpoint").value("/oauth/login-process"))
                    .andExpect(jsonPath("$.oauth_params.client_id").value(CLIENT_ID))
                    .andExpect(jsonPath("$.oauth_params.redirect_uri").value(REDIRECT_URI))
                    .andExpect(jsonPath("$.oauth_params.code_challenge").value(codeChallenge))
                    .andExpect(jsonPath("$.oauth_params.state").value("random-state-value"));
        }

        @Test
        @DisplayName("response_type이 code가 아니면 에러")
        void shouldRejectInvalidResponseType() throws Exception {
            // given
            String codeChallenge = PkceUtils.generateCodeChallenge(PkceUtils.generateCodeVerifier());

            // when & then
            mockMvc.perform(get("/oauth/authorize")
                            .param("client_id", CLIENT_ID)
                            .param("redirect_uri", REDIRECT_URI)
                            .param("response_type", "token")
                            .param("code_challenge", codeChallenge))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("unsupported_response_type"));
        }

        @Test
        @DisplayName("지원하지 않는 code_challenge_method면 에러")
        void shouldRejectUnsupportedCodeChallengeMethod() throws Exception {
            // when & then
            mockMvc.perform(get("/oauth/authorize")
                            .param("client_id", CLIENT_ID)
                            .param("redirect_uri", REDIRECT_URI)
                            .param("response_type", "code")
                            .param("code_challenge", "some-challenge-value-that-is-at-least-43-chars")
                            .param("code_challenge_method", "SHA512"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("invalid_request"))
                    .andExpect(jsonPath("$.error_description").value(containsString("code_challenge_method")));
        }

        @Test
        @DisplayName("code_challenge가 43자 미만이면 에러")
        void shouldRejectShortCodeChallenge() throws Exception {
            // when & then
            mockMvc.perform(get("/oauth/authorize")
                            .param("client_id", CLIENT_ID)
                            .param("redirect_uri", REDIRECT_URI)
                            .param("response_type", "code")
                            .param("code_challenge", "too-short"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("invalid_request"))
                    .andExpect(jsonPath("$.error_description").value(containsString("43")));
        }

        @Test
        @DisplayName("유효하지 않은 redirect_uri면 에러")
        void shouldRejectInvalidRedirectUri() throws Exception {
            // given
            String codeChallenge = PkceUtils.generateCodeChallenge(PkceUtils.generateCodeVerifier());

            // when & then
            mockMvc.perform(get("/oauth/authorize")
                            .param("client_id", CLIENT_ID)
                            .param("redirect_uri", "https://evil.com/callback")
                            .param("response_type", "code")
                            .param("code_challenge", codeChallenge))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("invalid_request"))
                    .andExpect(jsonPath("$.error_description").value(containsString("redirect_uri")));
        }

        @Test
        @DisplayName("localhost redirect_uri는 허용 (동적 포트)")
        void shouldAllowLocalhostRedirectUri() throws Exception {
            // given
            String codeChallenge = PkceUtils.generateCodeChallenge(PkceUtils.generateCodeVerifier());

            // when & then - 와일드카드 패턴 (http://localhost:*/oauth/callback)으로 동적 포트 허용
            mockMvc.perform(get("/oauth/authorize")
                            .param("client_id", CLIENT_ID)
                            .param("redirect_uri", "http://localhost:8080/oauth/callback")
                            .param("response_type", "code")
                            .param("code_challenge", codeChallenge))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.action").value("login_required"));
        }
    }

    // ==========================================
    // /oauth/login-process 테스트
    // ==========================================

    @Nested
    @DisplayName("POST /oauth/login-process")
    class LoginProcessEndpoint {

        @Test
        @DisplayName("인증 성공 시 Authorization Code와 함께 리다이렉트")
        void shouldRedirectWithAuthorizationCodeOnSuccess() throws Exception {
            // given
            String codeVerifier = PkceUtils.generateCodeVerifier();
            String codeChallenge = PkceUtils.generateCodeChallenge(codeVerifier);

            // when
            MvcResult result = mockMvc.perform(post("/oauth/login-process")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("email", TEST_EMAIL)
                            .param("password", TEST_PASSWORD)
                            .param("client_id", CLIENT_ID)
                            .param("redirect_uri", REDIRECT_URI)
                            .param("code_challenge", codeChallenge)
                            .param("code_challenge_method", "S256")
                            .param("state", "test-state"))
                    .andExpect(status().isFound())
                    .andReturn();

            // then - custom scheme인 경우 callback.html 페이지로 리다이렉트
            String location = result.getResponse().getHeader("Location");
            assertThat(location).startsWith("/oauth/callback.html");
            assertThat(location).contains("code=");
            assertThat(location).contains("state=test-state");
            assertThat(location).contains("redirect_uri="); // 원래 redirect_uri가 포함됨
        }

        @Test
        @DisplayName("잘못된 비밀번호로 인증 실패")
        void shouldRejectInvalidPassword() throws Exception {
            // given
            String codeChallenge = PkceUtils.generateCodeChallenge(PkceUtils.generateCodeVerifier());

            // when & then
            mockMvc.perform(post("/oauth/login-process")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("email", TEST_EMAIL)
                            .param("password", "wrong-password")
                            .param("client_id", CLIENT_ID)
                            .param("redirect_uri", REDIRECT_URI)
                            .param("code_challenge", codeChallenge))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("access_denied"));
        }

        @Test
        @DisplayName("존재하지 않는 사용자로 인증 실패")
        void shouldRejectNonExistentUser() throws Exception {
            // given
            String codeChallenge = PkceUtils.generateCodeChallenge(PkceUtils.generateCodeVerifier());

            // when & then
            mockMvc.perform(post("/oauth/login-process")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("email", "nonexistent@example.com")
                            .param("password", "any-password")
                            .param("client_id", CLIENT_ID)
                            .param("redirect_uri", REDIRECT_URI)
                            .param("code_challenge", codeChallenge))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("access_denied"));
        }
    }

    // ==========================================
    // /oauth/token 테스트
    // ==========================================

    @Nested
    @DisplayName("POST /oauth/token")
    class TokenEndpoint {

        @Test
        @DisplayName("유효한 Authorization Code로 JWT 토큰 발급")
        void shouldIssueJwtTokenForValidAuthorizationCode() throws Exception {
            // given - Authorization Code 발급
            String codeVerifier = PkceUtils.generateCodeVerifier();
            String codeChallenge = PkceUtils.generateCodeChallenge(codeVerifier);

            String authorizationCode = codeStore.createAndStore(
                    TEST_EMAIL,
                    CLIENT_ID,
                    REDIRECT_URI,
                    codeChallenge,
                    "S256"
            );

            // when & then
            mockMvc.perform(post("/oauth/token")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("grant_type", "authorization_code")
                            .param("code", authorizationCode)
                            .param("redirect_uri", REDIRECT_URI)
                            .param("client_id", CLIENT_ID)
                            .param("code_verifier", codeVerifier))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.access_token").isNotEmpty())
                    .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                    .andExpect(jsonPath("$.token_type").value("Bearer"))
                    .andExpect(jsonPath("$.expires_in").isNumber())
                    .andExpect(jsonPath("$.user.email").value(TEST_EMAIL));
        }

        @Test
        @DisplayName("잘못된 code_verifier로 토큰 발급 실패 (PKCE 검증 실패)")
        void shouldRejectInvalidCodeVerifier() throws Exception {
            // given
            String correctVerifier = PkceUtils.generateCodeVerifier();
            String wrongVerifier = PkceUtils.generateCodeVerifier();
            String codeChallenge = PkceUtils.generateCodeChallenge(correctVerifier);

            String authorizationCode = codeStore.createAndStore(
                    TEST_EMAIL,
                    CLIENT_ID,
                    REDIRECT_URI,
                    codeChallenge,
                    "S256"
            );

            // when & then
            mockMvc.perform(post("/oauth/token")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("grant_type", "authorization_code")
                            .param("code", authorizationCode)
                            .param("redirect_uri", REDIRECT_URI)
                            .param("client_id", CLIENT_ID)
                            .param("code_verifier", wrongVerifier))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("invalid_grant"))
                    .andExpect(jsonPath("$.error_description").value(containsString("PKCE")));
        }

        @Test
        @DisplayName("이미 사용된 Authorization Code로 토큰 발급 실패")
        void shouldRejectUsedAuthorizationCode() throws Exception {
            // given
            String codeVerifier = PkceUtils.generateCodeVerifier();
            String codeChallenge = PkceUtils.generateCodeChallenge(codeVerifier);

            String authorizationCode = codeStore.createAndStore(
                    TEST_EMAIL,
                    CLIENT_ID,
                    REDIRECT_URI,
                    codeChallenge,
                    "S256"
            );

            // 첫 번째 토큰 발급 (성공)
            mockMvc.perform(post("/oauth/token")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("grant_type", "authorization_code")
                            .param("code", authorizationCode)
                            .param("redirect_uri", REDIRECT_URI)
                            .param("client_id", CLIENT_ID)
                            .param("code_verifier", codeVerifier))
                    .andExpect(status().isOk());

            // when & then - 두 번째 시도 (실패)
            mockMvc.perform(post("/oauth/token")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("grant_type", "authorization_code")
                            .param("code", authorizationCode)
                            .param("redirect_uri", REDIRECT_URI)
                            .param("client_id", CLIENT_ID)
                            .param("code_verifier", codeVerifier))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("invalid_grant"));
        }

        @Test
        @DisplayName("redirect_uri 불일치 시 토큰 발급 실패")
        void shouldRejectMismatchedRedirectUri() throws Exception {
            // given
            String codeVerifier = PkceUtils.generateCodeVerifier();
            String codeChallenge = PkceUtils.generateCodeChallenge(codeVerifier);

            String authorizationCode = codeStore.createAndStore(
                    TEST_EMAIL,
                    CLIENT_ID,
                    REDIRECT_URI,
                    codeChallenge,
                    "S256"
            );

            // when & then
            mockMvc.perform(post("/oauth/token")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("grant_type", "authorization_code")
                            .param("code", authorizationCode)
                            .param("redirect_uri", "different://callback")
                            .param("client_id", CLIENT_ID)
                            .param("code_verifier", codeVerifier))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("invalid_grant"))
                    .andExpect(jsonPath("$.error_description").value(containsString("redirect_uri")));
        }

        @Test
        @DisplayName("지원하지 않는 grant_type 에러")
        void shouldRejectUnsupportedGrantType() throws Exception {
            // when & then
            mockMvc.perform(post("/oauth/token")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("grant_type", "password")
                            .param("code", "some-code")
                            .param("redirect_uri", REDIRECT_URI)
                            .param("client_id", CLIENT_ID)
                            .param("code_verifier", PkceUtils.generateCodeVerifier()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("unsupported_grant_type"));
        }

        @Test
        @DisplayName("code_verifier가 43자 미만이면 에러")
        void shouldRejectShortCodeVerifier() throws Exception {
            // when & then
            mockMvc.perform(post("/oauth/token")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("grant_type", "authorization_code")
                            .param("code", "some-code")
                            .param("redirect_uri", REDIRECT_URI)
                            .param("client_id", CLIENT_ID)
                            .param("code_verifier", "too-short"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("invalid_request"))
                    .andExpect(jsonPath("$.error_description").value(containsString("43")));
        }
    }

    // ==========================================
    // /oauth/token/refresh 테스트
    // ==========================================

    @Nested
    @DisplayName("POST /oauth/token/refresh")
    class TokenRefreshEndpoint {

        @Test
        @DisplayName("유효한 Refresh Token으로 새 토큰 발급")
        void shouldIssueNewTokensForValidRefreshToken() throws Exception {
            // given - 먼저 토큰 발급
            String codeVerifier = PkceUtils.generateCodeVerifier();
            String codeChallenge = PkceUtils.generateCodeChallenge(codeVerifier);

            String authorizationCode = codeStore.createAndStore(
                    TEST_EMAIL,
                    CLIENT_ID,
                    REDIRECT_URI,
                    codeChallenge,
                    "S256"
            );

            MvcResult tokenResult = mockMvc.perform(post("/oauth/token")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("grant_type", "authorization_code")
                            .param("code", authorizationCode)
                            .param("redirect_uri", REDIRECT_URI)
                            .param("client_id", CLIENT_ID)
                            .param("code_verifier", codeVerifier))
                    .andExpect(status().isOk())
                    .andReturn();

            String responseBody = tokenResult.getResponse().getContentAsString();
            String refreshToken = extractJsonValue(responseBody, "refresh_token");

            // when & then
            mockMvc.perform(post("/oauth/token/refresh")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("grant_type", "refresh_token")
                            .param("refresh_token", refreshToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.access_token").isNotEmpty())
                    .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                    .andExpect(jsonPath("$.token_type").value("Bearer"));
        }

        @Test
        @DisplayName("잘못된 Refresh Token으로 토큰 갱신 실패")
        void shouldRejectInvalidRefreshToken() throws Exception {
            // when & then
            mockMvc.perform(post("/oauth/token/refresh")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("grant_type", "refresh_token")
                            .param("refresh_token", "invalid-refresh-token"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("invalid_grant"));
        }

        @Test
        @DisplayName("잘못된 grant_type으로 토큰 갱신 실패")
        void shouldRejectWrongGrantType() throws Exception {
            // when & then
            mockMvc.perform(post("/oauth/token/refresh")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("grant_type", "authorization_code")
                            .param("refresh_token", "some-token"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("unsupported_grant_type"));
        }
    }

    // ==========================================
    // 전체 플로우 테스트
    // ==========================================

    @Nested
    @DisplayName("Full OAuth Flow")
    class FullOAuthFlow {

        @Test
        @DisplayName("전체 OAuth 2.0 with PKCE 플로우")
        void shouldCompleteFullOAuthFlowWithPkce() throws Exception {
            // Step 1: PKCE 값 생성 (클라이언트에서 수행)
            String codeVerifier = PkceUtils.generateCodeVerifier();
            String codeChallenge = PkceUtils.generateCodeChallenge(codeVerifier);
            String state = "csrf-protection-state";

            // Step 2: GET /oauth/authorize - 로그인 정보 요청
            mockMvc.perform(get("/oauth/authorize")
                            .param("client_id", CLIENT_ID)
                            .param("redirect_uri", REDIRECT_URI)
                            .param("response_type", "code")
                            .param("code_challenge", codeChallenge)
                            .param("code_challenge_method", "S256")
                            .param("state", state))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.action").value("login_required"));

            // Step 3: POST /oauth/login-process - 사용자 인증
            MvcResult loginResult = mockMvc.perform(post("/oauth/login-process")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("email", TEST_EMAIL)
                            .param("password", TEST_PASSWORD)
                            .param("client_id", CLIENT_ID)
                            .param("redirect_uri", REDIRECT_URI)
                            .param("code_challenge", codeChallenge)
                            .param("code_challenge_method", "S256")
                            .param("state", state))
                    .andExpect(status().isFound())
                    .andReturn();

            // Authorization Code 추출
            String location = loginResult.getResponse().getHeader("Location");
            String authorizationCode = extractQueryParam(location, "code");
            assertThat(authorizationCode).isNotNull();

            // Step 4: POST /oauth/token - 토큰 교환
            MvcResult tokenResult = mockMvc.perform(post("/oauth/token")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("grant_type", "authorization_code")
                            .param("code", authorizationCode)
                            .param("redirect_uri", REDIRECT_URI)
                            .param("client_id", CLIENT_ID)
                            .param("code_verifier", codeVerifier))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.access_token").isNotEmpty())
                    .andExpect(jsonPath("$.token_type").value("Bearer"))
                    .andReturn();

            // JWT 토큰 검증
            String responseBody = tokenResult.getResponse().getContentAsString();
            String accessToken = extractJsonValue(responseBody, "access_token");
            assertThat(accessToken).isNotNull();
            assertThat(accessToken.split("\\.")).hasSize(3); // JWT 형식 (header.payload.signature)
        }
    }

    // ==========================================
    // 헬퍼 메서드
    // ==========================================

    private String extractQueryParam(String url, String paramName) {
        if (url == null) return null;
        String[] parts = url.split("\\?");
        if (parts.length < 2) return null;

        String query = parts[1];
        for (String param : query.split("&")) {
            String[] keyValue = param.split("=");
            if (keyValue.length == 2 && keyValue[0].equals(paramName)) {
                return keyValue[1];
            }
        }
        return null;
    }

    private String extractJsonValue(String json, String key) {
        // 간단한 JSON 파싱 (테스트 목적)
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) return null;
        start += pattern.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}
