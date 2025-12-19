package com.bulc.homepage.licensing.service;

import com.bulc.homepage.licensing.domain.*;
import com.bulc.homepage.licensing.dto.*;
import com.bulc.homepage.licensing.exception.LicenseException;
import com.bulc.homepage.licensing.exception.LicenseException.ErrorCode;
import com.bulc.homepage.licensing.repository.ActivationRepository;
import com.bulc.homepage.licensing.repository.LicensePlanRepository;
import com.bulc.homepage.licensing.repository.LicenseRepository;
import com.bulc.homepage.licensing.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * LicenseService 유닛 테스트.
 *
 * Repository를 mock으로 두고 비즈니스 로직을 검증.
 * DB/Spring Context 없이 순수 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class LicenseServiceTest {

    @Mock
    private LicenseRepository licenseRepository;

    @Mock
    private ActivationRepository activationRepository;

    @Mock
    private LicensePlanRepository planRepository;

    @Mock
    private ProductRepository productRepository;

    private LicenseService licenseService;

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final String LICENSE_KEY = "TEST-1234-5678-ABCD";
    private static final String TEST_JWT_SECRET = "TestSecretKeyForUnitTestsMustBeAtLeast32Characters";

    @BeforeEach
    void setUp() {
        licenseService = new LicenseService(
                licenseRepository,
                activationRepository,
                planRepository,
                productRepository,
                TEST_JWT_SECRET
        );
    }

    // ==========================================
    // 라이선스 발급 테스트 (Billing에서 호출)
    // ==========================================

    @Nested
    @DisplayName("라이선스 발급 (issueLicense)")
    class IssueLicense {

        @Test
        @DisplayName("정상 발급 시 ACTIVE 상태의 라이선스 생성")
        void shouldIssueLicenseWithActiveStatus() {
            // given
            LicenseIssueRequest request = new LicenseIssueRequest(
                    OwnerType.USER,
                    OWNER_ID,
                    PRODUCT_ID,
                    null,
                    LicenseType.SUBSCRIPTION,
                    UsageCategory.COMMERCIAL,
                    null,
                    Instant.now().plus(365, ChronoUnit.DAYS),
                    null,
                    ORDER_ID
            );

            given(licenseRepository.findByOwnerTypeAndOwnerIdAndProductId(
                    any(), any(), any())).willReturn(Optional.empty());
            given(licenseRepository.save(any(License.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // when
            LicenseResponse response = licenseService.issueLicense(request);

            // then
            assertThat(response.status()).isEqualTo(LicenseStatus.ACTIVE);
            assertThat(response.ownerType()).isEqualTo(OwnerType.USER);
            assertThat(response.ownerId()).isEqualTo(OWNER_ID);
            assertThat(response.productId()).isEqualTo(PRODUCT_ID);

            verify(licenseRepository).save(any(License.class));
        }

        @Test
        @DisplayName("기본 정책 스냅샷이 적용됨")
        void shouldApplyDefaultPolicySnapshot() {
            // given
            LicenseIssueRequest request = new LicenseIssueRequest(
                    OwnerType.USER, OWNER_ID, PRODUCT_ID, null,
                    LicenseType.SUBSCRIPTION, null, null, null, null, ORDER_ID
            );

            given(licenseRepository.findByOwnerTypeAndOwnerIdAndProductId(
                    any(), any(), any())).willReturn(Optional.empty());

            ArgumentCaptor<License> licenseCaptor = ArgumentCaptor.forClass(License.class);
            given(licenseRepository.save(licenseCaptor.capture()))
                    .willAnswer(inv -> inv.getArgument(0));

            // when
            licenseService.issueLicense(request);

            // then
            License savedLicense = licenseCaptor.getValue();
            assertThat(savedLicense.getMaxActivations()).isEqualTo(3);
            assertThat(savedLicense.getMaxConcurrentSessions()).isEqualTo(2);
            assertThat(savedLicense.getGracePeriodDays()).isEqualTo(7);
        }

        @Test
        @DisplayName("동일 제품에 ACTIVE 라이선스가 있으면 예외 발생")
        void shouldThrowWhenActiveLicenseExists() {
            // given
            License existingLicense = createMockLicense(LicenseStatus.ACTIVE);
            given(licenseRepository.findByOwnerTypeAndOwnerIdAndProductId(
                    any(), any(), any())).willReturn(Optional.of(existingLicense));

            LicenseIssueRequest request = new LicenseIssueRequest(
                    OwnerType.USER, OWNER_ID, PRODUCT_ID, null,
                    LicenseType.SUBSCRIPTION, null, null, null, null, ORDER_ID
            );

            // when & then
            assertThatThrownBy(() -> licenseService.issueLicense(request))
                    .isInstanceOf(LicenseException.class)
                    .extracting(ex -> ((LicenseException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.LICENSE_ALREADY_EXISTS);
        }

        @Test
        @DisplayName("기존 라이선스가 REVOKED 상태면 새로 발급 가능")
        void shouldAllowIssueWhenExistingLicenseIsRevoked() {
            // given
            License revokedLicense = createMockLicense(LicenseStatus.REVOKED);
            given(licenseRepository.findByOwnerTypeAndOwnerIdAndProductId(
                    any(), any(), any())).willReturn(Optional.of(revokedLicense));
            given(licenseRepository.save(any(License.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            LicenseIssueRequest request = new LicenseIssueRequest(
                    OwnerType.USER, OWNER_ID, PRODUCT_ID, null,
                    LicenseType.SUBSCRIPTION, null, null, null, null, ORDER_ID
            );

            // when
            LicenseResponse response = licenseService.issueLicense(request);

            // then
            assertThat(response.status()).isEqualTo(LicenseStatus.ACTIVE);
        }
    }

    // ==========================================
    // 라이선스 검증/활성화 테스트 (클라이언트에서 호출)
    // ==========================================

    @Nested
    @DisplayName("라이선스 검증/활성화 (validateAndActivate)")
    class ValidateAndActivate {

        @Test
        @DisplayName("유효한 라이선스 검증 시 성공 응답 반환")
        void shouldReturnSuccessForValidLicense() {
            // given
            License license = createActiveLicenseWithPolicy();
            given(licenseRepository.findByLicenseKeyWithLock(LICENSE_KEY))
                    .willReturn(Optional.of(license));
            given(licenseRepository.save(any(License.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            ActivationRequest request = new ActivationRequest(
                    "device-123", "1.0.0", "Windows 11", "192.168.1.1"
            );

            // when
            ValidationResponse response = licenseService.validateAndActivate(LICENSE_KEY, request);

            // then
            assertThat(response.valid()).isTrue();
            assertThat(response.status()).isEqualTo(LicenseStatus.ACTIVE);
            assertThat(response.entitlements()).contains("core-simulation");
            assertThat(response.offlineToken()).isNotNull();
        }

        @Test
        @DisplayName("존재하지 않는 라이선스 키로 검증 시 예외 발생")
        void shouldThrowWhenLicenseNotFound() {
            // given
            given(licenseRepository.findByLicenseKeyWithLock(LICENSE_KEY))
                    .willReturn(Optional.empty());

            ActivationRequest request = new ActivationRequest(
                    "device-123", "1.0.0", "Windows", "10.0.0.1"
            );

            // when & then
            assertThatThrownBy(() ->
                    licenseService.validateAndActivate(LICENSE_KEY, request))
                    .isInstanceOf(LicenseException.class)
                    .extracting(ex -> ((LicenseException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.LICENSE_NOT_FOUND);
        }

        @Test
        @DisplayName("만료된(EXPIRED_HARD) 라이선스는 검증 실패")
        void shouldFailValidationForExpiredHardLicense() {
            // given
            License license = createExpiredLicense();
            given(licenseRepository.findByLicenseKeyWithLock(LICENSE_KEY))
                    .willReturn(Optional.of(license));

            ActivationRequest request = new ActivationRequest(
                    "device-123", "1.0.0", "Windows", "10.0.0.1"
            );

            // when
            ValidationResponse response = licenseService.validateAndActivate(LICENSE_KEY, request);

            // then
            assertThat(response.valid()).isFalse();
            assertThat(response.errorCode()).isEqualTo("LICENSE_EXPIRED");
        }

        @Test
        @DisplayName("정지된(SUSPENDED) 라이선스는 검증 실패")
        void shouldFailValidationForSuspendedLicense() {
            // given
            License license = createActiveLicenseWithPolicy();
            license.suspend("테스트 정지");
            given(licenseRepository.findByLicenseKeyWithLock(LICENSE_KEY))
                    .willReturn(Optional.of(license));

            ActivationRequest request = new ActivationRequest(
                    "device-123", "1.0.0", "Windows", "10.0.0.1"
            );

            // when
            ValidationResponse response = licenseService.validateAndActivate(LICENSE_KEY, request);

            // then
            assertThat(response.valid()).isFalse();
            assertThat(response.errorCode()).isEqualTo("LICENSE_SUSPENDED");
        }

        @Test
        @DisplayName("회수된(REVOKED) 라이선스는 검증 실패")
        void shouldFailValidationForRevokedLicense() {
            // given
            License license = createActiveLicenseWithPolicy();
            license.revoke("환불");
            given(licenseRepository.findByLicenseKeyWithLock(LICENSE_KEY))
                    .willReturn(Optional.of(license));

            ActivationRequest request = new ActivationRequest(
                    "device-123", "1.0.0", "Windows", "10.0.0.1"
            );

            // when
            ValidationResponse response = licenseService.validateAndActivate(LICENSE_KEY, request);

            // then
            assertThat(response.valid()).isFalse();
            assertThat(response.errorCode()).isEqualTo("LICENSE_REVOKED");
        }

        @Test
        @DisplayName("기기 수 초과 시 검증 실패")
        void shouldFailValidationWhenActivationLimitExceeded() {
            // given
            License license = createLicenseWithMaxActivations(2);
            // 이미 2개 기기 활성화됨
            license.addActivation("device-1", "1.0", "Windows", "10.0.0.1");
            license.addActivation("device-2", "1.0", "macOS", "10.0.0.2");

            given(licenseRepository.findByLicenseKeyWithLock(LICENSE_KEY))
                    .willReturn(Optional.of(license));
            given(activationRepository.countByLicenseIdAndStatus(any(), eq(ActivationStatus.ACTIVE)))
                    .willReturn(2L);

            ActivationRequest request = new ActivationRequest(
                    "device-3", "1.0.0", "Linux", "10.0.0.3"
            );

            // when
            ValidationResponse response = licenseService.validateAndActivate(LICENSE_KEY, request);

            // then
            assertThat(response.valid()).isFalse();
            assertThat(response.errorCode()).isIn(
                    "ACTIVATION_LIMIT_EXCEEDED",
                    "CONCURRENT_SESSION_LIMIT_EXCEEDED"
            );
        }
    }

    // ==========================================
    // 라이선스 조회 테스트
    // ==========================================

    @Nested
    @DisplayName("라이선스 조회")
    class GetLicense {

        @Test
        @DisplayName("ID로 라이선스 조회")
        void shouldGetLicenseById() {
            // given
            UUID licenseId = UUID.randomUUID();
            License license = createActiveLicenseWithPolicy();
            given(licenseRepository.findById(licenseId))
                    .willReturn(Optional.of(license));

            // when
            LicenseResponse response = licenseService.getLicense(licenseId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(LicenseStatus.ACTIVE);
        }

        @Test
        @DisplayName("존재하지 않는 ID로 조회 시 예외 발생")
        void shouldThrowWhenLicenseNotFoundById() {
            // given
            UUID licenseId = UUID.randomUUID();
            given(licenseRepository.findById(licenseId))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> licenseService.getLicense(licenseId))
                    .isInstanceOf(LicenseException.class)
                    .extracting(ex -> ((LicenseException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.LICENSE_NOT_FOUND);
        }

        @Test
        @DisplayName("라이선스 키로 조회")
        void shouldGetLicenseByKey() {
            // given
            License license = createActiveLicenseWithPolicy();
            given(licenseRepository.findByLicenseKey(LICENSE_KEY))
                    .willReturn(Optional.of(license));

            // when
            LicenseResponse response = licenseService.getLicenseByKey(LICENSE_KEY);

            // then
            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("소유자별 라이선스 목록 조회")
        void shouldGetLicensesByOwner() {
            // given
            License license1 = createActiveLicenseWithPolicy();
            License license2 = createActiveLicenseWithPolicy();
            given(licenseRepository.findByOwnerTypeAndOwnerId(OwnerType.USER, OWNER_ID))
                    .willReturn(List.of(license1, license2));

            // when
            var responses = licenseService.getLicensesByOwner(OwnerType.USER, OWNER_ID);

            // then
            assertThat(responses).hasSize(2);
        }
    }

    // ==========================================
    // 기기 비활성화 테스트
    // ==========================================

    @Nested
    @DisplayName("기기 비활성화 (deactivate)")
    class Deactivate {

        @Test
        @DisplayName("정상 비활성화")
        void shouldDeactivateDevice() {
            // given
            UUID licenseId = UUID.randomUUID();
            License license = createActiveLicenseWithPolicy();
            Activation activation = Activation.builder()
                    .license(license)
                    .deviceFingerprint("device-123")
                    .build();

            given(licenseRepository.findById(licenseId))
                    .willReturn(Optional.of(license));
            given(activationRepository.findByLicenseIdAndDeviceFingerprint(licenseId, "device-123"))
                    .willReturn(Optional.of(activation));

            // when
            licenseService.deactivate(licenseId, "device-123");

            // then
            assertThat(activation.getStatus()).isEqualTo(ActivationStatus.DEACTIVATED);
            verify(activationRepository).save(activation);
        }

        @Test
        @DisplayName("존재하지 않는 활성화 비활성화 시 예외 발생")
        void shouldThrowWhenActivationNotFound() {
            // given
            UUID licenseId = UUID.randomUUID();
            License license = createActiveLicenseWithPolicy();

            given(licenseRepository.findById(licenseId))
                    .willReturn(Optional.of(license));
            given(activationRepository.findByLicenseIdAndDeviceFingerprint(licenseId, "device-123"))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> licenseService.deactivate(licenseId, "device-123"))
                    .isInstanceOf(LicenseException.class)
                    .extracting(ex -> ((LicenseException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.ACTIVATION_NOT_FOUND);
        }
    }

    // ==========================================
    // 라이선스 정지 테스트 (Admin에서 호출)
    // ==========================================

    @Nested
    @DisplayName("라이선스 정지 (suspendLicense)")
    class SuspendLicense {

        @Test
        @DisplayName("정상 정지")
        void shouldSuspendLicense() {
            // given
            UUID licenseId = UUID.randomUUID();
            License license = createActiveLicenseWithPolicy();

            given(licenseRepository.findById(licenseId))
                    .willReturn(Optional.of(license));
            given(licenseRepository.save(any(License.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // when
            LicenseResponse response = licenseService.suspendLicense(licenseId, "관리자 정지");

            // then
            assertThat(response.status()).isEqualTo(LicenseStatus.SUSPENDED);
        }
    }

    // ==========================================
    // 라이선스 회수 테스트 (Billing에서 호출)
    // ==========================================

    @Nested
    @DisplayName("라이선스 회수 (revokeLicense)")
    class RevokeLicense {

        @Test
        @DisplayName("ID로 회수")
        void shouldRevokeLicenseById() {
            // given
            UUID licenseId = UUID.randomUUID();
            License license = createActiveLicenseWithPolicy();

            given(licenseRepository.findById(licenseId))
                    .willReturn(Optional.of(license));
            given(licenseRepository.save(any(License.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // when
            LicenseResponse response = licenseService.revokeLicense(licenseId, "환불");

            // then
            assertThat(response.status()).isEqualTo(LicenseStatus.REVOKED);
        }

        @Test
        @DisplayName("주문 ID로 회수 (Billing 연동)")
        void shouldRevokeLicenseByOrderId() {
            // given
            License license = createActiveLicenseWithPolicy();

            given(licenseRepository.findBySourceOrderId(ORDER_ID))
                    .willReturn(Optional.of(license));
            given(licenseRepository.save(any(License.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // when
            LicenseResponse response = licenseService.revokeLicenseByOrderId(ORDER_ID, "환불");

            // then
            assertThat(response.status()).isEqualTo(LicenseStatus.REVOKED);
        }

        @Test
        @DisplayName("존재하지 않는 주문 ID로 회수 시 예외 발생")
        void shouldThrowWhenOrderIdNotFound() {
            // given
            given(licenseRepository.findBySourceOrderId(ORDER_ID))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> licenseService.revokeLicenseByOrderId(ORDER_ID, "환불"))
                    .isInstanceOf(LicenseException.class)
                    .extracting(ex -> ((LicenseException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.LICENSE_NOT_FOUND);
        }
    }

    // ==========================================
    // 구독 갱신 테스트 (Billing에서 호출)
    // ==========================================

    @Nested
    @DisplayName("구독 갱신 (renewLicense)")
    class RenewLicense {

        @Test
        @DisplayName("정상 갱신")
        void shouldRenewLicense() {
            // given
            UUID licenseId = UUID.randomUUID();
            License license = createActiveLicenseWithPolicy();
            Instant newValidUntil = Instant.now().plus(365, ChronoUnit.DAYS);

            given(licenseRepository.findById(licenseId))
                    .willReturn(Optional.of(license));
            given(licenseRepository.save(any(License.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // when
            LicenseResponse response = licenseService.renewLicense(licenseId, newValidUntil);

            // then
            assertThat(response.validUntil()).isEqualTo(newValidUntil);
            assertThat(response.status()).isEqualTo(LicenseStatus.ACTIVE);
        }
    }

    // ==========================================
    // v1.1 계정 기반 검증 테스트 (validateAndActivateByUser)
    // ==========================================

    @Nested
    @DisplayName("계정 기반 검증 (validateAndActivateByUser)")
    class ValidateByUser {

        @Test
        @DisplayName("ACTIVE 라이선스가 있으면 해당 라이선스로 검증")
        void shouldPickActiveLicenseWhenMultipleLicensesExist() {
            // given
            UUID userId = UUID.randomUUID();
            License activeLicense = createActiveLicenseWithPolicy();
            List<License> licenses = List.of(activeLicense);

            given(licenseRepository.findByOwnerAndProductAndStatusInWithLock(
                    eq(OwnerType.USER), eq(userId), eq(PRODUCT_ID), any()))
                    .willReturn(licenses);
            given(licenseRepository.save(any(License.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            ValidateRequest request = new ValidateRequest(
                    null, PRODUCT_ID, null, "device-123", "1.0.0", "Windows"
            );

            // when
            ValidationResponse response = licenseService.validateAndActivateByUser(userId, request);

            // then
            assertThat(response.valid()).isTrue();
            assertThat(response.licenseId()).isEqualTo(activeLicense.getId());
        }

        @Test
        @DisplayName("해당 제품의 라이선스가 없으면 LICENSE_NOT_FOUND_FOR_PRODUCT 예외")
        void shouldThrowWhenNoLicenseFoundForProductAndUser() {
            // given
            UUID userId = UUID.randomUUID();
            given(licenseRepository.findByOwnerAndProductAndStatusInWithLock(
                    eq(OwnerType.USER), eq(userId), eq(PRODUCT_ID), any()))
                    .willReturn(List.of());

            ValidateRequest request = new ValidateRequest(
                    null, PRODUCT_ID, null, "device-123", "1.0.0", "Windows"
            );

            // when & then
            assertThatThrownBy(() -> licenseService.validateAndActivateByUser(userId, request))
                    .isInstanceOf(LicenseException.class)
                    .extracting(ex -> ((LicenseException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.LICENSE_NOT_FOUND_FOR_PRODUCT);
        }
    }

    // ==========================================
    // v1.1 계정 기반 Heartbeat 테스트
    // ==========================================

    @Nested
    @DisplayName("계정 기반 Heartbeat (heartbeatByUser)")
    class HeartbeatByUser {

        @Test
        @DisplayName("등록되지 않은 기기로 heartbeat 시 ACTIVATION_NOT_FOUND 예외")
        void shouldThrowActivationNotFoundWhenHeartbeatFromUnregisteredDevice() {
            // given
            UUID userId = UUID.randomUUID();
            License license = createActiveLicenseWithPolicy();  // 활성화된 기기 없음

            given(licenseRepository.findByOwnerAndProductAndStatusInWithLock(
                    eq(OwnerType.USER), eq(userId), eq(PRODUCT_ID), any()))
                    .willReturn(List.of(license));

            ValidateRequest request = new ValidateRequest(
                    null, PRODUCT_ID, null, "unregistered-device", "1.0.0", "Windows"
            );

            // when & then
            assertThatThrownBy(() -> licenseService.heartbeatByUser(userId, request))
                    .isInstanceOf(LicenseException.class)
                    .extracting(ex -> ((LicenseException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.ACTIVATION_NOT_FOUND);
        }

        @Test
        @DisplayName("등록된 기기로 heartbeat 시 lastSeenAt 갱신")
        void shouldUpdateLastSeenAtOnHeartbeat() {
            // given
            UUID userId = UUID.randomUUID();
            License license = createActiveLicenseWithPolicy();
            license.addActivation("registered-device", "1.0.0", "Windows", "10.0.0.1");

            given(licenseRepository.findByOwnerAndProductAndStatusInWithLock(
                    eq(OwnerType.USER), eq(userId), eq(PRODUCT_ID), any()))
                    .willReturn(List.of(license));
            given(licenseRepository.save(any(License.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            ValidateRequest request = new ValidateRequest(
                    null, PRODUCT_ID, null, "registered-device", "2.0.0", "Windows 11"
            );

            // when
            ValidationResponse response = licenseService.heartbeatByUser(userId, request);

            // then
            assertThat(response.valid()).isTrue();
        }

        @Test
        @DisplayName("해당 제품의 라이선스가 없으면 LICENSE_NOT_FOUND_FOR_PRODUCT 예외")
        void shouldThrowWhenNoLicenseForHeartbeat() {
            // given
            UUID userId = UUID.randomUUID();
            given(licenseRepository.findByOwnerAndProductAndStatusInWithLock(
                    eq(OwnerType.USER), eq(userId), eq(PRODUCT_ID), any()))
                    .willReturn(List.of());

            ValidateRequest request = new ValidateRequest(
                    null, PRODUCT_ID, null, "device-123", "1.0.0", "Windows"
            );

            // when & then
            assertThatThrownBy(() -> licenseService.heartbeatByUser(userId, request))
                    .isInstanceOf(LicenseException.class)
                    .extracting(ex -> ((LicenseException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.LICENSE_NOT_FOUND_FOR_PRODUCT);
        }
    }

    // ==========================================
    // v1.1 소유자 검증 테스트
    // ==========================================

    @Nested
    @DisplayName("소유자 검증 포함 기기 비활성화 (deactivateWithOwnerCheck)")
    class DeactivateWithOwnerCheck {

        @Test
        @DisplayName("본인 소유 라이선스의 기기 비활성화 성공")
        void shouldDeactivateOwnedLicenseDevice() {
            // given
            UUID userId = UUID.randomUUID();
            UUID licenseId = UUID.randomUUID();
            License license = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(userId)
                    .productId(PRODUCT_ID)
                    .licenseType(LicenseType.SUBSCRIPTION)
                    .validUntil(Instant.now().plus(30, ChronoUnit.DAYS))
                    .build();
            license.activate();

            Activation activation = Activation.builder()
                    .license(license)
                    .deviceFingerprint("device-123")
                    .build();

            given(licenseRepository.findById(licenseId)).willReturn(Optional.of(license));
            given(activationRepository.findByLicenseIdAndDeviceFingerprint(licenseId, "device-123"))
                    .willReturn(Optional.of(activation));

            // when
            licenseService.deactivateWithOwnerCheck(userId, licenseId, "device-123");

            // then
            assertThat(activation.getStatus()).isEqualTo(ActivationStatus.DEACTIVATED);
            verify(activationRepository).save(activation);
        }

        @Test
        @DisplayName("타인 소유 라이선스 비활성화 시 ACCESS_DENIED 예외")
        void shouldThrowAccessDeniedWhenNotOwner() {
            // given
            UUID userId = UUID.randomUUID();
            UUID otherUserId = UUID.randomUUID();
            UUID licenseId = UUID.randomUUID();
            License license = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(otherUserId)
                    .productId(PRODUCT_ID)
                    .licenseType(LicenseType.SUBSCRIPTION)
                    .validUntil(Instant.now().plus(30, ChronoUnit.DAYS))
                    .build();
            license.activate();

            given(licenseRepository.findById(licenseId)).willReturn(Optional.of(license));

            // when & then
            assertThatThrownBy(() ->
                    licenseService.deactivateWithOwnerCheck(userId, licenseId, "device-123"))
                    .isInstanceOf(LicenseException.class)
                    .extracting(ex -> ((LicenseException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.ACCESS_DENIED);
        }
    }

    @Nested
    @DisplayName("소유자 검증 포함 라이선스 조회 (getLicenseWithOwnerCheck)")
    class GetLicenseWithOwnerCheck {

        @Test
        @DisplayName("본인 소유 라이선스 조회 성공")
        void shouldGetOwnedLicense() {
            // given
            UUID userId = UUID.randomUUID();
            UUID licenseId = UUID.randomUUID();
            License license = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(userId)
                    .productId(PRODUCT_ID)
                    .licenseType(LicenseType.SUBSCRIPTION)
                    .validUntil(Instant.now().plus(30, ChronoUnit.DAYS))
                    .policySnapshot(Map.of("entitlements", List.of("core")))
                    .build();
            license.activate();

            given(licenseRepository.findById(licenseId)).willReturn(Optional.of(license));

            // when
            LicenseResponse response = licenseService.getLicenseWithOwnerCheck(userId, licenseId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.ownerId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("타인 소유 라이선스 조회 시 ACCESS_DENIED 예외")
        void shouldThrowAccessDeniedWhenNotOwner() {
            // given
            UUID userId = UUID.randomUUID();
            UUID otherUserId = UUID.randomUUID();
            UUID licenseId = UUID.randomUUID();
            License license = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(otherUserId)
                    .productId(PRODUCT_ID)
                    .licenseType(LicenseType.SUBSCRIPTION)
                    .validUntil(Instant.now().plus(30, ChronoUnit.DAYS))
                    .build();
            license.activate();

            given(licenseRepository.findById(licenseId)).willReturn(Optional.of(license));

            // when & then
            assertThatThrownBy(() -> licenseService.getLicenseWithOwnerCheck(userId, licenseId))
                    .isInstanceOf(LicenseException.class)
                    .extracting(ex -> ((LicenseException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.ACCESS_DENIED);
        }
    }

    // ==========================================
    // 헬퍼 메서드
    // ==========================================

    private License createMockLicense(LicenseStatus status) {
        License license = License.builder()
                .ownerType(OwnerType.USER)
                .ownerId(OWNER_ID)
                .productId(PRODUCT_ID)
                .licenseType(LicenseType.SUBSCRIPTION)
                .validUntil(Instant.now().plus(30, ChronoUnit.DAYS))
                .build();

        if (status == LicenseStatus.ACTIVE) {
            license.activate();
        } else if (status == LicenseStatus.SUSPENDED) {
            license.activate();
            license.suspend("test");
        } else if (status == LicenseStatus.REVOKED) {
            license.activate();
            license.revoke("test");
        }

        return license;
    }

    private License createActiveLicenseWithPolicy() {
        Map<String, Object> policy = Map.of(
                "maxActivations", 3,
                "maxConcurrentSessions", 2,
                "gracePeriodDays", 7,
                "allowOfflineDays", 30,
                "entitlements", List.of("core-simulation")
        );

        License license = License.builder()
                .ownerType(OwnerType.USER)
                .ownerId(OWNER_ID)
                .productId(PRODUCT_ID)
                .licenseType(LicenseType.SUBSCRIPTION)
                .validUntil(Instant.now().plus(30, ChronoUnit.DAYS))
                .policySnapshot(policy)
                .licenseKey(LICENSE_KEY)
                .sourceOrderId(ORDER_ID)
                .build();
        license.activate();
        // 유닛 테스트에서는 JPA가 없어서 ID가 자동 생성되지 않으므로 수동 설정
        ReflectionTestUtils.setField(license, "id", UUID.randomUUID());
        return license;
    }

    private License createExpiredLicense() {
        Map<String, Object> policy = Map.of("gracePeriodDays", 7);

        License license = License.builder()
                .ownerType(OwnerType.USER)
                .ownerId(OWNER_ID)
                .productId(PRODUCT_ID)
                .licenseType(LicenseType.SUBSCRIPTION)
                .validUntil(Instant.now().minus(10, ChronoUnit.DAYS)) // 10일 전 만료
                .policySnapshot(policy)
                .licenseKey(LICENSE_KEY)
                .build();
        license.activate();
        ReflectionTestUtils.setField(license, "id", UUID.randomUUID());
        return license;
    }

    private License createLicenseWithMaxActivations(int maxActivations) {
        Map<String, Object> policy = Map.of(
                "maxActivations", maxActivations,
                "maxConcurrentSessions", maxActivations
        );

        License license = License.builder()
                .ownerType(OwnerType.USER)
                .ownerId(OWNER_ID)
                .productId(PRODUCT_ID)
                .licenseType(LicenseType.SUBSCRIPTION)
                .validUntil(Instant.now().plus(30, ChronoUnit.DAYS))
                .policySnapshot(policy)
                .licenseKey(LICENSE_KEY)
                .build();
        license.activate();
        ReflectionTestUtils.setField(license, "id", UUID.randomUUID());
        return license;
    }
}
