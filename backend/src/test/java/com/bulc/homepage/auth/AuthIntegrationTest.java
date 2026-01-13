package com.bulc.homepage.auth;

import com.bulc.homepage.entity.User;
import com.bulc.homepage.licensing.config.TestKeyConfig;
import com.bulc.homepage.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 기존 인증 API 통합 테스트.
 *
 * AuthService의 authenticateUser() 리팩토링 후
 * 기존 /api/auth/login 기능이 정상 동작하는지 검증합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestKeyConfig.class)
@Transactional
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String TEST_ROLE_CODE = "002";
    private static final String TEST_EMAIL = "auth-test@example.com";
    private static final String TEST_PASSWORD = "TestPassword123!";

    @BeforeEach
    void setUp() {
        // 테스트 역할 생성 (user_roles 테이블에 직접 삽입)
        jdbcTemplate.update(
                "MERGE INTO user_roles (code, role) KEY(code) VALUES (?, ?)",
                TEST_ROLE_CODE, "USER"
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
    // 기존 로그인 API 테스트
    // ==========================================

    @Nested
    @DisplayName("POST /api/auth/login")
    class LoginEndpoint {

        @Test
        @DisplayName("올바른 자격 증명으로 로그인 성공")
        void shouldLoginSuccessfullyWithValidCredentials() throws Exception {
            // given
            Map<String, String> loginRequest = Map.of(
                    "email", TEST_EMAIL,
                    "password", TEST_PASSWORD
            );

            // when & then
            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.data.expiresIn").isNumber())
                    .andExpect(jsonPath("$.data.user.email").value(TEST_EMAIL))
                    .andExpect(jsonPath("$.data.user.rolesCode").value(TEST_ROLE_CODE))
                    .andReturn();

            // JWT 토큰 형식 검증
            String responseBody = result.getResponse().getContentAsString();
            assertThat(responseBody).contains("accessToken");
        }

        @Test
        @DisplayName("잘못된 비밀번호로 로그인 실패")
        void shouldFailLoginWithWrongPassword() throws Exception {
            // given
            Map<String, String> loginRequest = Map.of(
                    "email", TEST_EMAIL,
                    "password", "wrong-password"
            );

            // when & then
            // 보안: 이메일 존재 여부를 노출하지 않기 위해 통합된 에러 메시지 사용
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(containsString("이메일 또는 비밀번호")));
        }

        @Test
        @DisplayName("존재하지 않는 이메일로 로그인 실패")
        void shouldFailLoginWithNonExistentEmail() throws Exception {
            // given
            Map<String, String> loginRequest = Map.of(
                    "email", "nonexistent@example.com",
                    "password", TEST_PASSWORD
            );

            // when & then
            // 보안: 이메일 존재 여부를 노출하지 않기 위해 통합된 에러 메시지 사용
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(containsString("이메일 또는 비밀번호")));
        }
    }

    // ==========================================
    // 토큰 갱신 API 테스트
    // ==========================================

    @Nested
    @DisplayName("POST /api/auth/refresh")
    class RefreshEndpoint {

        @Test
        @DisplayName("유효한 Refresh Token으로 새 토큰 발급")
        void shouldRefreshTokenSuccessfully() throws Exception {
            // given - 먼저 로그인해서 refresh token 획득
            Map<String, String> loginRequest = Map.of(
                    "email", TEST_EMAIL,
                    "password", TEST_PASSWORD
            );

            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andReturn();

            String loginResponse = loginResult.getResponse().getContentAsString();
            String refreshToken = extractJsonValue(loginResponse, "refreshToken");

            // when & then
            Map<String, String> refreshRequest = Map.of(
                    "refreshToken", refreshToken
            );

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(refreshRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.user.email").value(TEST_EMAIL));
        }

        @Test
        @DisplayName("유효하지 않은 Refresh Token으로 갱신 실패")
        void shouldFailRefreshWithInvalidToken() throws Exception {
            // given
            Map<String, String> refreshRequest = Map.of(
                    "refreshToken", "invalid-refresh-token"
            );

            // when & then
            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(refreshRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("[RTR] 토큰 갱신 후 새 Refresh Token으로 재갱신 성공")
        void shouldRefreshWithNewTokenAfterRotation() throws Exception {
            // given - 먼저 로그인해서 refresh token 획득
            Map<String, String> loginRequest = Map.of(
                    "email", TEST_EMAIL,
                    "password", TEST_PASSWORD
            );

            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andReturn();

            String loginResponse = loginResult.getResponse().getContentAsString();
            String refreshToken1 = extractJsonValue(loginResponse, "refreshToken");
            assertThat(refreshToken1).isNotNull();

            // when - 첫 번째 갱신
            Map<String, String> refreshRequest1 = Map.of("refreshToken", refreshToken1);

            MvcResult refresh1Result = mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(refreshRequest1)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andReturn();

            String refresh1Response = refresh1Result.getResponse().getContentAsString();
            String refreshToken2 = extractJsonValue(refresh1Response, "refreshToken");
            assertThat(refreshToken2).isNotNull();

            // then - 새 토큰으로 다시 갱신 성공 (RTR 핵심 검증)
            Map<String, String> refreshRequest2 = Map.of("refreshToken", refreshToken2);

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(refreshRequest2)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
        }

        @Test
        @DisplayName("[RTR] 토큰 탈취 감지 - 이전 토큰 재사용 시 실패")
        void shouldDetectTokenTheftWhenOldTokenReused() throws Exception {
            // given - 먼저 로그인해서 refresh token 획득
            Map<String, String> loginRequest = Map.of(
                    "email", TEST_EMAIL,
                    "password", TEST_PASSWORD
            );

            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andReturn();

            String loginResponse = loginResult.getResponse().getContentAsString();
            String oldRefreshToken = extractJsonValue(loginResponse, "refreshToken");
            assertThat(oldRefreshToken).isNotNull();

            // when - 토큰 갱신 (RTR로 인해 새 토큰 발급, DB의 토큰이 교체됨)
            Map<String, String> refreshRequest = Map.of("refreshToken", oldRefreshToken);

            MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(refreshRequest)))
                    .andExpect(status().isOk())
                    .andReturn();

            // 새 토큰 확인
            String newRefreshToken = extractJsonValue(refreshResult.getResponse().getContentAsString(), "refreshToken");
            assertThat(newRefreshToken).isNotNull();

            // then - 이전(구) 토큰 재사용 시 실패 (토큰 탈취로 간주)
            // DB에는 새 토큰만 저장되어 있으므로 구 토큰은 거부됨
            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(refreshRequest)))  // 구 토큰 사용
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ==========================================
    // AuthService.authenticateUser() 단위 검증
    // ==========================================

    @Nested
    @DisplayName("AuthService.authenticateUser()")
    class AuthenticateUserMethod {

        @Autowired
        private com.bulc.homepage.service.AuthService authService;

        @Test
        @DisplayName("올바른 자격 증명으로 User 객체 반환")
        void shouldReturnUserForValidCredentials() {
            // when
            User user = authService.authenticateUser(TEST_EMAIL, TEST_PASSWORD);

            // then
            assertThat(user).isNotNull();
            assertThat(user.getEmail()).isEqualTo(TEST_EMAIL);
            assertThat(user.getRolesCode()).isEqualTo(TEST_ROLE_CODE);
        }

        @Test
        @DisplayName("잘못된 비밀번호로 예외 발생")
        void shouldThrowExceptionForWrongPassword() {
            // when & then
            // 보안: 통합된 에러 메시지 사용
            assertThatThrownBy(() -> authService.authenticateUser(TEST_EMAIL, "wrong-password"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("이메일 또는 비밀번호");
        }

        @Test
        @DisplayName("존재하지 않는 이메일로 예외 발생")
        void shouldThrowExceptionForNonExistentEmail() {
            // when & then
            // 보안: 통합된 에러 메시지 사용
            assertThatThrownBy(() -> authService.authenticateUser("nonexistent@example.com", TEST_PASSWORD))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("이메일 또는 비밀번호");
        }
    }

    // ==========================================
    // 헬퍼 메서드
    // ==========================================

    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) return null;
        start += pattern.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}
