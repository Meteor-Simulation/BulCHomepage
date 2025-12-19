package com.bulc.homepage.licensing.integration;

import com.bulc.homepage.licensing.domain.*;
import com.bulc.homepage.licensing.dto.*;
import com.bulc.homepage.licensing.repository.ActivationRepository;
import com.bulc.homepage.licensing.repository.LicenseRepository;
import com.bulc.homepage.licensing.service.LicenseService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * 라이선스 시스템 통합 테스트.
 *
 * @SpringBootTest로 전체 컨텍스트를 로드하고 실제 DB와 함께 테스트.
 * 핵심 플로우(발급→검증→활성화→회수)를 엔드투엔드로 검증.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LicenseIntegrationTest {

    @Autowired
    private LicenseService licenseService;

    @Autowired
    private LicenseRepository licenseRepository;

    @Autowired
    private ActivationRepository activationRepository;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();

    // ==========================================
    // Billing → Licensing 발급 플로우
    // ==========================================

    @Nested
    @DisplayName("결제 완료 → 라이선스 발급 플로우")
    class IssueFlow {

        @Test
        @DisplayName("결제 완료 시 라이선스가 정상 발급되고 ACTIVE 상태가 됨")
        void shouldIssueLicenseWhenPaymentCompleted() {
            // given - Billing 모듈에서 결제 완료 후 호출하는 시나리오
            LicenseIssueRequest request = new LicenseIssueRequest(
                    OwnerType.USER,
                    USER_ID,
                    PRODUCT_ID,
                    null,
                    LicenseType.SUBSCRIPTION,
                    UsageCategory.COMMERCIAL,
                    Instant.now(),
                    Instant.now().plus(365, ChronoUnit.DAYS),
                    Map.of(
                            "maxActivations", 3,
                            "maxConcurrentSessions", 2,
                            "gracePeriodDays", 7,
                            "entitlements", List.of("core-simulation", "export-csv")
                    ),
                    ORDER_ID
            );

            // when - Billing 모듈에서 LicenseService.issueLicense() 호출
            LicenseResponse response = licenseService.issueLicense(request);

            // then - 라이선스가 정상 발급됨
            assertThat(response.id()).isNotNull();
            assertThat(response.status()).isEqualTo(LicenseStatus.ACTIVE);
            assertThat(response.ownerType()).isEqualTo(OwnerType.USER);
            assertThat(response.ownerId()).isEqualTo(USER_ID);
            assertThat(response.productId()).isEqualTo(PRODUCT_ID);

            // DB에서 조회해도 동일 (licenseKey는 DB에서만 확인 - API에 노출하지 않음)
            License savedLicense = licenseRepository.findBySourceOrderId(ORDER_ID).orElseThrow();
            assertThat(savedLicense.getStatus()).isEqualTo(LicenseStatus.ACTIVE);
            assertThat(savedLicense.getMaxActivations()).isEqualTo(3);
            assertThat(savedLicense.getLicenseKey()).isNotNull()
                    .matches("[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}");
        }

        @Test
        @DisplayName("Trial 라이선스 발급")
        void shouldIssueTrialLicense() {
            // given
            LicenseIssueRequest request = new LicenseIssueRequest(
                    OwnerType.USER,
                    USER_ID,
                    PRODUCT_ID,
                    null,
                    LicenseType.TRIAL,
                    UsageCategory.INTERNAL_EVAL,
                    Instant.now(),
                    Instant.now().plus(14, ChronoUnit.DAYS), // 14일 체험
                    null,
                    null // Trial은 주문 없이 발급 가능
            );

            // when
            LicenseResponse response = licenseService.issueLicense(request);

            // then
            assertThat(response.licenseType()).isEqualTo(LicenseType.TRIAL);
            assertThat(response.usageCategory()).isEqualTo(UsageCategory.INTERNAL_EVAL);
            assertThat(response.validUntil()).isBeforeOrEqualTo(Instant.now().plus(15, ChronoUnit.DAYS));
        }
    }

    // ==========================================
    // 클라이언트 검증/활성화 플로우
    // ==========================================

    @Nested
    @DisplayName("클라이언트 검증/활성화 플로우")
    class ValidationFlow {

        private String licenseKey;
        private UUID licenseId;

        @BeforeEach
        void setUp() {
            // 테스트용 라이선스 발급
            LicenseIssueRequest request = new LicenseIssueRequest(
                    OwnerType.USER,
                    USER_ID,
                    PRODUCT_ID,
                    null,
                    LicenseType.SUBSCRIPTION,
                    null,
                    null,
                    Instant.now().plus(30, ChronoUnit.DAYS),
                    Map.of("maxActivations", 2, "entitlements", List.of("core-simulation")),
                    ORDER_ID
            );
            LicenseResponse response = licenseService.issueLicense(request);
            licenseId = response.id();
            // licenseKey는 DB에서 직접 조회 (API에 노출하지 않음)
            licenseKey = licenseRepository.findById(licenseId).orElseThrow().getLicenseKey();
        }

        @Test
        @DisplayName("첫 번째 기기 활성화 성공")
        void shouldActivateFirstDevice() {
            // given
            ActivationRequest request = new ActivationRequest(
                    "device-fingerprint-001",
                    "1.0.0",
                    "Windows 11",
                    "192.168.1.100"
            );

            // when
            ValidationResponse response = licenseService.validateAndActivate(licenseKey, request);

            // then
            assertThat(response.valid()).isTrue();
            assertThat(response.status()).isEqualTo(LicenseStatus.ACTIVE);
            assertThat(response.entitlements()).contains("core-simulation");
            assertThat(response.offlineToken()).isNotNull();

            // DB 확인
            var activations = activationRepository.findByLicenseId(licenseId);
            assertThat(activations).hasSize(1);
            assertThat(activations.get(0).getDeviceFingerprint()).isEqualTo("device-fingerprint-001");
            assertThat(activations.get(0).getStatus()).isEqualTo(ActivationStatus.ACTIVE);
        }

        @Test
        @DisplayName("동일 기기 재검증 시 LastSeenAt 갱신")
        void shouldUpdateLastSeenAtOnRevalidation() throws InterruptedException {
            // given - 첫 번째 활성화
            ActivationRequest request = new ActivationRequest(
                    "device-001",
                    "1.0.0",
                    "Windows 11",
                    "192.168.1.100"
            );
            licenseService.validateAndActivate(licenseKey, request);

            Activation firstActivation = activationRepository
                    .findByLicenseIdAndDeviceFingerprint(licenseId, "device-001")
                    .orElseThrow();
            Instant firstLastSeen = firstActivation.getLastSeenAt();

            Thread.sleep(10); // 시간 차이를 만들기 위해

            // when - 동일 기기로 재검증 (Heartbeat와 동일)
            ActivationRequest secondRequest = new ActivationRequest(
                    "device-001",
                    "1.0.1", // 버전 업데이트
                    "Windows 11",
                    "192.168.1.101"
            );
            ValidationResponse response = licenseService.validateAndActivate(licenseKey, secondRequest);

            // then
            assertThat(response.valid()).isTrue();

            Activation updatedActivation = activationRepository
                    .findByLicenseIdAndDeviceFingerprint(licenseId, "device-001")
                    .orElseThrow();
            assertThat(updatedActivation.getLastSeenAt()).isAfter(firstLastSeen);
            assertThat(updatedActivation.getClientVersion()).isEqualTo("1.0.1");

            // 여전히 1개의 활성화만 존재
            assertThat(activationRepository.findByLicenseId(licenseId)).hasSize(1);
        }

        @Test
        @DisplayName("최대 기기 수 초과 시 활성화 실패")
        void shouldFailWhenMaxActivationsExceeded() {
            // given - maxActivations=2인 라이선스에 2개 기기 활성화
            licenseService.validateAndActivate(licenseKey,
                    new ActivationRequest("device-1", "1.0", "Windows", "10.0.0.1"));
            licenseService.validateAndActivate(licenseKey,
                    new ActivationRequest("device-2", "1.0", "macOS", "10.0.0.2"));

            // when - 3번째 기기 활성화 시도
            ValidationResponse response = licenseService.validateAndActivate(licenseKey,
                    new ActivationRequest("device-3", "1.0", "Linux", "10.0.0.3"));

            // then
            assertThat(response.valid()).isFalse();
            assertThat(response.errorCode()).isIn("ACTIVATION_LIMIT_EXCEEDED", "CONCURRENT_SESSION_LIMIT_EXCEEDED");
        }

        @Test
        @DisplayName("기기 비활성화 후 새 기기 활성화 가능")
        void shouldAllowNewDeviceAfterDeactivation() {
            // given - 2개 기기 활성화 (최대치)
            licenseService.validateAndActivate(licenseKey,
                    new ActivationRequest("device-1", "1.0", "Windows", "10.0.0.1"));
            licenseService.validateAndActivate(licenseKey,
                    new ActivationRequest("device-2", "1.0", "macOS", "10.0.0.2"));

            // when - device-1 비활성화
            licenseService.deactivate(licenseId, "device-1");

            // then - 새 기기 활성화 가능
            ValidationResponse response = licenseService.validateAndActivate(licenseKey,
                    new ActivationRequest("device-3", "1.0", "Linux", "10.0.0.3"));

            assertThat(response.valid()).isTrue();
        }
    }

    // ==========================================
    // Billing → Licensing 환불/회수 플로우
    // ==========================================

    @Nested
    @DisplayName("환불 → 라이선스 회수 플로우")
    class RevokeFlow {

        private UUID orderId;
        private UUID licenseId;
        private String licenseKey;

        @BeforeEach
        void setUp() {
            orderId = UUID.randomUUID();
            LicenseIssueRequest request = new LicenseIssueRequest(
                    OwnerType.USER,
                    USER_ID,
                    PRODUCT_ID,
                    null,
                    LicenseType.SUBSCRIPTION,
                    null, null,
                    Instant.now().plus(30, ChronoUnit.DAYS),
                    null,
                    orderId
            );
            LicenseResponse response = licenseService.issueLicense(request);
            licenseId = response.id();
            // licenseKey는 DB에서 직접 조회 (API에 노출하지 않음)
            licenseKey = licenseRepository.findById(licenseId).orElseThrow().getLicenseKey();

            // 기기 활성화
            licenseService.validateAndActivate(licenseKey,
                    new ActivationRequest("device-1", "1.0", "Windows", "10.0.0.1"));
        }

        @Test
        @DisplayName("환불 시 라이선스가 REVOKED 상태가 되고 기기도 비활성화됨")
        void shouldRevokeLicenseAndDeactivateDevicesOnRefund() {
            // when - Billing 모듈에서 환불 처리 후 호출
            LicenseResponse response = licenseService.revokeLicenseByOrderId(orderId, "환불 처리");

            // then - 라이선스 상태 확인
            assertThat(response.status()).isEqualTo(LicenseStatus.REVOKED);

            // DB에서 확인
            License revokedLicense = licenseRepository.findById(licenseId).orElseThrow();
            assertThat(revokedLicense.getStatus()).isEqualTo(LicenseStatus.REVOKED);

            // 모든 활성화도 비활성화됨
            var activations = activationRepository.findByLicenseId(licenseId);
            assertThat(activations).allMatch(a -> a.getStatus() == ActivationStatus.DEACTIVATED);
        }

        @Test
        @DisplayName("회수된 라이선스로 검증 시 실패")
        void shouldFailValidationForRevokedLicense() {
            // given - 라이선스 회수
            licenseService.revokeLicenseByOrderId(orderId, "환불");

            // when - 회수된 라이선스로 검증 시도
            ValidationResponse response = licenseService.validateAndActivate(licenseKey,
                    new ActivationRequest("device-2", "1.0", "macOS", "10.0.0.2"));

            // then
            assertThat(response.valid()).isFalse();
            assertThat(response.errorCode()).isEqualTo("LICENSE_REVOKED");
        }
    }

    // ==========================================
    // 라이선스 정지 플로우 (Admin)
    // ==========================================

    @Nested
    @DisplayName("관리자 라이선스 정지 플로우")
    class SuspendFlow {

        private UUID licenseId;
        private String licenseKey;

        @BeforeEach
        void setUp() {
            LicenseIssueRequest request = new LicenseIssueRequest(
                    OwnerType.USER, USER_ID, PRODUCT_ID, null,
                    LicenseType.SUBSCRIPTION, null, null,
                    Instant.now().plus(30, ChronoUnit.DAYS), null, ORDER_ID
            );
            LicenseResponse response = licenseService.issueLicense(request);
            licenseId = response.id();
            // licenseKey는 DB에서 직접 조회 (API에 노출하지 않음)
            licenseKey = licenseRepository.findById(licenseId).orElseThrow().getLicenseKey();
        }

        @Test
        @DisplayName("관리자가 라이선스를 정지하면 SUSPENDED 상태가 됨")
        void shouldSuspendLicenseByAdmin() {
            // when - Admin 모듈에서 호출
            LicenseResponse response = licenseService.suspendLicense(licenseId, "약관 위반");

            // then
            assertThat(response.status()).isEqualTo(LicenseStatus.SUSPENDED);
        }

        @Test
        @DisplayName("정지된 라이선스로 검증 시 실패")
        void shouldFailValidationForSuspendedLicense() {
            // given
            licenseService.suspendLicense(licenseId, "테스트 정지");

            // when
            ValidationResponse response = licenseService.validateAndActivate(licenseKey,
                    new ActivationRequest("device-1", "1.0", "Windows", "10.0.0.1"));

            // then
            assertThat(response.valid()).isFalse();
            assertThat(response.errorCode()).isEqualTo("LICENSE_SUSPENDED");
        }
    }

    // ==========================================
    // 구독 갱신 플로우 (Billing)
    // ==========================================

    @Nested
    @DisplayName("구독 갱신 플로우")
    class RenewFlow {

        private UUID licenseId;

        @BeforeEach
        void setUp() {
            LicenseIssueRequest request = new LicenseIssueRequest(
                    OwnerType.USER, USER_ID, PRODUCT_ID, null,
                    LicenseType.SUBSCRIPTION, null, null,
                    Instant.now().plus(30, ChronoUnit.DAYS), null, ORDER_ID
            );
            LicenseResponse response = licenseService.issueLicense(request);
            licenseId = response.id();
        }

        @Test
        @DisplayName("구독 갱신 시 validUntil이 연장됨")
        void shouldExtendValidUntilOnRenewal() {
            // given
            Instant newValidUntil = Instant.now().plus(395, ChronoUnit.DAYS); // 약 13개월

            // when - Billing 모듈에서 구독 갱신 결제 완료 후 호출
            LicenseResponse response = licenseService.renewLicense(licenseId, newValidUntil);

            // then
            assertThat(response.validUntil()).isEqualTo(newValidUntil);
            assertThat(response.status()).isEqualTo(LicenseStatus.ACTIVE);

            // DB 확인
            License renewed = licenseRepository.findById(licenseId).orElseThrow();
            assertThat(renewed.getValidUntil()).isEqualTo(newValidUntil);
        }
    }

    // ==========================================
    // 동시성 테스트 (Race Condition)
    // ==========================================

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTest {

        @Test
        @DisplayName("동시에 maxConcurrentSessions 초과 요청 시 일부만 성공")
        void shouldNotExceedMaxConcurrentSessionsUnderRace() throws InterruptedException {
            // given - maxConcurrentSessions=2인 라이선스
            LicenseIssueRequest issueRequest = new LicenseIssueRequest(
                    OwnerType.USER,
                    USER_ID,
                    PRODUCT_ID,
                    null,
                    LicenseType.SUBSCRIPTION,
                    null, null,
                    Instant.now().plus(30, ChronoUnit.DAYS),
                    Map.of(
                            "maxActivations", 10,
                            "maxConcurrentSessions", 2,
                            "entitlements", List.of("core")
                    ),
                    ORDER_ID
            );
            LicenseResponse issuedLicense = licenseService.issueLicense(issueRequest);
            // licenseKey는 DB에서 직접 조회 (API에 노출하지 않음)
            String licenseKey = licenseRepository.findById(issuedLicense.id()).orElseThrow().getLicenseKey();

            // when - 동시에 3개의 다른 기기에서 활성화 시도
            java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(3);
            java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
            java.util.concurrent.atomic.AtomicInteger failCount = new java.util.concurrent.atomic.AtomicInteger(0);

            for (int i = 0; i < 3; i++) {
                final int deviceNum = i;
                new Thread(() -> {
                    try {
                        startLatch.await(); // 모든 스레드가 동시에 시작
                        ValidationResponse response = licenseService.validateAndActivate(
                                licenseKey,
                                new ActivationRequest(
                                        "concurrent-device-" + deviceNum,
                                        "1.0.0",
                                        "Windows",
                                        "10.0.0." + deviceNum
                                )
                        );
                        if (response.valid()) {
                            successCount.incrementAndGet();
                        } else {
                            failCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                }).start();
            }

            // 모든 스레드 동시 시작
            startLatch.countDown();
            // 모든 스레드 완료 대기
            doneLatch.await(10, java.util.concurrent.TimeUnit.SECONDS);

            // then - maxConcurrentSessions=2이므로 최대 2개만 성공 가능
            assertThat(successCount.get()).isLessThanOrEqualTo(2);
            assertThat(successCount.get() + failCount.get()).isEqualTo(3);

            // DB 확인 - 실제 활성화된 기기 수
            var activations = activationRepository.findByLicenseId(issuedLicense.id());
            long activeCount = activations.stream()
                    .filter(a -> a.getStatus() == ActivationStatus.ACTIVE)
                    .count();
            assertThat(activeCount).isLessThanOrEqualTo(2);
        }
    }
}
