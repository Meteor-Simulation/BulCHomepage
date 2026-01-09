package com.bulc.homepage.licensing.integration;

import com.bulc.homepage.licensing.config.TestKeyConfig;
import com.bulc.homepage.licensing.domain.*;
import com.bulc.homepage.licensing.dto.*;
import com.bulc.homepage.licensing.repository.ActivationRepository;
import com.bulc.homepage.licensing.repository.LicenseRepository;
import com.bulc.homepage.licensing.service.LicenseService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * v0.3.0 Auto-Resolve + Global Session Kick 통합 테스트.
 *
 * 주요 시나리오:
 * 1. 빈 슬롯으로 자동 선택 (resolution: OK)
 * 2. Stale 세션 자동 정리 (resolution: AUTO_RECOVERED)
 * 3. 모두 Full로 409 응답 (resolution: USER_ACTION_REQUIRED)
 * 4. Force Validate 후 정상 활성화
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestKeyConfig.class)
@Transactional
class AutoResolveIntegrationTest {

    @Autowired
    private LicenseService licenseService;

    @Autowired
    private LicenseRepository licenseRepository;

    @Autowired
    private ActivationRepository activationRepository;

    @Value("${bulc.licensing.stale-threshold-minutes:30}")
    private int staleThresholdMinutes;

    private static final UUID USER_ID = UUID.randomUUID();

    // ==========================================
    // Auto-Resolve: 빈 슬롯으로 자동 선택
    // ==========================================

    @Nested
    @DisplayName("Auto-Resolve: 빈 슬롯으로 자동 선택")
    class EmptySlotAutoSelectTest {

        @Test
        @DisplayName("후보 라이선스 중 빈 슬롯이 있으면 자동 선택하여 성공 (resolution: OK)")
        void shouldAutoSelectLicenseWithEmptySlot() {
            // given - maxConcurrentSessions=2인 라이선스 1개 발급
            LicenseResponse license = issueLicenseWithLimits(3, 2);
            UUID productId = license.productId();

            ValidateRequest request = new ValidateRequest(
                    null, productId, null,
                    "device-001", "1.0.0", "Windows 11", null, null
            );

            // when - 빈 슬롯이 있는 상태에서 검증
            ValidationResponse response = licenseService.validateAndActivateByUser(USER_ID, request);

            // then - 자동 선택되어 성공
            assertThat(response.valid()).isTrue();
            assertThat(response.resolution()).isEqualTo("OK");
            assertThat(response.licenseId()).isEqualTo(license.id());
            assertThat(response.status()).isEqualTo(LicenseStatus.ACTIVE);
        }

        @Test
        @DisplayName("다중 라이선스 중 빈 슬롯이 있는 라이선스 자동 선택")
        void shouldAutoSelectFromMultipleLicensesWithEmptySlot() {
            // given - 동일 제품에 대해 2개의 라이선스 발급 (다른 productId)
            UUID productId1 = UUID.randomUUID();
            UUID productId2 = UUID.randomUUID();
            LicenseResponse license1 = issueLicenseWithLimits(productId1, 2, 1);
            LicenseResponse license2 = issueLicenseWithLimits(productId2, 2, 1);

            // license1은 이미 한 기기가 사용 중 (full)
            ValidateRequest existingRequest = new ValidateRequest(
                    null, productId1, null,
                    "existing-device", "1.0.0", "macOS", null, null
            );
            licenseService.validateAndActivateByUser(USER_ID, existingRequest);

            // when - productId 미지정으로 검증 (두 라이선스 모두 후보)
            ValidateRequest newRequest = new ValidateRequest(
                    null, null, null,  // productId 미지정 → 모든 유효 라이선스가 후보
                    "new-device", "1.0.0", "Windows", null, null
            );
            ValidationResponse response = licenseService.validateAndActivateByUser(USER_ID, newRequest);

            // then - 빈 슬롯이 있는 라이선스(license2)가 자동 선택됨
            assertThat(response.valid()).isTrue();
            assertThat(response.resolution()).isEqualTo("OK");
            // license2가 선택되어야 함 (license1은 full)
            assertThat(response.licenseId()).isEqualTo(license2.id());
        }

