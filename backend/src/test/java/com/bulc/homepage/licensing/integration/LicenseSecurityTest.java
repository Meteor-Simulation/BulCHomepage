package com.bulc.homepage.licensing.integration;

import com.bulc.homepage.licensing.config.TestKeyConfig;
import com.bulc.homepage.licensing.domain.*;
import com.bulc.homepage.licensing.dto.*;
import com.bulc.homepage.licensing.exception.LicenseException;
import com.bulc.homepage.licensing.repository.ActivationRepository;
import com.bulc.homepage.licensing.repository.LicenseRepository;
import com.bulc.homepage.licensing.service.LicenseService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 라이선스 보안/엣지케이스 통합 테스트.
 *
 * Critical 테스트 시나리오:
 * - S-01: Device Fingerprint 불일치 (토큰 복사 방어)
 * - C-02: Ghost Session 부활 방지
 * - D-02: 라이선스 Revoke 후 Heartbeat 차단
 * - D-03: 다중 라이선스 Strategy 테스트
 * - T-01: 만료 경계 테스트
 * - T-02: Grace Period 경계 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestKeyConfig.class)
@Transactional
class LicenseSecurityTest {

    @Autowired
    private LicenseService licenseService;

    @Autowired
    private LicenseRepository licenseRepository;

    @Autowired
    private ActivationRepository activationRepository;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID_2 = UUID.randomUUID();  // D-03 다중 라이선스 테스트용

    // ==========================================
    // S-01: Device Fingerprint 불일치 테스트 (Critical)
    // ==========================================

    @Nested
    @DisplayName("S-01: Device Fingerprint Binding 테스트")
    class DeviceFingerprintBindingTest {

        @Test
        @DisplayName("다른 기기 fingerprint로 heartbeat 요청 시 실패")
        void shouldRejectHeartbeatFromDifferentDevice() {
            // given - 기기 A에서 라이선스 활성화
            LicenseResponse license = issueLicense();
            UUID userId = USER_ID;

            ValidateRequest activateRequest = new ValidateRequest(
                    null, PRODUCT_ID, null,
                    "device-A-fingerprint",
                    "1.0.0", "Windows", null, null
            );
            ValidationResponse activateResponse = licenseService.validateAndActivateByUser(userId, activateRequest);
            assertThat(activateResponse.valid()).isTrue();

            // when - 기기 B의 fingerprint로 heartbeat 시도 (토큰 복사 시도)
            ValidateRequest heartbeatRequest = new ValidateRequest(
                    null, PRODUCT_ID, null,
                    "device-B-fingerprint",  // 다른 기기
                    "1.0.0", "Windows", null, null
            );

            // then - 활성화되지 않은 기기이므로 실패해야 함
            assertThatThrownBy(() -> licenseService.heartbeatByUser(userId, heartbeatRequest))
                    .isInstanceOf(LicenseException.class)
                    .extracting(ex -> ((LicenseException) ex).getErrorCode())
                    .isEqualTo(LicenseException.ErrorCode.ACTIVATION_NOT_FOUND);
        }

        @Test
        @DisplayName("동일 기기 fingerprint로 heartbeat 요청 시 성공")
        void shouldAcceptHeartbeatFromSameDevice() {
            // given - 기기 A에서 라이선스 활성화
            LicenseResponse license = issueLicense();
            UUID userId = USER_ID;

            ValidateRequest activateRequest = new ValidateRequest(
                    null, PRODUCT_ID, null,
                    "device-A-fingerprint",
                    "1.0.0", "Windows", null, null
            );
            licenseService.validateAndActivateByUser(userId, activateRequest);

            // when - 동일 기기로 heartbeat
            ValidateRequest heartbeatRequest = new ValidateRequest(
                    null, PRODUCT_ID, null,
                    "device-A-fingerprint",  // 동일 기기
                    "1.0.1", "Windows 11", null, null  // 버전 업데이트
            );
            ValidationResponse response = licenseService.heartbeatByUser(userId, heartbeatRequest);

            // then - 성공
            assertThat(response.valid()).isTrue();
        }
    }

    // ==========================================
    // C-02: Ghost Session 부활 방지 테스트 (High)
    // ==========================================

    @Nested
    @DisplayName("C-02: Ghost Session 부활 방지 테스트")
    class GhostSessionPreventionTest {

