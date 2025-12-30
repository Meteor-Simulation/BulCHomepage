package com.bulc.homepage.licensing.controller;

import com.bulc.homepage.licensing.domain.LicenseStatus;
import com.bulc.homepage.licensing.dto.*;
import com.bulc.homepage.licensing.exception.LicenseException;
import com.bulc.homepage.licensing.exception.LicenseException.ErrorCode;
import com.bulc.homepage.licensing.exception.LicenseExceptionHandler;
import com.bulc.homepage.licensing.service.LicenseService;
import com.bulc.homepage.entity.User;
import com.bulc.homepage.repository.UserRepository;
import com.bulc.homepage.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * LicenseController 슬라이스 테스트 (v1.1).
 *
 * @WebMvcTest로 Controller 레이어만 테스트.
 * LicenseService는 mock으로 처리하여 API 계약(contract)만 검증.
 */
@WebMvcTest(LicenseController.class)
@Import(LicenseExceptionHandler.class)
class LicenseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LicenseService licenseService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserRepository userRepository;

    private static final String LICENSE_KEY = "TEST-1234-5678-ABCD";
    private static final UUID LICENSE_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final String TEST_USER_EMAIL = "user";

    // ==========================================
    // v1.1 라이선스 검증/활성화 API 테스트
    // ==========================================

    @Nested
    @DisplayName("POST /api/licenses/validate")
    class ValidateEndpoint {

        @Test
        @WithMockUser(username = TEST_USER_EMAIL)
        @DisplayName("유효한 라이선스 검증 시 200 OK 반환")
        void shouldReturn200WhenValidLicense() throws Exception {
            // given
            User mockUser = User.builder().email(TEST_USER_EMAIL).build();
            given(userRepository.findByEmail(TEST_USER_EMAIL)).willReturn(Optional.of(mockUser));

            UUID userIdAsUUID = UUID.nameUUIDFromBytes(TEST_USER_EMAIL.getBytes(StandardCharsets.UTF_8));
            ValidationResponse response = ValidationResponse.success(
                    LICENSE_ID,
                    LicenseStatus.ACTIVE,
                    Instant.now().plus(30, ChronoUnit.DAYS),
                    List.of("core-simulation"),
                    "mock-session-token",  // v1.1.2: sessionToken (만료는 exp 클레임으로 판단)
                    "offline-token-abc",
                    Instant.now().plus(30, ChronoUnit.DAYS)
            );

            given(licenseService.validateAndActivateByUser(eq(userIdAsUUID), any(ValidateRequest.class)))
                    .willReturn(response);

            ValidateRequest request = new ValidateRequest(
                    null,  // productCode
                    PRODUCT_ID,
                    null,  // licenseId
                    "device-fingerprint-123",
                    "1.0.0",
                    "Windows 11",
                    null   // deviceDisplayName
            );

            // when & then
            mockMvc.perform(post("/api/licenses/validate")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(true))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.entitlements[0]").value("core-simulation"))
                    .andExpect(jsonPath("$.offlineToken").isNotEmpty());
        }

        @Test
        @WithMockUser(username = TEST_USER_EMAIL)
        @DisplayName("해당 제품의 라이선스가 없을 시 404 Not Found 반환")
        void shouldReturn404WhenLicenseNotFoundForProduct() throws Exception {
            // given
            User mockUser = User.builder().email(TEST_USER_EMAIL).build();
            given(userRepository.findByEmail(TEST_USER_EMAIL)).willReturn(Optional.of(mockUser));

            UUID userIdAsUUID = UUID.nameUUIDFromBytes(TEST_USER_EMAIL.getBytes(StandardCharsets.UTF_8));
            given(licenseService.validateAndActivateByUser(eq(userIdAsUUID), any(ValidateRequest.class)))
                    .willThrow(new LicenseException(ErrorCode.LICENSE_NOT_FOUND_FOR_PRODUCT));

            ValidateRequest request = new ValidateRequest(
                    null,  // productCode
                    PRODUCT_ID,
                    null,  // licenseId
                    "device-123",
                    "1.0.0",
                    "Windows",
                    null   // deviceDisplayName
            );

            // when & then
            mockMvc.perform(post("/api/licenses/validate")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("LICENSE_NOT_FOUND_FOR_PRODUCT"));
        }

        @Test
        @WithMockUser(username = TEST_USER_EMAIL)
        @DisplayName("productId 누락 시 400 Bad Request 반환")
        void shouldReturn400WhenProductIdMissing() throws Exception {
            // given
            User mockUser = User.builder().email(TEST_USER_EMAIL).build();
            given(userRepository.findByEmail(TEST_USER_EMAIL)).willReturn(Optional.of(mockUser));

            String invalidRequest = """
                    {
                        "deviceFingerprint": "device-123",
                        "clientVersion": "1.0.0"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/licenses/validate")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidRequest))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(username = TEST_USER_EMAIL)
        @DisplayName("deviceFingerprint 누락 시 400 Bad Request 반환")
        void shouldReturn400WhenDeviceFingerprintMissing() throws Exception {
            // given
            User mockUser = User.builder().email(TEST_USER_EMAIL).build();
            given(userRepository.findByEmail(TEST_USER_EMAIL)).willReturn(Optional.of(mockUser));

            String invalidRequest = """
                    {
                        "productId": "%s",
                        "clientVersion": "1.0.0"
                    }
                    """.formatted(PRODUCT_ID);

            // when & then
            mockMvc.perform(post("/api/licenses/validate")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidRequest))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(username = TEST_USER_EMAIL)
        @DisplayName("기기 수 초과 시 403 Forbidden + ACTIVATION_LIMIT_EXCEEDED 반환")
        void shouldReturn403WhenActivationLimitExceeded() throws Exception {
            // given
            User mockUser = User.builder().email(TEST_USER_EMAIL).build();
            given(userRepository.findByEmail(TEST_USER_EMAIL)).willReturn(Optional.of(mockUser));

            UUID userIdAsUUID = UUID.nameUUIDFromBytes(TEST_USER_EMAIL.getBytes(StandardCharsets.UTF_8));
            ValidationResponse response = ValidationResponse.failure(
                    "ACTIVATION_LIMIT_EXCEEDED",
                    "최대 기기 수를 초과했습니다"
            );
            given(licenseService.validateAndActivateByUser(eq(userIdAsUUID), any(ValidateRequest.class)))
                    .willReturn(response);

            ValidateRequest request = new ValidateRequest(
                    null,  // productCode
                    PRODUCT_ID,
                    null,  // licenseId
                    "device-123",
                    "1.0.0",
                    "Windows",
                    null   // deviceDisplayName
            );

            // when & then
            mockMvc.perform(post("/api/licenses/validate")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.valid").value(false))
                    .andExpect(jsonPath("$.errorCode").value("ACTIVATION_LIMIT_EXCEEDED"));
        }

        @Test
        @WithMockUser(username = TEST_USER_EMAIL)
        @DisplayName("동시 세션 초과 시 409 Conflict + CONCURRENT_SESSION_LIMIT_EXCEEDED 반환 (v1.1.1)")
        void shouldReturn409WhenConcurrentSessionLimitExceeded() throws Exception {
            // given
            User mockUser = User.builder().email(TEST_USER_EMAIL).build();
            given(userRepository.findByEmail(TEST_USER_EMAIL)).willReturn(Optional.of(mockUser));

            UUID userIdAsUUID = UUID.nameUUIDFromBytes(TEST_USER_EMAIL.getBytes(StandardCharsets.UTF_8));
            // v1.1.1: 동시 세션 초과 시 activeSessions 목록과 함께 반환
            ValidationResponse response = ValidationResponse.concurrentSessionLimitExceeded(
                    LICENSE_ID,
                    List.of(),  // 활성 세션 목록
                    2           // maxConcurrentSessions
            );
            given(licenseService.validateAndActivateByUser(eq(userIdAsUUID), any(ValidateRequest.class)))
                    .willReturn(response);

            ValidateRequest request = new ValidateRequest(
                    null,  // productCode
                    PRODUCT_ID,
                    null,  // licenseId
                    "device-123",
                    "1.0.0",
                    "Windows",
                    null   // deviceDisplayName
            );

            // when & then
            mockMvc.perform(post("/api/licenses/validate")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())  // v1.1.1: 409 Conflict
                    .andExpect(jsonPath("$.valid").value(false))
                    .andExpect(jsonPath("$.errorCode").value("CONCURRENT_SESSION_LIMIT_EXCEEDED"))
                    .andExpect(jsonPath("$.maxConcurrentSessions").value(2));
        }

        @Test
        @WithMockUser(username = TEST_USER_EMAIL)
        @DisplayName("만료된 라이선스 검증 시 403 Forbidden + LICENSE_EXPIRED 반환")
        void shouldReturn403WhenLicenseExpired() throws Exception {
            // given
            User mockUser = User.builder().email(TEST_USER_EMAIL).build();
            given(userRepository.findByEmail(TEST_USER_EMAIL)).willReturn(Optional.of(mockUser));

            UUID userIdAsUUID = UUID.nameUUIDFromBytes(TEST_USER_EMAIL.getBytes(StandardCharsets.UTF_8));
            ValidationResponse response = ValidationResponse.failure(
                    "LICENSE_EXPIRED",
                    "라이선스가 만료되었습니다"
            );
            given(licenseService.validateAndActivateByUser(eq(userIdAsUUID), any(ValidateRequest.class)))
                    .willReturn(response);

            ValidateRequest request = new ValidateRequest(
                    null,  // productCode
                    PRODUCT_ID,
                    null,  // licenseId
                    "device-123",
                    "1.0.0",
                    "Windows",
                    null   // deviceDisplayName
            );

            // when & then
            mockMvc.perform(post("/api/licenses/validate")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.valid").value(false))
                    .andExpect(jsonPath("$.errorCode").value("LICENSE_EXPIRED"));
        }

        @Test
        @WithMockUser(username = TEST_USER_EMAIL)
        @DisplayName("정지된 라이선스 검증 시 403 Forbidden + LICENSE_SUSPENDED 반환")
        void shouldReturn403WhenLicenseSuspended() throws Exception {
            // given
            User mockUser = User.builder().email(TEST_USER_EMAIL).build();
            given(userRepository.findByEmail(TEST_USER_EMAIL)).willReturn(Optional.of(mockUser));

            UUID userIdAsUUID = UUID.nameUUIDFromBytes(TEST_USER_EMAIL.getBytes(StandardCharsets.UTF_8));
            ValidationResponse response = ValidationResponse.failure(
                    "LICENSE_SUSPENDED",
                    "라이선스가 정지되었습니다"
            );
            given(licenseService.validateAndActivateByUser(eq(userIdAsUUID), any(ValidateRequest.class)))
                    .willReturn(response);

            ValidateRequest request = new ValidateRequest(
                    null,  // productCode
                    PRODUCT_ID,
                    null,  // licenseId
                    "device-123",
                    "1.0.0",
                    "Windows",
                    null   // deviceDisplayName
            );

            // when & then
            mockMvc.perform(post("/api/licenses/validate")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.valid").value(false))
                    .andExpect(jsonPath("$.errorCode").value("LICENSE_SUSPENDED"));
        }

        @Test
        @WithMockUser(username = TEST_USER_EMAIL)
        @DisplayName("회수된 라이선스 검증 시 403 Forbidden + LICENSE_REVOKED 반환")
        void shouldReturn403WhenLicenseRevoked() throws Exception {
            // given
            User mockUser = User.builder().email(TEST_USER_EMAIL).build();
            given(userRepository.findByEmail(TEST_USER_EMAIL)).willReturn(Optional.of(mockUser));

            UUID userIdAsUUID = UUID.nameUUIDFromBytes(TEST_USER_EMAIL.getBytes(StandardCharsets.UTF_8));
            ValidationResponse response = ValidationResponse.failure(
                    "LICENSE_REVOKED",
                    "라이선스가 회수되었습니다"
            );
            given(licenseService.validateAndActivateByUser(eq(userIdAsUUID), any(ValidateRequest.class)))
                    .willReturn(response);

            ValidateRequest request = new ValidateRequest(
                    null,  // productCode
                    PRODUCT_ID,
                    null,  // licenseId
                    "device-123",
                    "1.0.0",
                    "Windows",
                    null   // deviceDisplayName
            );

            // when & then
            mockMvc.perform(post("/api/licenses/validate")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.valid").value(false))
                    .andExpect(jsonPath("$.errorCode").value("LICENSE_REVOKED"));
        }
    }

    // ==========================================
    // v1.1 Heartbeat API 테스트
    // ==========================================

    @Nested
    @DisplayName("POST /api/licenses/heartbeat")
    class HeartbeatEndpoint {

        @Test
        @WithMockUser(username = TEST_USER_EMAIL)
        @DisplayName("정상 heartbeat 시 200 OK 반환")
        void shouldReturn200OnSuccessfulHeartbeat() throws Exception {
            // given
            User mockUser = User.builder().email(TEST_USER_EMAIL).build();
            given(userRepository.findByEmail(TEST_USER_EMAIL)).willReturn(Optional.of(mockUser));

            UUID userIdAsUUID = UUID.nameUUIDFromBytes(TEST_USER_EMAIL.getBytes(StandardCharsets.UTF_8));
            ValidationResponse response = ValidationResponse.success(
                    LICENSE_ID,
                    LicenseStatus.ACTIVE,
                    Instant.now().plus(30, ChronoUnit.DAYS),
                    List.of("core-simulation"),
                    "mock-session-token",  // v1.1.2: sessionToken (만료는 exp 클레임으로 판단)
                    "offline-token-abc",
                    Instant.now().plus(30, ChronoUnit.DAYS)
            );

            given(licenseService.heartbeatByUser(eq(userIdAsUUID), any(ValidateRequest.class)))
                    .willReturn(response);

            ValidateRequest request = new ValidateRequest(
                    null,  // productCode
                    PRODUCT_ID,
                    null,  // licenseId
                    "device-123",
                    "1.0.0",
                    "Windows 11",
                    null   // deviceDisplayName
            );

            // when & then
            mockMvc.perform(post("/api/licenses/heartbeat")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(true));
        }

        @Test
        @WithMockUser(username = TEST_USER_EMAIL)
        @DisplayName("활성화되지 않은 기기로 heartbeat 시 404 Not Found 반환")
        void shouldReturn404WhenActivationNotFound() throws Exception {
            // given
            User mockUser = User.builder().email(TEST_USER_EMAIL).build();
            given(userRepository.findByEmail(TEST_USER_EMAIL)).willReturn(Optional.of(mockUser));

            UUID userIdAsUUID = UUID.nameUUIDFromBytes(TEST_USER_EMAIL.getBytes(StandardCharsets.UTF_8));
            given(licenseService.heartbeatByUser(eq(userIdAsUUID), any(ValidateRequest.class)))
                    .willThrow(new LicenseException(ErrorCode.ACTIVATION_NOT_FOUND));

            ValidateRequest request = new ValidateRequest(
                    null,  // productCode
                    PRODUCT_ID,
                    null,  // licenseId
                    "device-123",
                    "1.0.0",
                    "Windows",
                    null   // deviceDisplayName
            );

            // when & then
            mockMvc.perform(post("/api/licenses/heartbeat")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("ACTIVATION_NOT_FOUND"));
        }

        @Test
        @WithMockUser(username = TEST_USER_EMAIL)
        @DisplayName("해당 제품의 라이선스가 없을 시 404 Not Found 반환")
        void shouldReturn404WhenLicenseNotFoundForProduct() throws Exception {
            // given
            User mockUser = User.builder().email(TEST_USER_EMAIL).build();
            given(userRepository.findByEmail(TEST_USER_EMAIL)).willReturn(Optional.of(mockUser));

            UUID userIdAsUUID = UUID.nameUUIDFromBytes(TEST_USER_EMAIL.getBytes(StandardCharsets.UTF_8));
            given(licenseService.heartbeatByUser(eq(userIdAsUUID), any(ValidateRequest.class)))
                    .willThrow(new LicenseException(ErrorCode.LICENSE_NOT_FOUND_FOR_PRODUCT));

            ValidateRequest request = new ValidateRequest(
                    null,  // productCode
                    PRODUCT_ID,
                    null,  // licenseId
                    "device-123",
                    "1.0.0",
                    "Windows",
                    null   // deviceDisplayName
            );

            // when & then
            mockMvc.perform(post("/api/licenses/heartbeat")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("LICENSE_NOT_FOUND_FOR_PRODUCT"));
        }

        @Test
        @WithMockUser(username = TEST_USER_EMAIL)
        @DisplayName("deviceFingerprint 누락 시 400 Bad Request 반환")
        void shouldReturn400WhenDeviceFingerprintMissing() throws Exception {
            // given
            User mockUser = User.builder().email(TEST_USER_EMAIL).build();
            given(userRepository.findByEmail(TEST_USER_EMAIL)).willReturn(Optional.of(mockUser));

            String invalidRequest = """
                    {
                        "productId": "%s",
                        "clientVersion": "1.0.0"
                    }
                    """.formatted(PRODUCT_ID);

            // when & then
            mockMvc.perform(post("/api/licenses/heartbeat")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidRequest))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==========================================
    // 기기 비활성화 API 테스트
    // ==========================================

    @Nested
    @DisplayName("DELETE /api/licenses/{licenseId}/activations/{deviceFingerprint}")
    class DeactivateEndpoint {

        @Test
        @WithMockUser(username = TEST_USER_EMAIL)
        @DisplayName("정상 비활성화 시 204 No Content 반환")
        void shouldReturn204OnSuccessfulDeactivation() throws Exception {
            // given
            User mockUser = User.builder().email(TEST_USER_EMAIL).build();
            given(userRepository.findByEmail(TEST_USER_EMAIL)).willReturn(Optional.of(mockUser));

            UUID userIdAsUUID = UUID.nameUUIDFromBytes(TEST_USER_EMAIL.getBytes(StandardCharsets.UTF_8));
            doNothing().when(licenseService).deactivateWithOwnerCheck(userIdAsUUID, LICENSE_ID, "device-123");

            // when & then
            mockMvc.perform(delete("/api/licenses/{licenseId}/activations/{deviceFingerprint}",
                            LICENSE_ID, "device-123")
                            .with(csrf()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @WithMockUser(username = TEST_USER_EMAIL)
        @DisplayName("존재하지 않는 활성화 비활성화 시 4xx 클라이언트 오류 반환")
        void shouldReturn4xxWhenActivationNotFound() throws Exception {
            // given
            User mockUser = User.builder().email(TEST_USER_EMAIL).build();
            given(userRepository.findByEmail(TEST_USER_EMAIL)).willReturn(Optional.of(mockUser));

            UUID userIdAsUUID = UUID.nameUUIDFromBytes(TEST_USER_EMAIL.getBytes(StandardCharsets.UTF_8));
            doThrow(new LicenseException(ErrorCode.ACTIVATION_NOT_FOUND))
                    .when(licenseService).deactivateWithOwnerCheck(eq(userIdAsUUID), any(UUID.class), eq("device-123"));

            // when & then
            mockMvc.perform(delete("/api/licenses/{licenseId}/activations/{deviceFingerprint}",
                            UUID.randomUUID(), "device-123")
                            .with(csrf()))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @WithMockUser(username = TEST_USER_EMAIL)
        @DisplayName("타인 소유 라이선스 비활성화 시 403 Forbidden 반환")
        void shouldReturn403WhenAccessDenied() throws Exception {
            // given
            User mockUser = User.builder().email(TEST_USER_EMAIL).build();
            given(userRepository.findByEmail(TEST_USER_EMAIL)).willReturn(Optional.of(mockUser));

            UUID userIdAsUUID = UUID.nameUUIDFromBytes(TEST_USER_EMAIL.getBytes(StandardCharsets.UTF_8));
            doThrow(new LicenseException(ErrorCode.ACCESS_DENIED))
                    .when(licenseService).deactivateWithOwnerCheck(eq(userIdAsUUID), any(UUID.class), eq("device-123"));

            // when & then
            mockMvc.perform(delete("/api/licenses/{licenseId}/activations/{deviceFingerprint}",
                            UUID.randomUUID(), "device-123")
                            .with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }

    // ==========================================
    // 라이선스 조회 API 테스트
    // ==========================================

    @Nested
    @DisplayName("GET /api/licenses/{licenseId}")
    class GetLicenseByIdEndpoint {

        @Test
        @WithMockUser(username = TEST_USER_EMAIL)
        @DisplayName("라이선스 ID로 조회 시 200 OK 반환")
        void shouldReturn200WithLicenseDetails() throws Exception {
            // given
            User mockUser = User.builder().email(TEST_USER_EMAIL).build();
            given(userRepository.findByEmail(TEST_USER_EMAIL)).willReturn(Optional.of(mockUser));

            UUID userIdAsUUID = UUID.nameUUIDFromBytes(TEST_USER_EMAIL.getBytes(StandardCharsets.UTF_8));
            LicenseResponse licenseResponse = createLicenseResponse();
            given(licenseService.getLicenseWithOwnerCheck(userIdAsUUID, LICENSE_ID))
                    .willReturn(licenseResponse);

            // when & then
            mockMvc.perform(get("/api/licenses/{licenseId}", LICENSE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(LICENSE_ID.toString()))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        @WithMockUser(username = TEST_USER_EMAIL)
        @DisplayName("존재하지 않는 라이선스 ID 조회 시 4xx 클라이언트 오류 반환")
        void shouldReturn4xxWhenLicenseNotFound() throws Exception {
            // given
            User mockUser = User.builder().email(TEST_USER_EMAIL).build();
            given(userRepository.findByEmail(TEST_USER_EMAIL)).willReturn(Optional.of(mockUser));

            UUID userIdAsUUID = UUID.nameUUIDFromBytes(TEST_USER_EMAIL.getBytes(StandardCharsets.UTF_8));
            given(licenseService.getLicenseWithOwnerCheck(eq(userIdAsUUID), any(UUID.class)))
                    .willThrow(new LicenseException(ErrorCode.LICENSE_NOT_FOUND));

            // when & then
            mockMvc.perform(get("/api/licenses/{licenseId}", UUID.randomUUID()))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @WithMockUser(username = TEST_USER_EMAIL)
        @DisplayName("타인 소유 라이선스 조회 시 403 Forbidden 반환")
        void shouldReturn403WhenAccessDenied() throws Exception {
            // given
            User mockUser = User.builder().email(TEST_USER_EMAIL).build();
            given(userRepository.findByEmail(TEST_USER_EMAIL)).willReturn(Optional.of(mockUser));

            UUID userIdAsUUID = UUID.nameUUIDFromBytes(TEST_USER_EMAIL.getBytes(StandardCharsets.UTF_8));
            given(licenseService.getLicenseWithOwnerCheck(eq(userIdAsUUID), any(UUID.class)))
                    .willThrow(new LicenseException(ErrorCode.ACCESS_DENIED));

            // when & then
            mockMvc.perform(get("/api/licenses/{licenseId}", UUID.randomUUID()))
                    .andExpect(status().isForbidden());
        }
    }

    // ==========================================
    // 인증 필수 테스트 (401 Unauthorized)
    // ==========================================

    @Nested
    @DisplayName("인증 없이 API 호출 시 401 Unauthorized")
    class AuthenticationRequired {

        @Test
        @DisplayName("POST /api/licenses/validate - 인증 없이 호출 시 401 반환")
        void validateShouldReturn401WhenNoAuth() throws Exception {
            ValidateRequest request = new ValidateRequest(
                    null,  // productCode
                    PRODUCT_ID,
                    null,  // licenseId
                    "device-123",
                    "1.0.0",
                    "Windows",
                    null   // deviceDisplayName
            );

            mockMvc.perform(post("/api/licenses/validate")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /api/licenses/heartbeat - 인증 없이 호출 시 401 반환")
        void heartbeatShouldReturn401WhenNoAuth() throws Exception {
            ValidateRequest request = new ValidateRequest(
                    null,  // productCode
                    PRODUCT_ID,
                    null,  // licenseId
                    "device-123",
                    "1.0.0",
                    "Windows",
                    null   // deviceDisplayName
            );

            mockMvc.perform(post("/api/licenses/heartbeat")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/licenses/{licenseId} - 인증 없이 호출 시 401 반환")
        void getLicenseShouldReturn401WhenNoAuth() throws Exception {
            mockMvc.perform(get("/api/licenses/{licenseId}", LICENSE_ID))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("DELETE /api/licenses/{licenseId}/activations/{deviceFingerprint} - 인증 없이 호출 시 401 반환")
        void deactivateShouldReturn401WhenNoAuth() throws Exception {
            mockMvc.perform(delete("/api/licenses/{licenseId}/activations/{deviceFingerprint}",
                            LICENSE_ID, "device-123")
                            .with(csrf()))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ==========================================
    // 헬퍼 메서드
    // ==========================================

    private LicenseResponse createLicenseResponse() {
        return new LicenseResponse(
                LICENSE_ID,
                com.bulc.homepage.licensing.domain.OwnerType.USER,
                UUID.nameUUIDFromBytes(TEST_USER_EMAIL.getBytes(StandardCharsets.UTF_8)),
                PRODUCT_ID,
                null,
                com.bulc.homepage.licensing.domain.LicenseType.SUBSCRIPTION,
                com.bulc.homepage.licensing.domain.UsageCategory.COMMERCIAL,
                LicenseStatus.ACTIVE,
                Instant.now(),
                Instant.now(),
                Instant.now().plus(30, ChronoUnit.DAYS),
                java.util.Map.of(
                        "maxActivations", 3,
                        "maxConcurrentSessions", 2,
                        "gracePeriodDays", 7,
                        "allowOfflineDays", 30,
                        "entitlements", List.of("core-simulation")
                ),
                List.of(),
                Instant.now(),
                Instant.now()
        );
    }
}