        @Test
        @DisplayName("Device Affinity: 현재 기기가 이미 활성화된 라이선스 우선 선택")
        void shouldPreferLicenseWithExistingDeviceAffinity() {
            // given - 2개의 라이선스 발급 (각각 다른 productId)
            UUID productId1 = UUID.randomUUID();
            UUID productId2 = UUID.randomUUID();
            LicenseResponse license1 = issueLicenseWithLimits(productId1, 2, 2);
            LicenseResponse license2 = issueLicenseWithLimits(productId2, 2, 2);

            // device-A를 license1에 활성화 (licenseId 명시 지정)
            ValidateRequest firstRequest = new ValidateRequest(
                    null, productId1, license1.id(),
                    "device-A", "1.0.0", "Windows", null, null
            );
            licenseService.validateAndActivateByUser(USER_ID, firstRequest);

            // when - 동일 기기로 재검증 (licenseId, productId 미지정)
            ValidateRequest secondRequest = new ValidateRequest(
                    null, null, null,  // 모든 후보에서 검색
                    "device-A", "1.0.0", "Windows", null, null
            );
            ValidationResponse response = licenseService.validateAndActivateByUser(USER_ID, secondRequest);

            // then - 기존 기기-라이선스 연결(Device Affinity)으로 license1 선택
            assertThat(response.valid()).isTrue();
            assertThat(response.resolution()).isEqualTo("OK");
            assertThat(response.licenseId()).isEqualTo(license1.id());
        }
    }

    // ==========================================
    // Auto-Resolve: Stale 세션 자동 종료
    // ==========================================

    @Nested
    @DisplayName("Auto-Resolve: Stale 세션 자동 종료 (AUTO_RECOVERED)")
    class StaleSessionAutoTerminateTest {

        @Test
        @DisplayName("모든 슬롯이 사용 중이지만 stale 세션이 있으면 자동 종료 후 성공 (AUTO_RECOVERED)")
        void shouldAutoTerminateStaleSessionAndSucceed() {
            // given - maxConcurrentSessions=1인 라이선스, 1개 기기 활성화
            LicenseResponse license = issueLicenseWithLimits(2, 1);
            UUID productId = license.productId();

            ValidateRequest firstRequest = new ValidateRequest(
                    null, productId, null,
                    "stale-device", "1.0.0", "Windows", null, null
            );
            licenseService.validateAndActivateByUser(USER_ID, firstRequest);

            // stale-device의 lastSeenAt을 stale 시간 이전으로 설정
            Activation staleActivation = activationRepository
                    .findByLicenseIdAndDeviceFingerprint(license.id(), "stale-device")
                    .orElseThrow();
            Instant staleTime = Instant.now().minus(staleThresholdMinutes + 5, ChronoUnit.MINUTES);
            staleActivation.forceSetLastSeenAt(staleTime);
            activationRepository.save(staleActivation);

            // when - 새 기기로 검증 (full이지만 stale 있음)
            ValidateRequest newRequest = new ValidateRequest(
                    null, productId, null,
                    "new-device", "1.0.0", "macOS", null, null
            );
            ValidationResponse response = licenseService.validateAndActivateByUser(USER_ID, newRequest);

            // then - stale 세션 자동 종료 후 성공
            assertThat(response.valid()).isTrue();
            assertThat(response.resolution()).isEqualTo("AUTO_RECOVERED");
            assertThat(response.recoveryAction()).isEqualTo("STALE_SESSION_TERMINATED");
            assertThat(response.terminatedSession()).isNotNull();

            // DB 확인 - stale 세션 비활성화됨
            Activation terminated = activationRepository
                    .findByLicenseIdAndDeviceFingerprint(license.id(), "stale-device")
                    .orElseThrow();
            assertThat(terminated.getStatus()).isEqualTo(ActivationStatus.DEACTIVATED);
        }
    }

    // ==========================================
    // ALL_LICENSES_FULL (409 Conflict)
    // ==========================================

    @Nested
    @DisplayName("ALL_LICENSES_FULL: 모든 슬롯 사용 중 (409)")
    class AllLicensesFullTest {