        @Test
        @DisplayName("Force Validate로 종료된 세션의 heartbeat 요청 시 실패")
        void shouldRejectHeartbeatFromDeactivatedSession() {
            // given - 라이선스 발급 및 기기 A 활성화
            LicenseResponse license = issueLicenseWithLimits(2, 1);
            UUID licenseId = license.id();
            UUID userId = USER_ID;

            ValidateRequest activateA = new ValidateRequest(
                    null, PRODUCT_ID, null, "device-A", "1.0", "Windows", null, null
            );
            licenseService.validateAndActivateByUser(userId, activateA);

            // 기기 A의 activationId 조회
            Activation activationA = activationRepository
                    .findByLicenseIdAndDeviceFingerprint(licenseId, "device-A")
                    .orElseThrow();

            // 기기 B가 Force Validate로 기기 A를 강제 종료
            ForceValidateRequest forceRequest = new ForceValidateRequest(
                    licenseId,
                    "device-B",
                    List.of(activationA.getId()),
                    "1.0", "macOS", "Device B"
            );
            licenseService.forceValidateByUser(userId, forceRequest);

            // then - 기기 A의 상태가 DEACTIVATED
            Activation deactivatedA = activationRepository.findById(activationA.getId()).orElseThrow();
            assertThat(deactivatedA.getStatus()).isEqualTo(ActivationStatus.DEACTIVATED);

            // when - 종료된 기기 A가 heartbeat 요청
            ValidateRequest heartbeatA = new ValidateRequest(
                    null, PRODUCT_ID, null, "device-A", "1.0", "Windows", null, null
            );

            // then - SESSION_DEACTIVATED (비활성화된 세션은 heartbeat 불가)
            assertThatThrownBy(() -> licenseService.heartbeatByUser(userId, heartbeatA))
                    .isInstanceOf(LicenseException.class)
                    .extracting(ex -> ((LicenseException) ex).getErrorCode())
                    .isEqualTo(LicenseException.ErrorCode.SESSION_DEACTIVATED);
        }
    }

    // ==========================================
    // D-02: Revoke 후 Heartbeat 차단 테스트 (High)
    // ==========================================

    @Nested
    @DisplayName("D-02: Revoke 후 Heartbeat 차단 테스트")
    class RevokeAndHeartbeatTest {

        @Test
        @DisplayName("환불로 라이선스 회수 후 heartbeat 요청 시 LICENSE_REVOKED 반환")
        void shouldRejectHeartbeatAfterRevoke() {
            // given - 라이선스 발급 및 활성화
            UUID orderId = UUID.randomUUID();
            LicenseResponse license = issueLicenseWithOrder(orderId);
            UUID userId = USER_ID;

            ValidateRequest activateRequest = new ValidateRequest(
                    null, PRODUCT_ID, null, "device-123", "1.0", "Windows", null, null
            );
            licenseService.validateAndActivateByUser(userId, activateRequest);

            // 라이선스 회수 (환불)
            licenseService.revokeLicenseByOrderId(orderId, "REFUNDED");

            // when - 회수된 라이선스로 heartbeat 시도
            ValidateRequest heartbeatRequest = new ValidateRequest(
                    null, PRODUCT_ID, null, "device-123", "1.0", "Windows", null, null
            );

            // then - LICENSE_NOT_FOUND_FOR_PRODUCT (REVOKED는 조회 대상에서 제외)
            assertThatThrownBy(() -> licenseService.heartbeatByUser(userId, heartbeatRequest))
                    .isInstanceOf(LicenseException.class);
        }

        @Test
        @DisplayName("Suspend 후 heartbeat 요청 시 LICENSE_SUSPENDED 반환")
        void shouldRejectHeartbeatAfterSuspend() {
            // given - 라이선스 발급 및 활성화
            LicenseResponse license = issueLicense();
            UUID licenseId = license.id();
            UUID userId = USER_ID;

            ValidateRequest activateRequest = new ValidateRequest(
                    null, PRODUCT_ID, null, "device-123", "1.0", "Windows", null, null
            );
            licenseService.validateAndActivateByUser(userId, activateRequest);

            // 라이선스 정지
            licenseService.suspendLicense(licenseId, "Policy violation");

            // when - 정지된 라이선스로 heartbeat 시도
            ValidateRequest heartbeatRequest = new ValidateRequest(
                    null, PRODUCT_ID, null, "device-123", "1.0", "Windows", null, null
            );

            // then - LICENSE_NOT_FOUND_FOR_PRODUCT (SUSPENDED는 조회 대상에서 제외)
            assertThatThrownBy(() -> licenseService.heartbeatByUser(userId, heartbeatRequest))
                    .isInstanceOf(LicenseException.class);
        }
    }

