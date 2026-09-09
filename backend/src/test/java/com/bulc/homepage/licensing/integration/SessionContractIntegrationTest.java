package com.bulc.homepage.licensing.integration;

import com.bulc.homepage.licensing.config.TestKeyConfig;
import com.bulc.homepage.licensing.domain.Activation;
import com.bulc.homepage.licensing.domain.ActivationStatus;
import com.bulc.homepage.licensing.domain.ClientKind;
import com.bulc.homepage.licensing.domain.LicenseType;
import com.bulc.homepage.licensing.domain.OwnerType;
import com.bulc.homepage.licensing.dto.LicenseIssueRequest;
import com.bulc.homepage.licensing.dto.LicenseResponse;
import com.bulc.homepage.licensing.dto.ValidateRequest;
import com.bulc.homepage.licensing.dto.ValidationResponse;
import com.bulc.homepage.licensing.exception.LicenseException;
import com.bulc.homepage.licensing.repository.ActivationRepository;
import com.bulc.homepage.licensing.service.LicenseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 세션·좌석 계약 정합 통합 테스트 (MDP-790 · 계약 §3).
 * B1(activationId 기반 반납) · B3(409 필드 채움) · B5(clientKind).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestKeyConfig.class)
@Transactional
class SessionContractIntegrationTest {

    @Autowired
    private LicenseService licenseService;

    @Autowired
    private ActivationRepository activationRepository;

    private static final UUID USER_ID = UUID.randomUUID();

    // ==========================================
    // B5: clientKind
    // ==========================================

    @Nested
    @DisplayName("B5: clientKind 기록")
    class ClientKindTest {

        @Test
        @DisplayName("validate 요청의 clientKind=cli 가 activation 에 CLI 로 기록됨")
        void shouldPersistCliClientKind() {
            LicenseResponse license = issueLicense(3, 2);
            ValidateRequest request = new ValidateRequest(
                    null, license.productId(), null, "device-cli",
                    "1.0", "Windows", "CLI Box", "cli", null
            );

            ValidationResponse response = licenseService.validateAndActivateByUser(USER_ID, request);
            assertThat(response.valid()).isTrue();

            Activation activation = activationRepository
                    .findByLicenseIdAndDeviceFingerprint(license.id(), "device-cli").orElseThrow();
            assertThat(activation.getClientKind()).isEqualTo(ClientKind.CLI);
        }

        @Test
        @DisplayName("clientKind 대문자(GUI)도 관대 수용")
        void shouldAcceptUppercaseClientKind() {
            LicenseResponse license = issueLicense(3, 2);
            ValidateRequest request = new ValidateRequest(
                    null, license.productId(), null, "device-gui",
                    "1.0", "Windows", null, "GUI", null
            );
            licenseService.validateAndActivateByUser(USER_ID, request);

            Activation activation = activationRepository
                    .findByLicenseIdAndDeviceFingerprint(license.id(), "device-gui").orElseThrow();
            assertThat(activation.getClientKind()).isEqualTo(ClientKind.GUI);
        }

        @Test
        @DisplayName("clientKind 미전송 시 null (구버전 관대 수용)")
        void shouldLeaveClientKindNullWhenAbsent() {
            LicenseResponse license = issueLicense(3, 2);
            ValidateRequest request = new ValidateRequest(
                    null, license.productId(), null, "device-legacy",
                    "1.0", "Windows", null, null  // 8-인자 하위호환 생성자 (clientKind 없음)
            );
            licenseService.validateAndActivateByUser(USER_ID, request);

            Activation activation = activationRepository
                    .findByLicenseIdAndDeviceFingerprint(license.id(), "device-legacy").orElseThrow();
            assertThat(activation.getClientKind()).isNull();
        }

        @Test
        @DisplayName("알 수 없는 clientKind 값은 null 로 저장 (검증 실패 아님)")
        void shouldStoreNullForUnknownClientKind() {
            LicenseResponse license = issueLicense(3, 2);
            ValidateRequest request = new ValidateRequest(
                    null, license.productId(), null, "device-weird",
                    "1.0", "Windows", null, "wat", null
            );
            ValidationResponse response = licenseService.validateAndActivateByUser(USER_ID, request);
            assertThat(response.valid()).isTrue();

            Activation activation = activationRepository
                    .findByLicenseIdAndDeviceFingerprint(license.id(), "device-weird").orElseThrow();
            assertThat(activation.getClientKind()).isNull();
        }
    }

    // ==========================================
    // B3: 409 응답 필드 채움
    // ==========================================

    @Nested
    @DisplayName("B3: ALL_LICENSES_FULL 409 응답의 licenseId·maxConcurrentSessions")
    class FullResponseFieldsTest {

