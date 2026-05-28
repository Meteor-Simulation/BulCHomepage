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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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
import static org.mockito.Mockito.lenient;

/**
 * LicenseService 유닛 테스트.
 *
 * Repository를 mock으로 두고 비즈니스 로직을 검증.
 * DB/Spring Context 없이 순수 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LicenseServiceTest {

    @Mock
    private LicenseRepository licenseRepository;

    @Mock
    private ActivationRepository activationRepository;

    @Mock
    private LicensePlanRepository planRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SessionTokenService sessionTokenService;

    @Mock
    private OfflineTokenService offlineTokenService;

    private LicenseService licenseService;

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final String LICENSE_KEY = "TEST-1234-5678-ABCD";

    @BeforeEach
    void setUp() {
        licenseService = new LicenseService(
                licenseRepository,
                activationRepository,
                planRepository,
                productRepository,
                sessionTokenService,
                offlineTokenService,
                30  // v0.3.0: staleThresholdMinutes
        );

        // v1.1.2: sessionToken mock 기본 설정 (lenient - 모든 테스트에서 사용되지 않아도 OK)
        // SessionToken은 이제 token만 포함 (exp는 토큰 내부 클레임으로 판단)
        lenient().when(sessionTokenService.generateSessionToken(any(), any(), any(), any()))
                .thenReturn(new SessionTokenService.SessionToken("mock-session-token"));

        // v1.1.3: offlineToken mock 기본 설정
        lenient().when(offlineTokenService.shouldRenew(any(), anyInt())).thenReturn(true);
        lenient().when(offlineTokenService.generateOfflineToken(any(), any(), any(), any(), anyInt(), any()))
                .thenReturn(new OfflineTokenService.OfflineToken("mock-offline-token", Instant.now().plusSeconds(86400)));
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
        @DisplayName("동일 제품에 ACTIVE 라이선스가 있어도 새 라이선스 발급 가능 (1인 다중 라이선스 허용)")
        void shouldAllowIssueWhenActiveLicenseExists() {
            // given - 1인 다중 라이선스 허용으로 변경됨
            given(licenseRepository.save(any(License.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            LicenseIssueRequest request = new LicenseIssueRequest(
                    OwnerType.USER, OWNER_ID, PRODUCT_ID, null,
                    LicenseType.SUBSCRIPTION, null, null, null, null, ORDER_ID
            );

            // when
            LicenseResponse response = licenseService.issueLicense(request);

            // then - 새 라이선스가 정상 발급됨
            assertThat(response.status()).isEqualTo(LicenseStatus.ACTIVE);
            verify(licenseRepository).save(any(License.class));
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
            // v0.3.0: countActiveSessions 사용
            given(activationRepository.countActiveSessions(any(), any()))
                    .willReturn(2L);
            given(activationRepository.findActiveSessions(any(), any()))
                    .willReturn(List.of());

            ActivationRequest request = new ActivationRequest(
                    "device-3", "1.0.0", "Linux", "10.0.0.3"
            );

            // when
            ValidationResponse response = licenseService.validateAndActivate(LICENSE_KEY, request);

            // then
            assertThat(response.valid()).isFalse();
            // v0.3.0: ALL_LICENSES_FULL 또는 ACTIVATION_LIMIT_EXCEEDED
            assertThat(response.errorCode()).isIn(
                    "ACTIVATION_LIMIT_EXCEEDED",
                    "ALL_LICENSES_FULL"
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
                    null, PRODUCT_ID, null, "device-123", "1.0.0", "Windows", null, null
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
                    null, PRODUCT_ID, null, "device-123", "1.0.0", "Windows", null, null
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
                    null, PRODUCT_ID, null, "unregistered-device", "1.0.0", "Windows", null, null
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
                    null, PRODUCT_ID, null, "registered-device", "2.0.0", "Windows 11", null, null
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
                    null, PRODUCT_ID, null, "device-123", "1.0.0", "Windows", null, null
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
    // TRIAL Chaining (B안 지연 시작) 테스트
    // ==========================================

    @Nested
    @DisplayName("TRIAL 체이닝 (issueLicenseForRedeem)")
    class TrialChaining {

        @Test
        @DisplayName("기존 ACTIVE TRIAL 없으면 즉시 시작 (chaining 없음)")
        void shouldStartImmediatelyWhenNoExistingTrial() {
            UUID userId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID planId = UUID.randomUUID();
            LicensePlan plan = createTrialPlan(productId, 14);
            given(planRepository.findAvailableById(planId)).willReturn(Optional.of(plan));
            given(licenseRepository.findChainableTrialsWithLock(
                    eq(OwnerType.USER), eq(userId), eq(productId),
                    eq(LicenseType.TRIAL), any()))
                    .willReturn(List.of());

            ArgumentCaptor<License> captor = ArgumentCaptor.forClass(License.class);
            given(licenseRepository.save(captor.capture()))
                    .willAnswer(inv -> inv.getArgument(0));

            Instant before = Instant.now();
            License result = licenseService.issueLicenseForRedeem(
                    userId, planId, UsageCategory.COMMERCIAL);

            License saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(LicenseStatus.ACTIVE);
            assertThat(saved.getValidFrom()).isAfterOrEqualTo(before);
            // 약 14일 뒤 만료
            assertThat(saved.getValidUntil())
                    .isAfter(before.plus(13, ChronoUnit.DAYS))
                    .isBefore(before.plus(15, ChronoUnit.DAYS));
        }

        @Test
        @DisplayName("동일 productId의 ACTIVE TRIAL 존재 시 PENDING + chained validFrom")
        void shouldChainAfterExistingTrialValidUntil() {
            UUID userId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID planId = UUID.randomUUID();
            LicensePlan plan = createTrialPlan(productId, 14);
            given(planRepository.findAvailableById(planId)).willReturn(Optional.of(plan));

            Instant existingValidUntil = Instant.now().plus(9, ChronoUnit.DAYS);
            License existingTrial = createExistingTrial(userId, productId, existingValidUntil);
            given(licenseRepository.findChainableTrialsWithLock(
                    eq(OwnerType.USER), eq(userId), eq(productId),
                    eq(LicenseType.TRIAL), any()))
                    .willReturn(List.of(existingTrial));

            ArgumentCaptor<License> captor = ArgumentCaptor.forClass(License.class);
            given(licenseRepository.save(captor.capture()))
                    .willAnswer(inv -> inv.getArgument(0));

            licenseService.issueLicenseForRedeem(userId, planId, UsageCategory.COMMERCIAL);

            License saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(LicenseStatus.PENDING);
            assertThat(saved.getValidFrom()).isEqualTo(existingValidUntil);
            assertThat(saved.getValidUntil())
                    .isEqualTo(existingValidUntil.plus(14, ChronoUnit.DAYS));
        }

        @Test
        @DisplayName("여러 TRIAL 존재 시 가장 늦은 validUntil 뒤로 chaining")
        void shouldChainToLatestValidUntilAmongMultipleTrials() {
            UUID userId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID planId = UUID.randomUUID();
            LicensePlan plan = createTrialPlan(productId, 14);
            given(planRepository.findAvailableById(planId)).willReturn(Optional.of(plan));

            // Repository는 validUntil 내림차순으로 정렬하여 반환 (가장 늦은 것이 첫 번째)
            Instant latestValidUntil = Instant.now().plus(28, ChronoUnit.DAYS);
            License laterTrial = createExistingTrial(userId, productId, latestValidUntil);
            License earlierTrial = createExistingTrial(userId, productId, Instant.now().plus(14, ChronoUnit.DAYS));
            given(licenseRepository.findChainableTrialsWithLock(
                    eq(OwnerType.USER), eq(userId), eq(productId),
                    eq(LicenseType.TRIAL), any()))
                    .willReturn(List.of(laterTrial, earlierTrial));

            ArgumentCaptor<License> captor = ArgumentCaptor.forClass(License.class);
            given(licenseRepository.save(captor.capture()))
                    .willAnswer(inv -> inv.getArgument(0));

            licenseService.issueLicenseForRedeem(userId, planId, UsageCategory.COMMERCIAL);

            License saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(LicenseStatus.PENDING);
            assertThat(saved.getValidFrom()).isEqualTo(latestValidUntil);
        }

        @Test
        @DisplayName("ORGANIZATION 소유자는 chaining 적용 안 함 (issueLicenseWithPlan)")
        void shouldNotChainForOrganizationOwner() {
            UUID orgId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID planId = UUID.randomUUID();
            LicensePlan plan = createTrialPlan(productId, 14);
            given(planRepository.findAvailableById(planId)).willReturn(Optional.of(plan));

            ArgumentCaptor<License> captor = ArgumentCaptor.forClass(License.class);
            given(licenseRepository.save(captor.capture()))
                    .willAnswer(inv -> inv.getArgument(0));

            licenseService.issueLicenseWithPlan(
                    OwnerType.ORG, orgId, planId, null, UsageCategory.COMMERCIAL);

            // ORG의 경우 findChainableTrialsWithLock이 호출되지 않아야 함
            verify(licenseRepository, never()).findChainableTrialsWithLock(
                    any(), any(), any(), any(), any());

            License saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(LicenseStatus.ACTIVE);
        }

        @Test
        @DisplayName("SUBSCRIPTION은 TRIAL chaining 적용 안 함")
        void shouldNotChainForSubscription() {
            UUID userId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID planId = UUID.randomUUID();
            LicensePlan plan = LicensePlan.builder()
                    .productId(productId)
                    .code("SUB_30D")
                    .name("Monthly Subscription")
                    .licenseType(LicenseType.SUBSCRIPTION)
                    .durationDays(30)
                    .graceDays(7)
                    .maxActivations(3)
                    .maxConcurrentSessions(2)
                    .allowOfflineDays(30)
                    .build();
            given(planRepository.findAvailableById(planId)).willReturn(Optional.of(plan));

            ArgumentCaptor<License> captor = ArgumentCaptor.forClass(License.class);
            given(licenseRepository.save(captor.capture()))
                    .willAnswer(inv -> inv.getArgument(0));

            licenseService.issueLicenseWithPlan(
                    OwnerType.USER, userId, planId, null, UsageCategory.COMMERCIAL);

            verify(licenseRepository, never()).findChainableTrialsWithLock(
                    any(), any(), any(), any(), any());

            License saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(LicenseStatus.ACTIVE);
        }

        @Test
        @DisplayName("issueLicenseByAdmin - USER + TRIAL chaining 적용 (PENDING + 지연 validFrom)")
        void shouldChainForAdminTrialIssue() {
            UUID userId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID planId = UUID.randomUUID();
            LicensePlan plan = createTrialPlan(productId, 14);
            given(planRepository.findAvailableById(planId)).willReturn(Optional.of(plan));

            Instant existingValidUntil = Instant.now().plus(10, ChronoUnit.DAYS);
            License existingTrial = createExistingTrial(userId, productId, existingValidUntil);
            given(licenseRepository.findChainableTrialsWithLock(
                    eq(OwnerType.USER), eq(userId), eq(productId),
                    eq(LicenseType.TRIAL), any()))
                    .willReturn(List.of(existingTrial));

            ArgumentCaptor<License> captor = ArgumentCaptor.forClass(License.class);
            given(licenseRepository.save(captor.capture()))
                    .willAnswer(inv -> inv.getArgument(0));

            licenseService.issueLicenseByAdmin(
                    userId, planId, UsageCategory.COMMERCIAL, "관리자 발급 메모");

            License saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(LicenseStatus.PENDING);
            assertThat(saved.getValidFrom()).isEqualTo(existingValidUntil);
            assertThat(saved.getValidUntil())
                    .isEqualTo(existingValidUntil.plus(14, ChronoUnit.DAYS));
            // 관리자 메모/플래그가 정책 스냅샷에 들어가야 함
            assertThat(saved.getPolicySnapshot()).containsEntry("adminMemo", "관리자 발급 메모");
            assertThat(saved.getPolicySnapshot()).containsEntry("issuedByAdmin", true);
        }

        @Test
        @DisplayName("issueLicenseWithPlanForBilling - USER + TRIAL chaining 적용 (반환은 LicenseIssueResult)")
        void shouldChainForBillingTrialIssue() {
            UUID userId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID planId = UUID.randomUUID();
            LicensePlan plan = createTrialPlan(productId, 14);
            given(planRepository.findAvailableById(planId)).willReturn(Optional.of(plan));

            Instant existingValidUntil = Instant.now().plus(7, ChronoUnit.DAYS);
            License existingTrial = createExistingTrial(userId, productId, existingValidUntil);
            given(licenseRepository.findChainableTrialsWithLock(
                    eq(OwnerType.USER), eq(userId), eq(productId),
                    eq(LicenseType.TRIAL), any()))
                    .willReturn(List.of(existingTrial));

            ArgumentCaptor<License> captor = ArgumentCaptor.forClass(License.class);
            given(licenseRepository.save(captor.capture()))
                    .willAnswer(inv -> inv.getArgument(0));

            LicenseIssueResult result = licenseService.issueLicenseWithPlanForBilling(
                    OwnerType.USER, userId, planId, ORDER_ID, UsageCategory.COMMERCIAL);

            assertThat(result).isNotNull();
            assertThat(result.licenseKey()).isNotNull();

            License saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(LicenseStatus.PENDING);
            assertThat(saved.getValidFrom()).isEqualTo(existingValidUntil);
            assertThat(saved.getValidUntil())
                    .isEqualTo(existingValidUntil.plus(14, ChronoUnit.DAYS));
        }

        @Test
        @DisplayName("3중 chaining - 가장 늦은 validUntil(+28d) 뒤에 14d 이어붙임 → 새 라이선스는 PENDING + validUntil = +42d")
        void shouldChainThreeTrialsSequentially() {
            UUID userId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID planId = UUID.randomUUID();
            LicensePlan plan = createTrialPlan(productId, 14);
            given(planRepository.findAvailableById(planId)).willReturn(Optional.of(plan));

            // 가장 늦은 trial: +28d, 그 다음: +14d (Repository는 validUntil DESC 정렬)
            Instant latestValidUntil = Instant.now().plus(28, ChronoUnit.DAYS);
            License laterTrial = createExistingTrial(userId, productId, latestValidUntil);
            License earlierTrial = createExistingTrial(userId, productId,
                    Instant.now().plus(14, ChronoUnit.DAYS));
            given(licenseRepository.findChainableTrialsWithLock(
                    eq(OwnerType.USER), eq(userId), eq(productId),
                    eq(LicenseType.TRIAL), any()))
                    .willReturn(List.of(laterTrial, earlierTrial));

            ArgumentCaptor<License> captor = ArgumentCaptor.forClass(License.class);
            given(licenseRepository.save(captor.capture()))
                    .willAnswer(inv -> inv.getArgument(0));

            licenseService.issueLicenseForRedeem(userId, planId, UsageCategory.COMMERCIAL);

            License saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(LicenseStatus.PENDING);
            assertThat(saved.getValidFrom()).isEqualTo(latestValidUntil);
            // 새 라이선스 validUntil은 +28d + 14d = +42d
            assertThat(saved.getValidUntil())
                    .isEqualTo(latestValidUntil.plus(14, ChronoUnit.DAYS));
        }

        @Test
        @DisplayName("동일 productId지만 plan이 다른 trial 사이에도 chaining 적용 (planId 무관)")
        void shouldChainAcrossDifferentPlansOfSameProduct() {
            UUID userId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID newPlanId = UUID.randomUUID();
            // 새로 발급할 plan은 TRIAL 14D
            LicensePlan newPlan = createTrialPlan(productId, 14);
            given(planRepository.findAvailableById(newPlanId)).willReturn(Optional.of(newPlan));

            // 기존 trial은 TRIAL 7D로 발급된 상태 (planId 다름, 같은 productId)
            Instant existingValidUntil = Instant.now().plus(5, ChronoUnit.DAYS);
            License existingTrial = createExistingTrial(userId, productId, existingValidUntil);
            // 해당 라이선스의 planId를 다른 값으로 설정 (planId가 chaining 판단에 영향 없음을 명시)
            ReflectionTestUtils.setField(existingTrial, "planId", UUID.randomUUID());

            given(licenseRepository.findChainableTrialsWithLock(
                    eq(OwnerType.USER), eq(userId), eq(productId),
                    eq(LicenseType.TRIAL), any()))
                    .willReturn(List.of(existingTrial));

            ArgumentCaptor<License> captor = ArgumentCaptor.forClass(License.class);
            given(licenseRepository.save(captor.capture()))
                    .willAnswer(inv -> inv.getArgument(0));

            licenseService.issueLicenseForRedeem(userId, newPlanId, UsageCategory.COMMERCIAL);

            License saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(LicenseStatus.PENDING);
            assertThat(saved.getValidFrom()).isEqualTo(existingValidUntil);
            assertThat(saved.getValidUntil())
                    .isEqualTo(existingValidUntil.plus(14, ChronoUnit.DAYS));
        }

        @Test
        @DisplayName("usageCategory가 다른 trial이어도 chaining 적용 (usageCategory 무시 정책)")
        void shouldChainRegardlessOfUsageCategory() {
            UUID userId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID planId = UUID.randomUUID();
            LicensePlan plan = createTrialPlan(productId, 14);
            given(planRepository.findAvailableById(planId)).willReturn(Optional.of(plan));

            // 기존 trial은 INTERNAL_EVAL 카테고리로 발급됨
            Instant existingValidUntil = Instant.now().plus(6, ChronoUnit.DAYS);
            License existingTrial = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(userId)
                    .productId(productId)
                    .licenseType(LicenseType.TRIAL)
                    .usageCategory(UsageCategory.INTERNAL_EVAL)
                    .validFrom(Instant.now())
                    .validUntil(existingValidUntil)
                    .build();
            existingTrial.activate();
            ReflectionTestUtils.setField(existingTrial, "id", UUID.randomUUID());

            given(licenseRepository.findChainableTrialsWithLock(
                    eq(OwnerType.USER), eq(userId), eq(productId),
                    eq(LicenseType.TRIAL), any()))
                    .willReturn(List.of(existingTrial));

            ArgumentCaptor<License> captor = ArgumentCaptor.forClass(License.class);
            given(licenseRepository.save(captor.capture()))
                    .willAnswer(inv -> inv.getArgument(0));

            // 새 trial은 COMMERCIAL로 발급
            licenseService.issueLicenseForRedeem(userId, planId, UsageCategory.COMMERCIAL);

            License saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(LicenseStatus.PENDING);
            assertThat(saved.getValidFrom()).isEqualTo(existingValidUntil);
            assertThat(saved.getUsageCategory()).isEqualTo(UsageCategory.COMMERCIAL);
        }

        @Test
        @DisplayName("SOURCE_REDEEM 라이선스는 chaining 적용된 경우에도 policySnapshot.source=REDEEM 유지")
        void shouldKeepRedeemSourceInPolicySnapshotEvenWithChaining() {
            UUID userId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID planId = UUID.randomUUID();
            LicensePlan plan = createTrialPlan(productId, 14);
            given(planRepository.findAvailableById(planId)).willReturn(Optional.of(plan));

            Instant existingValidUntil = Instant.now().plus(5, ChronoUnit.DAYS);
            License existingTrial = createExistingTrial(userId, productId, existingValidUntil);
            given(licenseRepository.findChainableTrialsWithLock(
                    eq(OwnerType.USER), eq(userId), eq(productId),
                    eq(LicenseType.TRIAL), any()))
                    .willReturn(List.of(existingTrial));

            ArgumentCaptor<License> captor = ArgumentCaptor.forClass(License.class);
            given(licenseRepository.save(captor.capture()))
                    .willAnswer(inv -> inv.getArgument(0));

            licenseService.issueLicenseForRedeem(userId, planId, UsageCategory.COMMERCIAL);

            License saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(LicenseStatus.PENDING);
            assertThat(saved.getSourceType()).isEqualTo(LicenseSourceType.REDEEM);
            assertThat(saved.getPolicySnapshot()).containsEntry("source", "REDEEM");
        }

        @Test
        @DisplayName("PERPETUAL 플랜은 chaining 미적용 + 즉시 ACTIVE + validUntil=null")
        void shouldNotChainForPerpetualPlan() {
            UUID userId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID planId = UUID.randomUUID();
            LicensePlan plan = LicensePlan.builder()
                    .productId(productId)
                    .code("PERPETUAL")
                    .name("Perpetual License")
                    .licenseType(LicenseType.PERPETUAL)
                    .durationDays(0)
                    .graceDays(0)
                    .maxActivations(3)
                    .maxConcurrentSessions(2)
                    .allowOfflineDays(0)
                    .build();
            given(planRepository.findAvailableById(planId)).willReturn(Optional.of(plan));

            ArgumentCaptor<License> captor = ArgumentCaptor.forClass(License.class);
            given(licenseRepository.save(captor.capture()))
                    .willAnswer(inv -> inv.getArgument(0));

            licenseService.issueLicenseWithPlan(
                    OwnerType.USER, userId, planId, null, UsageCategory.COMMERCIAL);

            // PERPETUAL은 chaining 분기 미적용 → findChainableTrialsWithLock 호출되지 않음
            verify(licenseRepository, never()).findChainableTrialsWithLock(
                    any(), any(), any(), any(), any());

            License saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(LicenseStatus.ACTIVE);
            assertThat(saved.getValidUntil()).isNull();
        }
    }

    // ==========================================
    // PENDING 라이선스 일괄 활성화 테스트 (스케줄러용)
    // ==========================================

    @Nested
    @DisplayName("PENDING 라이선스 일괄 활성화 (activatePendingLicenses)")
    class ActivatePendingLicenses {

        @Test
        @DisplayName("validFrom 도래한 PENDING 라이선스를 ACTIVE로 전이")
        void shouldActivateDueLicenses() {
            Instant now = Instant.now();
            License dueLicense = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(OWNER_ID)
                    .productId(PRODUCT_ID)
                    .licenseType(LicenseType.TRIAL)
                    .validFrom(now.minus(1, ChronoUnit.MINUTES))
                    .validUntil(now.plus(14, ChronoUnit.DAYS))
                    .build();

            given(licenseRepository.findPendingDueForActivation(any()))
                    .willReturn(List.of(dueLicense));

            int activated = licenseService.activatePendingLicenses();

            assertThat(activated).isEqualTo(1);
            assertThat(dueLicense.getStatus()).isEqualTo(LicenseStatus.ACTIVE);
        }

        @Test
        @DisplayName("대상 없으면 0 반환")
        void shouldReturnZeroWhenNoDuePending() {
            given(licenseRepository.findPendingDueForActivation(any()))
                    .willReturn(List.of());

            int activated = licenseService.activatePendingLicenses();

            assertThat(activated).isZero();
        }

        @Test
        @DisplayName("여러 PENDING 중 일부만 due - due한 것만 카운트, 나머지는 PENDING 유지")
        void shouldOnlyCountDueLicenses() {
            Instant now = Instant.now();
            // due 케이스 (validFrom 과거)
            License dueLicense1 = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(OWNER_ID)
                    .productId(PRODUCT_ID)
                    .licenseType(LicenseType.TRIAL)
                    .validFrom(now.minus(10, ChronoUnit.MINUTES))
                    .validUntil(now.plus(14, ChronoUnit.DAYS))
                    .build();
            License dueLicense2 = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(OWNER_ID)
                    .productId(PRODUCT_ID)
                    .licenseType(LicenseType.TRIAL)
                    .validFrom(now.minus(1, ChronoUnit.MINUTES))
                    .validUntil(now.plus(28, ChronoUnit.DAYS))
                    .build();
            // not-yet due 케이스 (validFrom 미래) - 같은 트랜잭션에서 동시에 처리되더라도
            // tryActivateIfDue가 false 반환해야 함
            License notDueLicense = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(OWNER_ID)
                    .productId(PRODUCT_ID)
                    .licenseType(LicenseType.TRIAL)
                    .validFrom(now.plus(1, ChronoUnit.HOURS))
                    .validUntil(now.plus(14, ChronoUnit.DAYS))
                    .build();

            // Repository는 보통 due한 것만 반환하지만, race condition 등의 사유로
            // not-yet-due 라이선스가 섞여 들어오는 케이스를 검증.
            given(licenseRepository.findPendingDueForActivation(any()))
                    .willReturn(List.of(dueLicense1, dueLicense2, notDueLicense));

            int activated = licenseService.activatePendingLicenses();

            assertThat(activated).isEqualTo(2);
            assertThat(dueLicense1.getStatus()).isEqualTo(LicenseStatus.ACTIVE);
            assertThat(dueLicense2.getStatus()).isEqualTo(LicenseStatus.ACTIVE);
            assertThat(notDueLicense.getStatus()).isEqualTo(LicenseStatus.PENDING);
        }

        @Test
        @DisplayName("tryActivateIfDue가 false 반환하는 라이선스(이미 ACTIVE)는 카운트 제외")
        void shouldExcludeAlreadyActiveFromCount() {
            Instant now = Instant.now();
            License dueLicense = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(OWNER_ID)
                    .productId(PRODUCT_ID)
                    .licenseType(LicenseType.TRIAL)
                    .validFrom(now.minus(1, ChronoUnit.MINUTES))
                    .validUntil(now.plus(14, ChronoUnit.DAYS))
                    .build();
            // race condition 시뮬레이션: Repository가 가져왔으나 그 사이에 다른 트랜잭션이
            // 이 라이선스를 ACTIVE로 변경한 상황
            License alreadyActiveLicense = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(OWNER_ID)
                    .productId(PRODUCT_ID)
                    .licenseType(LicenseType.TRIAL)
                    .validFrom(now.minus(5, ChronoUnit.MINUTES))
                    .validUntil(now.plus(7, ChronoUnit.DAYS))
                    .build();
            alreadyActiveLicense.activate();

            given(licenseRepository.findPendingDueForActivation(any()))
                    .willReturn(List.of(dueLicense, alreadyActiveLicense));

            int activated = licenseService.activatePendingLicenses();

            // 진짜 PENDING이었던 1개만 카운트
            assertThat(activated).isEqualTo(1);
            assertThat(dueLicense.getStatus()).isEqualTo(LicenseStatus.ACTIVE);
            assertThat(alreadyActiveLicense.getStatus()).isEqualTo(LicenseStatus.ACTIVE);
        }
    }

    // ==========================================
    // 중복 구매 차단 (MDP: USER가 동일 product의 유상 라이선스 중복 구매 방지)
    // ==========================================

    @Nested
    @DisplayName("USER 중복 구매 차단")
    class DuplicatePurchaseBlock {

        private LicensePlan buildPaidPlan(UUID planId, UUID productId) {
            LicensePlan plan = LicensePlan.builder()
                    .productId(productId)
                    .code("PRO_1Y")
                    .name("Pro 1Y")
                    .licenseType(LicenseType.SUBSCRIPTION)
                    .durationDays(365)
                    .graceDays(7)
                    .maxActivations(3)
                    .maxConcurrentSessions(1)
                    .allowOfflineDays(7)
                    .build();
            ReflectionTestUtils.setField(plan, "id", planId);
            return plan;
        }

        @Test
        @DisplayName("requireUserCanPurchasePlan - 중복 없으면 통과")
        void shouldPassWhenNoActivePaidLicense() {
            UUID planId = UUID.randomUUID();
            LicensePlan plan = buildPaidPlan(planId, PRODUCT_ID);

            given(planRepository.findAvailableById(planId)).willReturn(Optional.of(plan));
            given(licenseRepository.existsActiveOrPendingPaidLicense(
                    OwnerType.USER, OWNER_ID, PRODUCT_ID)).willReturn(false);

            assertThatCode(() -> licenseService.requireUserCanPurchasePlan(OWNER_ID, planId))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("requireUserCanPurchasePlan - ACTIVE/PENDING 유상 라이선스 보유 시 LICENSE_ALREADY_EXISTS")
        void shouldThrowWhenActivePaidLicenseExists() {
            UUID planId = UUID.randomUUID();
            LicensePlan plan = buildPaidPlan(planId, PRODUCT_ID);

            given(planRepository.findAvailableById(planId)).willReturn(Optional.of(plan));
            given(licenseRepository.existsActiveOrPendingPaidLicense(
                    OwnerType.USER, OWNER_ID, PRODUCT_ID)).willReturn(true);

            assertThatThrownBy(() -> licenseService.requireUserCanPurchasePlan(OWNER_ID, planId))
                    .isInstanceOf(LicenseException.class)
                    .extracting(ex -> ((LicenseException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.LICENSE_ALREADY_EXISTS);
        }

        @Test
        @DisplayName("issueLicenseWithPlanForBilling - USER + 중복 보유 시 LICENSE_ALREADY_EXISTS")
        void shouldBlockUserDuplicatePaidIssuance() {
            UUID planId = UUID.randomUUID();
            LicensePlan plan = buildPaidPlan(planId, PRODUCT_ID);

            given(planRepository.findAvailableById(planId)).willReturn(Optional.of(plan));
            given(licenseRepository.existsActiveOrPendingPaidLicense(
                    OwnerType.USER, OWNER_ID, PRODUCT_ID)).willReturn(true);

            assertThatThrownBy(() -> licenseService.issueLicenseWithPlanForBilling(
                    OwnerType.USER, OWNER_ID, planId, ORDER_ID, UsageCategory.COMMERCIAL))
                    .isInstanceOf(LicenseException.class)
                    .extracting(ex -> ((LicenseException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.LICENSE_ALREADY_EXISTS);

            // 발급 자체가 차단되어야 하므로 save는 호출되지 않아야 함
            verify(licenseRepository, never()).save(any(License.class));
        }

        @Test
        @DisplayName("issueLicenseWithPlanForBilling - ORG는 중복 보유해도 발급 가능 (다중 라이선스 정책)")
        void shouldAllowOrgDuplicateIssuance() {
            UUID planId = UUID.randomUUID();
            LicensePlan plan = buildPaidPlan(planId, PRODUCT_ID);

            given(planRepository.findAvailableById(planId)).willReturn(Optional.of(plan));
            given(licenseRepository.save(any(License.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // when - ORG 발급 (existsActiveOrPendingPaidLicense은 호출되지 않아야 함)
            LicenseIssueResult result = licenseService.issueLicenseWithPlanForBilling(
                    OwnerType.ORG, OWNER_ID, planId, ORDER_ID, UsageCategory.COMMERCIAL);

            // then
            assertThat(result).isNotNull();
            verify(licenseRepository, never()).existsActiveOrPendingPaidLicense(any(), any(), any());
            verify(licenseRepository).save(any(License.class));
        }

        @Test
        @DisplayName("issueLicenseWithPlanForBilling - 중복 없으면 USER 발급 성공")
        void shouldAllowUserIssuanceWhenNoDuplicate() {
            UUID planId = UUID.randomUUID();
            LicensePlan plan = buildPaidPlan(planId, PRODUCT_ID);

            given(planRepository.findAvailableById(planId)).willReturn(Optional.of(plan));
            given(licenseRepository.existsActiveOrPendingPaidLicense(
                    OwnerType.USER, OWNER_ID, PRODUCT_ID)).willReturn(false);
            given(licenseRepository.save(any(License.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            LicenseIssueResult result = licenseService.issueLicenseWithPlanForBilling(
                    OwnerType.USER, OWNER_ID, planId, ORDER_ID, UsageCategory.COMMERCIAL);

            assertThat(result).isNotNull();
            assertThat(result.licenseKey()).isNotNull();
            verify(licenseRepository).save(any(License.class));
        }
    }

    // ==========================================
    // 헬퍼 메서드
    // ==========================================

    private LicensePlan createTrialPlan(UUID productId, int durationDays) {
        return LicensePlan.builder()
                .productId(productId)
                .code("TRIAL_" + durationDays + "D")
                .name(durationDays + "-day Trial")
                .licenseType(LicenseType.TRIAL)
                .durationDays(durationDays)
                .graceDays(0)
                .maxActivations(1)
                .maxConcurrentSessions(1)
                .allowOfflineDays(0)
                .build();
    }

    private License createExistingTrial(UUID userId, UUID productId, Instant validUntil) {
        License license = License.builder()
                .ownerType(OwnerType.USER)
                .ownerId(userId)
                .productId(productId)
                .licenseType(LicenseType.TRIAL)
                .validFrom(Instant.now())
                .validUntil(validUntil)
                .build();
        license.activate();
        ReflectionTestUtils.setField(license, "id", UUID.randomUUID());
        return license;
    }

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