    // ==========================================
    // D-03: 다중 라이선스 Strategy 테스트 (Mid)
    // ==========================================

    @Nested
    @DisplayName("D-03: 다중 라이선스 Strategy 테스트")
    class MultiLicenseStrategyTest {

        @Test
        @DisplayName("v0.3.0: 다중 라이선스 시 서버가 자동 선택 (Auto-Resolve)")
        void shouldAutoSelectWhenMultipleLicenses() {
            // given - 동일 사용자에게 다른 제품의 라이선스 2개 발급
            LicenseResponse license1 = issueLicenseForProduct(PRODUCT_ID);
            LicenseResponse license2 = issueLicenseForProduct(PRODUCT_ID_2);

            UUID userId = USER_ID;
            // productId를 지정하지 않으면 사용자의 모든 유효 라이선스가 candidates
            ValidateRequest request = new ValidateRequest(
                    null, null, null,  // productId 미지정
                    "device-123", "1.0", "Windows", null,
                    null  // v0.3.0: strategy 무시, 서버가 항상 Auto-Select
            );

            // when
            ValidationResponse response = licenseService.validateAndActivateByUser(userId, request);

            // then - v0.3.0: 서버가 자동으로 라이선스 선택하여 성공
            assertThat(response.valid()).isTrue();
            assertThat(response.licenseId()).isIn(license1.id(), license2.id());
            assertThat(response.resolution()).isEqualTo("OK");
        }

        @Test
        @DisplayName("strategy=AUTO_PICK_BEST 시 ACTIVE 라이선스 자동 선택")
        void shouldAutoSelectActiveLicenseWithAutoPick() {
            // given - ACTIVE 라이선스 2개 (다른 제품)
            LicenseResponse license1 = issueLicenseForProduct(PRODUCT_ID);
            LicenseResponse license2 = issueLicenseForProduct(PRODUCT_ID_2);

            UUID userId = USER_ID;
            // productId를 지정하지 않으면 사용자의 모든 유효 라이선스가 candidates
            ValidateRequest request = new ValidateRequest(
                    null, null, null,  // productId 미지정
                    "device-123", "1.0", "Windows", null,
                    LicenseSelectionStrategy.AUTO_PICK_BEST
            );

            // when
            ValidationResponse response = licenseService.validateAndActivateByUser(userId, request);

            // then - 두 라이선스 중 하나가 자동 선택되어 성공
            assertThat(response.valid()).isTrue();
            assertThat(response.licenseId()).isIn(license1.id(), license2.id());
            assertThat(response.status()).isEqualTo(LicenseStatus.ACTIVE);
        }
    }

    // ==========================================
    // T-01: 만료 경계 테스트 (High)
    // ==========================================

    @Nested
    @DisplayName("T-01: 만료 경계 테스트")
    class ExpirationBoundaryTest {

        @Test
        @DisplayName("만료 직후(EXPIRED_HARD) 라이선스 검증 시 실패")
        void shouldFailValidationJustAfterExpiration() {
            // given - 이미 만료된 라이선스 (gracePeriodDays=0)
            LicenseIssueRequest request = new LicenseIssueRequest(
                    OwnerType.USER, USER_ID, PRODUCT_ID, null,
                    LicenseType.SUBSCRIPTION, null, null,
                    Instant.now().minus(1, ChronoUnit.DAYS),  // 어제 만료
                    Map.of("maxActivations", 3, "gracePeriodDays", 0),  // Grace 없음
                    UUID.randomUUID()
            );
            LicenseResponse licenseResponse = licenseService.issueLicense(request);

            // 라이선스 키 조회
            License license = licenseRepository.findById(licenseResponse.id()).orElseThrow();
            String licenseKey = license.getLicenseKey();

            // when - 라이선스 키로 직접 검증 (validateAndActivate은 effectiveStatus 계산)
            ActivationRequest activationRequest = new ActivationRequest(
                    "device-123", "1.0", "Windows", null
            );
            ValidationResponse response = licenseService.validateAndActivate(
                    licenseKey, activationRequest
            );

            // then - valid=false, LICENSE_EXPIRED
            assertThat(response.valid()).isFalse();
            assertThat(response.errorCode()).isEqualTo("LICENSE_EXPIRED");
        }
    }