        @Test
        @DisplayName("모든 후보 라이선스의 슬롯이 사용 중이면 ALL_LICENSES_FULL 반환")
        void shouldReturnAllLicensesFullWhenNoAvailableSlots() {
            // given - maxConcurrentSessions=1인 라이선스, 1개 기기 활성화 (최근 활동)
            LicenseResponse license = issueLicenseWithLimits(2, 1);
            UUID productId = license.productId();

            ValidateRequest firstRequest = new ValidateRequest(
                    null, productId, null,
                    "active-device", "1.0.0", "Windows", null, null
            );
            licenseService.validateAndActivateByUser(USER_ID, firstRequest);

            // active-device가 최근에 heartbeat (stale 아님)
            licenseService.heartbeatByUser(USER_ID, new ValidateRequest(
                    null, productId, null,
                    "active-device", "1.0.0", "Windows", null, null
            ));

            // when - 새 기기로 검증 (full이고 stale 없음)
            ValidateRequest newRequest = new ValidateRequest(
                    null, productId, null,
                    "new-device", "1.0.0", "macOS", null, null
            );
            ValidationResponse response = licenseService.validateAndActivateByUser(USER_ID, newRequest);

            // then - ALL_LICENSES_FULL 에러
            assertThat(response.valid()).isFalse();
            assertThat(response.resolution()).isEqualTo("USER_ACTION_REQUIRED");
            assertThat(response.actionRequired()).isEqualTo("KICK_REQUIRED");
            assertThat(response.errorCode()).isEqualTo("ALL_LICENSES_FULL");
            assertThat(response.activeSessions()).isNotEmpty();
        }

        @Test
        @DisplayName("Global Session Kick: activeSessions에 모든 후보의 세션 정보 포함")
        void shouldIncludeAllCandidateSessionsInActiveSessions() {
            // given - 2개의 라이선스, 각각 full (다른 productId)
            UUID productId1 = UUID.randomUUID();
            UUID productId2 = UUID.randomUUID();
            LicenseResponse license1 = issueLicenseWithLimits(productId1, 2, 1);
            LicenseResponse license2 = issueLicenseWithLimits(productId2, 2, 1);

            licenseService.validateAndActivateByUser(USER_ID, new ValidateRequest(
                    null, productId1, license1.id(),
                    "device-on-license1", "1.0.0", "Windows", null, null
            ));
            licenseService.validateAndActivateByUser(USER_ID, new ValidateRequest(
                    null, productId2, license2.id(),
                    "device-on-license2", "1.0.0", "macOS", null, null
            ));

            // when - productId 미지정으로 검증 (양쪽 다 full)
            ValidateRequest newRequest = new ValidateRequest(
                    null, null, null,  // productId 미지정 → 모든 유효 라이선스가 후보
                    "new-device", "1.0.0", "Linux", null, null
            );
            ValidationResponse response = licenseService.validateAndActivateByUser(USER_ID, newRequest);

            // then - 모든 후보 라이선스의 세션이 activeSessions에 포함
            assertThat(response.valid()).isFalse();
            assertThat(response.activeSessions()).hasSize(2);

            // 각 세션에 licenseId 정보 포함 확인
            List<UUID> licenseIdsInSessions = response.activeSessions().stream()
                    .map(ValidationResponse.GlobalSessionInfo::licenseId)
                    .toList();
            assertThat(licenseIdsInSessions).containsExactlyInAnyOrder(license1.id(), license2.id());
        }
    }

    // ==========================================
    // Force Validate 후 정상 활성화
    // ==========================================

    @Nested
    @DisplayName("Force Validate로 강제 활성화")
    class ForceValidateTest {