        @Test
        @DisplayName("단일 후보 라이선스 full(동시 세션 1) 시 licenseId·maxConcurrentSessions 채워짐")
        void shouldFillFieldsOnSingleLicenseFull() {
            // maxConcurrentSessions=1 — autoResolve 경로(licenseId 미지정)로 동시 세션 한도 적용
            LicenseResponse license = issueLicense(3, 1);

            // 첫 기기 활성화 (동시 세션 1/1)
            licenseService.validateAndActivateByUser(USER_ID, new ValidateRequest(
                    null, license.productId(), null, "device-1",
                    "1.0", "Windows", null, null));

            // 두 번째 기기 (licenseId 미지정 → autoResolve) → 단일 후보 full
            ValidationResponse full = licenseService.validateAndActivateByUser(USER_ID, new ValidateRequest(
                    null, license.productId(), null, "device-2",
                    "1.0", "macOS", null, null));

            assertThat(full.valid()).isFalse();
            assertThat(full.errorCode()).isEqualTo("ALL_LICENSES_FULL");
            // B3: 종전엔 null 이던 값들 (단일 후보이므로 채워짐)
            assertThat(full.licenseId()).isEqualTo(license.id());
            assertThat(full.maxConcurrentSessions()).isEqualTo(1);
        }
    }

    // ==========================================
    // B1: activationId 기반 반납
    // ==========================================

    @Nested
    @DisplayName("B1: deactivateByActivationIdWithOwnerCheck")
    class DeactivateByActivationIdTest {

        @Test
        @DisplayName("activationId 로 자기 세션 반납 시 DEACTIVATED")
        void shouldDeactivateByActivationId() {
            LicenseResponse license = issueLicense(3, 2);
            ValidationResponse resp = licenseService.validateAndActivateByUser(USER_ID, new ValidateRequest(
                    null, license.productId(), license.id(), "device-x",
                    "1.0", "Windows", null, null));
            assertThat(resp.valid()).isTrue();

            Activation activation = activationRepository
                    .findByLicenseIdAndDeviceFingerprint(license.id(), "device-x").orElseThrow();

            licenseService.deactivateByActivationIdWithOwnerCheck(USER_ID, license.id(), activation.getId());

            Activation after = activationRepository.findById(activation.getId()).orElseThrow();
            assertThat(after.getStatus()).isEqualTo(ActivationStatus.DEACTIVATED);
        }

        @Test
        @DisplayName("타인 소유 라이선스 반납 시 ACCESS_DENIED")
        void shouldRejectOtherOwner() {
            LicenseResponse license = issueLicense(3, 2);
            licenseService.validateAndActivateByUser(USER_ID, new ValidateRequest(
                    null, license.productId(), license.id(), "device-x",
                    "1.0", "Windows", null, null));
            Activation activation = activationRepository
                    .findByLicenseIdAndDeviceFingerprint(license.id(), "device-x").orElseThrow();

            UUID otherUser = UUID.randomUUID();
            assertThatThrownBy(() -> licenseService.deactivateByActivationIdWithOwnerCheck(
                    otherUser, license.id(), activation.getId()))
                    .isInstanceOf(LicenseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", LicenseException.ErrorCode.ACCESS_DENIED);
        }

        @Test
        @DisplayName("경로 licenseId 와 activation 소유 라이선스 불일치 시 INVALID_ACTIVATION_OWNERSHIP")
        void shouldRejectMismatchedLicense() {
            LicenseResponse licenseA = issueLicense(UUID.randomUUID(), 3, 2);
            LicenseResponse licenseB = issueLicense(UUID.randomUUID(), 3, 2);
            licenseService.validateAndActivateByUser(USER_ID, new ValidateRequest(
                    null, licenseA.productId(), licenseA.id(), "device-a",
                    "1.0", "Windows", null, null));
            Activation activationA = activationRepository
                    .findByLicenseIdAndDeviceFingerprint(licenseA.id(), "device-a").orElseThrow();

            // licenseB 경로로 licenseA 의 activation 반납 시도
            assertThatThrownBy(() -> licenseService.deactivateByActivationIdWithOwnerCheck(
                    USER_ID, licenseB.id(), activationA.getId()))
                    .isInstanceOf(LicenseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", LicenseException.ErrorCode.INVALID_ACTIVATION_OWNERSHIP);
        }

        @Test
        @DisplayName("존재하지 않는 activationId 반납 시 ACTIVATION_NOT_FOUND")
        void shouldRejectUnknownActivation() {
            LicenseResponse license = issueLicense(3, 2);
            assertThatThrownBy(() -> licenseService.deactivateByActivationIdWithOwnerCheck(
                    USER_ID, license.id(), UUID.randomUUID()))
                    .isInstanceOf(LicenseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", LicenseException.ErrorCode.ACTIVATION_NOT_FOUND);
        }
    }

    // ==========================================
    // helpers
    // ==========================================

    private LicenseResponse issueLicense(int maxActivations, int maxConcurrentSessions) {
        return issueLicense(UUID.randomUUID(), maxActivations, maxConcurrentSessions);
    }

    private LicenseResponse issueLicense(UUID productId, int maxActivations, int maxConcurrentSessions) {
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