    // ==========================================
    // T-02: Grace Period 경계 테스트 (High)
    // ==========================================

    @Nested
    @DisplayName("T-02: Grace Period 경계 테스트")
    class GracePeriodBoundaryTest {

        @Test
        @DisplayName("Grace Period 내에서는 검증 성공 (EXPIRED_GRACE)")
        void shouldSucceedDuringGracePeriod() {
            // given - 만료되었지만 Grace Period 내 (7일)
            LicenseIssueRequest request = new LicenseIssueRequest(
                    OwnerType.USER, USER_ID, PRODUCT_ID, null,
                    LicenseType.SUBSCRIPTION, null, null,
                    Instant.now().minus(3, ChronoUnit.DAYS),  // 3일 전 만료
                    Map.of("maxActivations", 3, "gracePeriodDays", 7),  // Grace 7일
                    UUID.randomUUID()
            );
            LicenseResponse licenseResponse = licenseService.issueLicense(request);

            // 라이선스 키 조회
            License license = licenseRepository.findById(licenseResponse.id()).orElseThrow();
            String licenseKey = license.getLicenseKey();

            // when - 라이선스 키로 직접 검증
            ActivationRequest activationRequest = new ActivationRequest(
                    "device-123", "1.0", "Windows", null
            );
            ValidationResponse response = licenseService.validateAndActivate(
                    licenseKey, activationRequest
            );

            // then - EXPIRED_GRACE 상태로 성공
            assertThat(response.valid()).isTrue();
            assertThat(response.status()).isEqualTo(LicenseStatus.EXPIRED_GRACE);
        }

        @Test
        @DisplayName("Grace Period 종료 후에는 검증 실패 (EXPIRED_HARD)")
        void shouldFailAfterGracePeriodEnds() {
            // given - Grace Period도 지난 라이선스
            LicenseIssueRequest request = new LicenseIssueRequest(
                    OwnerType.USER, USER_ID, PRODUCT_ID, null,
                    LicenseType.SUBSCRIPTION, null, null,
                    Instant.now().minus(10, ChronoUnit.DAYS),  // 10일 전 만료
                    Map.of("maxActivations", 3, "gracePeriodDays", 7),  // Grace 7일 (이미 지남)
                    UUID.randomUUID()
            );
            LicenseResponse licenseResponse = licenseService.issueLicense(request);

            // 라이선스 키 조회
            License license = licenseRepository.findById(licenseResponse.id()).orElseThrow();
            String licenseKey = license.getLicenseKey();

            // when - 라이선스 키로 직접 검증
            ActivationRequest activationRequest = new ActivationRequest(
                    "device-123", "1.0", "Windows", null
            );
            ValidationResponse response = licenseService.validateAndActivate(
                    licenseKey, activationRequest
            );

            // then - valid=false, LICENSE_EXPIRED
            assertThat(response.valid()).isFalse();
            assertThat(response.errorCode()).isEqualTo("LICENSE_EXPIRED");
        }
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    private LicenseResponse issueLicense() {
        return issueLicenseWithLimits(3, 2);
    }

    private LicenseResponse issueLicenseForProduct(UUID productId) {
        LicenseIssueRequest request = new LicenseIssueRequest(
                OwnerType.USER, USER_ID, productId, null,
                LicenseType.SUBSCRIPTION, null, null,
                Instant.now().plus(30, ChronoUnit.DAYS),
                Map.of(
                        "maxActivations", 3,
                        "maxConcurrentSessions", 2,
                        "entitlements", List.of("core-simulation")
                ),
                UUID.randomUUID()
        );
        return licenseService.issueLicense(request);
    }

    private LicenseResponse issueLicenseWithLimits(int maxActivations, int maxConcurrentSessions) {
        LicenseIssueRequest request = new LicenseIssueRequest(
                OwnerType.USER, USER_ID, PRODUCT_ID, null,
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

    private LicenseResponse issueLicenseWithOrder(UUID orderId) {
        LicenseIssueRequest request = new LicenseIssueRequest(
                OwnerType.USER, USER_ID, PRODUCT_ID, null,
                LicenseType.SUBSCRIPTION, null, null,
                Instant.now().plus(30, ChronoUnit.DAYS),
                Map.of("maxActivations", 3, "entitlements", List.of("core-simulation")),
                orderId
        );
        return licenseService.issueLicense(request);
    }
}