        @Test
        @DisplayName("ALL_LICENSES_FULL 시 Force Validate로 기존 세션 종료 후 활성화")
        void shouldActivateAfterForceValidate() {
            // given - full 상태의 라이선스
            LicenseResponse license = issueLicenseWithLimits(2, 1);
            UUID productId = license.productId();

            ValidateRequest firstRequest = new ValidateRequest(
                    null, productId, null,
                    "existing-device", "1.0.0", "Windows", null, null
            );
            ValidationResponse firstResponse = licenseService.validateAndActivateByUser(USER_ID, firstRequest);
            assertThat(firstResponse.valid()).isTrue();

            // 새 기기 시도 - ALL_LICENSES_FULL
            ValidateRequest newRequest = new ValidateRequest(
                    null, productId, null,
                    "new-device", "1.0.0", "macOS", null, null
            );
            ValidationResponse fullResponse = licenseService.validateAndActivateByUser(USER_ID, newRequest);
            assertThat(fullResponse.valid()).isFalse();
            assertThat(fullResponse.errorCode()).isEqualTo("ALL_LICENSES_FULL");

            // activeSessions에서 종료할 세션 정보 가져오기
            assertThat(fullResponse.activeSessions()).hasSize(1);
            UUID activationIdToKick = fullResponse.activeSessions().get(0).activationId();

            // when - Force Validate로 기존 세션 강제 종료 후 활성화
            ForceValidateRequest forceRequest = new ForceValidateRequest(
                    license.id(),
                    "new-device",
                    List.of(activationIdToKick),
                    "1.0.0", "macOS", "New MacBook"
            );
            ValidationResponse forceResponse = licenseService.forceValidateByUser(USER_ID, forceRequest);

            // then - 성공
            assertThat(forceResponse.valid()).isTrue();
            assertThat(forceResponse.licenseId()).isEqualTo(license.id());

            // 기존 세션은 비활성화됨
            Activation kicked = activationRepository.findById(activationIdToKick).orElseThrow();
            assertThat(kicked.getStatus()).isEqualTo(ActivationStatus.DEACTIVATED);
        }
    }

    // ==========================================
    // resolution 필드 검증
    // ==========================================

    @Nested
    @DisplayName("Resolution 필드 검증")
    class ResolutionFieldTest {

        @Test
        @DisplayName("성공 시 resolution은 OK 또는 AUTO_RECOVERED")
        void shouldHaveValidResolutionOnSuccess() {
            // given
            LicenseResponse license = issueLicenseWithLimits(3, 2);
            UUID productId = license.productId();

            ValidateRequest request = new ValidateRequest(
                    null, productId, null,
                    "device-001", "1.0.0", "Windows", null, null
            );

            // when
            ValidationResponse response = licenseService.validateAndActivateByUser(USER_ID, request);

            // then
            assertThat(response.valid()).isTrue();
            assertThat(response.resolution()).isIn("OK", "AUTO_RECOVERED");
        }

        @Test
        @DisplayName("실패 시 resolution은 USER_ACTION_REQUIRED")
        void shouldHaveUserActionRequiredResolutionOnFailure() {
            // given - full 라이선스 (활성 세션)
            LicenseResponse license = issueLicenseWithLimits(2, 1);
            UUID productId = license.productId();

            licenseService.validateAndActivateByUser(USER_ID, new ValidateRequest(
                    null, productId, null,
                    "active-device", "1.0.0", "Windows", null, null
            ));

            // when - 새 기기 시도
            ValidateRequest newRequest = new ValidateRequest(
                    null, productId, null,
                    "new-device", "1.0.0", "macOS", null, null
            );
            ValidationResponse response = licenseService.validateAndActivateByUser(USER_ID, newRequest);

            // then
            assertThat(response.valid()).isFalse();
            assertThat(response.resolution()).isEqualTo("USER_ACTION_REQUIRED");
        }
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    /**
     * 테스트용 라이선스 발급 (새로운 productId 자동 생성).
     */
    private LicenseResponse issueLicenseWithLimits(int maxActivations, int maxConcurrentSessions) {
        return issueLicenseWithLimits(UUID.randomUUID(), maxActivations, maxConcurrentSessions);
    }

    /**
     * 테스트용 라이선스 발급 (productId 지정).
     */
    private LicenseResponse issueLicenseWithLimits(UUID productId, int maxActivations, int maxConcurrentSessions) {
        LicenseIssueRequest request = new LicenseIssueRequest(
                OwnerType.USER, USER_ID, productId, null,
                LicenseType.SUBSCRIPTION, null, null,
                Instant.now().plus(30, ChronoUnit.DAYS),
                Map.of(
                        "maxActivations", maxActivations,
                        "maxConcurrentSessions", maxConcurrentSessions,
                        "entitlements", List.of("core-simulation")
                ),
                UUID.randomUUID()
        );
        return licenseService.issueLicense(request);
    }
}
