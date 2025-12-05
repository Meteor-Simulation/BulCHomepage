package com.bulc.homepage.licensing.controller;

import com.bulc.homepage.licensing.domain.LicenseStatus;
import com.bulc.homepage.licensing.dto.*;
import com.bulc.homepage.licensing.exception.LicenseException;
import com.bulc.homepage.licensing.exception.LicenseException.ErrorCode;
import com.bulc.homepage.licensing.exception.LicenseExceptionHandler;
import com.bulc.homepage.licensing.service.LicenseService;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
 * LicenseController 슬라이스 테스트.
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

    private static final String LICENSE_KEY = "TEST-1234-5678-ABCD";
    private static final UUID LICENSE_ID = UUID.randomUUID();

    // ==========================================
    // 라이선스 검증/활성화 API 테스트
    // ==========================================

    @Nested
    @DisplayName("POST /api/licenses/{licenseKey}/validate")
    class ValidateEndpoint {

        @Test
        @WithMockUser
        @DisplayName("유효한 라이선스 검증 시 200 OK 반환")
        void shouldReturn200WhenValidLicense() throws Exception {
            // given
            ValidationResponse response = ValidationResponse.success(
                    LICENSE_ID,
                    LicenseStatus.ACTIVE,
                    Instant.now().plus(30, ChronoUnit.DAYS),
                    List.of("core-simulation"),
                    "offline-token-abc",
                    Instant.now().plus(30, ChronoUnit.DAYS)
            );

            given(licenseService.validateAndActivate(eq(LICENSE_KEY), any(ActivationRequest.class)))
                    .willReturn(response);

            ActivationRequest request = new ActivationRequest(
                    "device-fingerprint-123",
                    "1.0.0",
                    "Windows 11",
                    "192.168.1.1"
            );

            // when & then
            mockMvc.perform(post("/api/licenses/{licenseKey}/validate", LICENSE_KEY)
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
        @WithMockUser
        @DisplayName("만료된 라이선스 검증 시 403 Forbidden 반환")
        void shouldReturn403WhenLicenseExpired() throws Exception {
            // given
            ValidationResponse response = ValidationResponse.failure(
                    "LICENSE_EXPIRED",
                    "라이선스가 만료되었습니다"
            );

            given(licenseService.validateAndActivate(eq(LICENSE_KEY), any(ActivationRequest.class)))
                    .willReturn(response);

            ActivationRequest request = new ActivationRequest(
                    "device-123", "1.0.0", "Windows", "10.0.0.1"
            );

            // when & then
            mockMvc.perform(post("/api/licenses/{licenseKey}/validate", LICENSE_KEY)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.valid").value(false))
                    .andExpect(jsonPath("$.errorCode").value("LICENSE_EXPIRED"));
        }

        @Test
        @WithMockUser
        @DisplayName("정지된 라이선스 검증 시 403 Forbidden 반환")
        void shouldReturn403WhenLicenseSuspended() throws Exception {
            // given
            ValidationResponse response = ValidationResponse.failure(
                    "LICENSE_SUSPENDED",
                    "라이선스가 정지되었습니다"
            );

            given(licenseService.validateAndActivate(eq(LICENSE_KEY), any(ActivationRequest.class)))
                    .willReturn(response);

            ActivationRequest request = new ActivationRequest(
                    "device-123", "1.0.0", "Windows", "10.0.0.1"
            );

            // when & then
            mockMvc.perform(post("/api/licenses/{licenseKey}/validate", LICENSE_KEY)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("LICENSE_SUSPENDED"));
        }

        @Test
        @WithMockUser
        @DisplayName("존재하지 않는 라이선스 키 검증 시 404 반환")
        void shouldReturn404WhenLicenseNotFound() throws Exception {
            // given
            given(licenseService.validateAndActivate(eq(LICENSE_KEY), any(ActivationRequest.class)))
                    .willThrow(new LicenseException(ErrorCode.LICENSE_NOT_FOUND));

            ActivationRequest request = new ActivationRequest(
                    "device-123", "1.0.0", "Windows", "10.0.0.1"
            );

            // when & then
            mockMvc.perform(post("/api/licenses/{licenseKey}/validate", LICENSE_KEY)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser
        @DisplayName("deviceFingerprint 누락 시 400 Bad Request 반환")
        void shouldReturn400WhenDeviceFingerprintMissing() throws Exception {
            // given - deviceFingerprint가 빈 문자열
            String invalidRequest = """
                    {
                        "deviceFingerprint": "",
                        "clientVersion": "1.0.0"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/licenses/{licenseKey}/validate", LICENSE_KEY)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidRequest))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==========================================
    // Heartbeat API 테스트
    // ==========================================

    @Nested
    @DisplayName("POST /api/licenses/{licenseKey}/heartbeat")
    class HeartbeatEndpoint {

        @Test
        @WithMockUser
        @DisplayName("정상 heartbeat 시 200 OK 반환")
        void shouldReturn200OnSuccessfulHeartbeat() throws Exception {
            // given
            ValidationResponse response = ValidationResponse.success(
                    LICENSE_ID,
                    LicenseStatus.ACTIVE,
                    Instant.now().plus(30, ChronoUnit.DAYS),
                    List.of("core-simulation"),
                    "offline-token-abc",
                    Instant.now().plus(30, ChronoUnit.DAYS)
            );

            given(licenseService.validateAndActivate(eq(LICENSE_KEY), any(ActivationRequest.class)))
                    .willReturn(response);

            ActivationRequest request = new ActivationRequest(
                    "device-123", "1.0.0", "Windows 11", "192.168.1.1"
            );

            // when & then
            mockMvc.perform(post("/api/licenses/{licenseKey}/heartbeat", LICENSE_KEY)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(true));
        }

        @Test
        @WithMockUser
        @DisplayName("만료된 라이선스로 heartbeat 시 403 반환")
        void shouldReturn403WhenLicenseExpiredOnHeartbeat() throws Exception {
            // given
            ValidationResponse response = ValidationResponse.failure(
                    "LICENSE_EXPIRED",
                    "라이선스가 만료되었습니다"
            );

            given(licenseService.validateAndActivate(eq(LICENSE_KEY), any(ActivationRequest.class)))
                    .willReturn(response);

            ActivationRequest request = new ActivationRequest(
                    "device-123", "1.0.0", "Windows", "10.0.0.1"
            );

            // when & then
            mockMvc.perform(post("/api/licenses/{licenseKey}/heartbeat", LICENSE_KEY)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.valid").value(false));
        }
    }

    // ==========================================
    // 기기 비활성화 API 테스트
    // ==========================================

    @Nested
    @DisplayName("DELETE /api/licenses/{licenseId}/activations/{deviceFingerprint}")
    class DeactivateEndpoint {

        @Test
        @WithMockUser
        @DisplayName("정상 비활성화 시 204 No Content 반환")
        void shouldReturn204OnSuccessfulDeactivation() throws Exception {
            // given
            doNothing().when(licenseService).deactivate(LICENSE_ID, "device-123");

            // when & then
            mockMvc.perform(delete("/api/licenses/{licenseId}/activations/{deviceFingerprint}",
                            LICENSE_ID, "device-123")
                            .with(csrf()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @WithMockUser
        @DisplayName("존재하지 않는 활성화 비활성화 시 404 반환")
        void shouldReturn404WhenActivationNotFound() throws Exception {
            // given
            doThrow(new LicenseException(ErrorCode.ACTIVATION_NOT_FOUND))
                    .when(licenseService).deactivate(LICENSE_ID, "device-123");

            // when & then
            mockMvc.perform(delete("/api/licenses/{licenseId}/activations/{deviceFingerprint}",
                            LICENSE_ID, "device-123")
                            .with(csrf()))
                    .andExpect(status().isNotFound());
        }
    }

    // ==========================================
    // 라이선스 조회 API 테스트
    // ==========================================

    @Nested
    @DisplayName("GET /api/licenses/{licenseId}")
    class GetLicenseByIdEndpoint {

        @Test
        @WithMockUser
        @DisplayName("라이선스 ID로 조회 시 200 OK 반환")
        void shouldReturn200WithLicenseDetails() throws Exception {
            // given
            LicenseResponse response = createLicenseResponse();
            given(licenseService.getLicense(LICENSE_ID)).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/licenses/{licenseId}", LICENSE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(LICENSE_ID.toString()))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.licenseKey").value(LICENSE_KEY));
        }

        @Test
        @WithMockUser
        @DisplayName("존재하지 않는 라이선스 ID 조회 시 404 반환")
        void shouldReturn404WhenLicenseNotFound() throws Exception {
            // given
            given(licenseService.getLicense(LICENSE_ID))
                    .willThrow(new LicenseException(ErrorCode.LICENSE_NOT_FOUND));

            // when & then
            mockMvc.perform(get("/api/licenses/{licenseId}", LICENSE_ID))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/licenses/key/{licenseKey}")
    class GetLicenseByKeyEndpoint {

        @Test
        @WithMockUser
        @DisplayName("라이선스 키로 조회 시 200 OK 반환")
        void shouldReturn200WithLicenseDetails() throws Exception {
            // given
            LicenseResponse response = createLicenseResponse();
            given(licenseService.getLicenseByKey(LICENSE_KEY)).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/licenses/key/{licenseKey}", LICENSE_KEY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.licenseKey").value(LICENSE_KEY));
        }
    }

    // ==========================================
    // 제거된 API 테스트 (404 확인)
    // ==========================================

    @Nested
    @DisplayName("제거된 API 엔드포인트")
    class RemovedEndpoints {

        @Test
        @WithMockUser
        @DisplayName("POST /api/licenses (발급 API) 호출 시 404 반환")
        void shouldReturn404ForIssueEndpoint() throws Exception {
            mockMvc.perform(post("/api/licenses")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser
        @DisplayName("POST /api/licenses/{id}/suspend (정지 API) 호출 시 404 반환")
        void shouldReturn404ForSuspendEndpoint() throws Exception {
            mockMvc.perform(post("/api/licenses/{licenseId}/suspend", LICENSE_ID)
                            .with(csrf())
                            .param("reason", "test"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser
        @DisplayName("POST /api/licenses/{id}/revoke (회수 API) 호출 시 404 반환")
        void shouldReturn404ForRevokeEndpoint() throws Exception {
            mockMvc.perform(post("/api/licenses/{licenseId}/revoke", LICENSE_ID)
                            .with(csrf())
                            .param("reason", "test"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser
        @DisplayName("POST /api/licenses/{id}/renew (갱신 API) 호출 시 404 반환")
        void shouldReturn404ForRenewEndpoint() throws Exception {
            mockMvc.perform(post("/api/licenses/{licenseId}/renew", LICENSE_ID)
                            .with(csrf())
                            .param("newValidUntil", "2026-12-31T23:59:59Z"))
                    .andExpect(status().isNotFound());
        }
    }

    // ==========================================
    // 헬퍼 메서드
    // ==========================================

    private LicenseResponse createLicenseResponse() {
        return new LicenseResponse(
                LICENSE_ID,
                com.bulc.homepage.licensing.domain.OwnerType.USER,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                com.bulc.homepage.licensing.domain.LicenseType.SUBSCRIPTION,
                com.bulc.homepage.licensing.domain.UsageCategory.COMMERCIAL,
                LicenseStatus.ACTIVE,
                Instant.now(),
                Instant.now(),
                Instant.now().plus(30, ChronoUnit.DAYS),
                LICENSE_KEY,
                java.util.Map.of("maxActivations", 3),
                List.of(),
                Instant.now(),
                Instant.now()
        );
    }
}
