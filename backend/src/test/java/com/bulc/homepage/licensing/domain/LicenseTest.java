package com.bulc.homepage.licensing.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * License 도메인 유닛 테스트.
 *
 * DB/Spring 의존 없이 순수 자바 객체만으로 테스트.
 * 상태 전이, 만료, 기기 활성화 제한 등 핵심 도메인 로직 검증.
 */
class LicenseTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();

    // ==========================================
    // 라이선스 생성 테스트
    // ==========================================

    @Nested
    @DisplayName("라이선스 생성")
    class CreateLicense {

        @Test
        @DisplayName("정상 생성 시 PENDING 상태로 시작")
        void shouldCreateWithPendingStatus() {
            License license = createDefaultLicense();

            assertThat(license.getStatus()).isEqualTo(LicenseStatus.PENDING);
            assertThat(license.getOwnerType()).isEqualTo(OwnerType.USER);
            assertThat(license.getOwnerId()).isEqualTo(OWNER_ID);
            assertThat(license.getProductId()).isEqualTo(PRODUCT_ID);
            assertThat(license.getSourceOrderId()).isEqualTo(ORDER_ID);
        }

        @Test
        @DisplayName("validFrom 미지정 시 현재 시각으로 설정")
        void shouldSetValidFromToNowWhenNotProvided() {
            Instant before = Instant.now();
            License license = createDefaultLicense();
            Instant after = Instant.now();

            assertThat(license.getValidFrom())
                    .isAfterOrEqualTo(before)
                    .isBeforeOrEqualTo(after);
        }

        @Test
        @DisplayName("usageCategory 미지정 시 COMMERCIAL로 기본 설정")
        void shouldDefaultToCommercialUsageCategory() {
            License license = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(OWNER_ID)
                    .productId(PRODUCT_ID)
                    .licenseType(LicenseType.SUBSCRIPTION)
                    .build();

            assertThat(license.getUsageCategory()).isEqualTo(UsageCategory.COMMERCIAL);
        }

        @Test
        @DisplayName("Perpetual 라이선스는 validUntil이 null")
        void perpetualLicenseShouldHaveNullValidUntil() {
            License license = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(OWNER_ID)
                    .productId(PRODUCT_ID)
                    .licenseType(LicenseType.PERPETUAL)
                    .build();

            assertThat(license.getValidUntil()).isNull();
        }
    }

    // ==========================================
    // 라이선스 활성화 (PENDING → ACTIVE)
    // ==========================================

    @Nested
    @DisplayName("라이선스 활성화 (activate)")
    class ActivateLicense {

        @Test
        @DisplayName("PENDING 상태에서 활성화 가능")
        void shouldActivateFromPendingStatus() {
            License license = createDefaultLicense();

            license.activate();

            assertThat(license.getStatus()).isEqualTo(LicenseStatus.ACTIVE);
        }

        @Test
        @DisplayName("ACTIVE 상태에서 활성화 시도 시 예외 발생")
        void shouldThrowWhenActivatingAlreadyActiveLicense() {
            License license = createDefaultLicense();
            license.activate();

            assertThatThrownBy(license::activate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PENDING 상태에서만");
        }

        @Test
        @DisplayName("SUSPENDED 상태에서 활성화 시도 시 예외 발생")
        void shouldThrowWhenActivatingSuspendedLicense() {
            License license = createActiveLicense();
            license.suspend("test");

            assertThatThrownBy(license::activate)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ==========================================
    // 라이선스 정지 (suspend)
    // ==========================================

    @Nested
    @DisplayName("라이선스 정지 (suspend)")
    class SuspendLicense {

        @Test
        @DisplayName("ACTIVE 상태에서 정지 가능")
        void shouldSuspendFromActiveStatus() {
            License license = createActiveLicense();

            license.suspend("테스트 정지");

            assertThat(license.getStatus()).isEqualTo(LicenseStatus.SUSPENDED);
        }

        @Test
        @DisplayName("PENDING 상태에서 정지 가능")
        void shouldSuspendFromPendingStatus() {
            License license = createDefaultLicense();

            license.suspend("발급 전 정지");

            assertThat(license.getStatus()).isEqualTo(LicenseStatus.SUSPENDED);
        }

        @Test
        @DisplayName("REVOKED 상태에서 정지 시도 시 예외 발생")
        void shouldThrowWhenSuspendingRevokedLicense() {
            License license = createActiveLicense();
            license.revoke("환불");

            assertThatThrownBy(() -> license.suspend("test"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("회수된 라이선스");
        }
    }

    // ==========================================
    // 라이선스 회수 (revoke)
    // ==========================================

    @Nested
    @DisplayName("라이선스 회수 (revoke)")
    class RevokeLicense {

        @Test
        @DisplayName("ACTIVE 상태에서 회수 가능")
        void shouldRevokeFromActiveStatus() {
            License license = createActiveLicense();

            license.revoke("환불 처리");

            assertThat(license.getStatus()).isEqualTo(LicenseStatus.REVOKED);
        }

        @Test
        @DisplayName("SUSPENDED 상태에서 회수 가능")
        void shouldRevokeFromSuspendedStatus() {
            License license = createActiveLicense();
            license.suspend("test");

            license.revoke("환불");

            assertThat(license.getStatus()).isEqualTo(LicenseStatus.REVOKED);
        }

        @Test
        @DisplayName("회수 시 모든 활성화도 비활성화됨")
        void shouldDeactivateAllActivationsWhenRevoked() {
            License license = createActiveLicense();
            license.addActivation("device-1", "1.0", "Windows", "10.0.0.1");
            license.addActivation("device-2", "1.0", "macOS", "10.0.0.2");

            license.revoke("환불");

            assertThat(license.getActivations())
                    .allMatch(a -> a.getStatus() == ActivationStatus.DEACTIVATED);
        }
    }

    // ==========================================
    // 구독 갱신 (renew)
    // ==========================================

    @Nested
    @DisplayName("구독 갱신 (renew)")
    class RenewLicense {

        @Test
        @DisplayName("SUBSCRIPTION 라이선스 갱신 가능")
        void shouldRenewSubscriptionLicense() {
            License license = createActiveLicense();
            Instant newValidUntil = Instant.now().plus(365, ChronoUnit.DAYS);

            license.renew(newValidUntil);

            assertThat(license.getValidUntil()).isEqualTo(newValidUntil);
            assertThat(license.getStatus()).isEqualTo(LicenseStatus.ACTIVE);
        }

        @Test
        @DisplayName("PERPETUAL 라이선스 갱신 시도 시 예외 발생")
        void shouldThrowWhenRenewingPerpetualLicense() {
            License license = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(OWNER_ID)
                    .productId(PRODUCT_ID)
                    .licenseType(LicenseType.PERPETUAL)
                    .build();
            license.activate();

            assertThatThrownBy(() -> license.renew(Instant.now().plus(365, ChronoUnit.DAYS)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("구독형 라이선스만");
        }
    }

    // ==========================================
    // 유효 상태 계산 (만료/Grace Period)
    // ==========================================

    @Nested
    @DisplayName("유효 상태 계산 (calculateEffectiveStatus)")
    class CalculateEffectiveStatus {

        @Test
        @DisplayName("validUntil 이전이면 ACTIVE")
        void shouldReturnActiveBeforeValidUntil() {
            License license = createActiveLicense();
            Instant now = Instant.now();

            LicenseStatus status = license.calculateEffectiveStatus(now);

            assertThat(status).isEqualTo(LicenseStatus.ACTIVE);
        }

        @Test
        @DisplayName("validUntil 이후 ~ Grace Period 내면 EXPIRED_GRACE")
        void shouldReturnExpiredGraceDuringGracePeriod() {
            Instant validUntil = Instant.now().minus(3, ChronoUnit.DAYS);
            License license = createLicenseWithValidUntil(validUntil, 7);

            LicenseStatus status = license.calculateEffectiveStatus(Instant.now());

            assertThat(status).isEqualTo(LicenseStatus.EXPIRED_GRACE);
        }

        @Test
        @DisplayName("Grace Period 이후면 EXPIRED_HARD")
        void shouldReturnExpiredHardAfterGracePeriod() {
            Instant validUntil = Instant.now().minus(10, ChronoUnit.DAYS);
            License license = createLicenseWithValidUntil(validUntil, 7);

            LicenseStatus status = license.calculateEffectiveStatus(Instant.now());

            assertThat(status).isEqualTo(LicenseStatus.EXPIRED_HARD);
        }

        @Test
        @DisplayName("PERPETUAL 라이선스는 항상 저장된 상태 반환")
        void perpetualLicenseShouldReturnStoredStatus() {
            License license = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(OWNER_ID)
                    .productId(PRODUCT_ID)
                    .licenseType(LicenseType.PERPETUAL)
                    .build();
            license.activate();

            LicenseStatus status = license.calculateEffectiveStatus(
                    Instant.now().plus(1000, ChronoUnit.DAYS));

            assertThat(status).isEqualTo(LicenseStatus.ACTIVE);
        }

        @Test
        @DisplayName("SUSPENDED 상태면 기간과 무관하게 SUSPENDED 반환")
        void suspendedLicenseShouldReturnSuspended() {
            License license = createActiveLicense();
            license.suspend("test");

            LicenseStatus status = license.calculateEffectiveStatus(Instant.now());

            assertThat(status).isEqualTo(LicenseStatus.SUSPENDED);
        }

        @Test
        @DisplayName("REVOKED 상태면 기간과 무관하게 REVOKED 반환")
        void revokedLicenseShouldReturnRevoked() {
            License license = createActiveLicense();
            license.revoke("test");

            LicenseStatus status = license.calculateEffectiveStatus(Instant.now());

            assertThat(status).isEqualTo(LicenseStatus.REVOKED);
        }
    }

    // ==========================================
    // 기기 활성화 (addActivation, canActivate)
    // ==========================================

    @Nested
    @DisplayName("기기 활성화")
    class DeviceActivation {

        @Test
        @DisplayName("ACTIVE 상태에서 기기 활성화 가능")
        void shouldActivateDeviceWhenLicenseIsActive() {
            License license = createActiveLicense();

            boolean canActivate = license.canActivate("device-1", Instant.now());

            assertThat(canActivate).isTrue();
        }

        @Test
        @DisplayName("EXPIRED_GRACE 상태에서도 기기 활성화 가능")
        void shouldActivateDeviceWhenInGracePeriod() {
            Instant validUntil = Instant.now().minus(3, ChronoUnit.DAYS);
            License license = createLicenseWithValidUntil(validUntil, 7);

            boolean canActivate = license.canActivate("device-1", Instant.now());

            assertThat(canActivate).isTrue();
        }

        @Test
        @DisplayName("EXPIRED_HARD 상태에서는 기기 활성화 불가")
        void shouldNotActivateDeviceWhenExpiredHard() {
            Instant validUntil = Instant.now().minus(10, ChronoUnit.DAYS);
            License license = createLicenseWithValidUntil(validUntil, 7);

            boolean canActivate = license.canActivate("device-1", Instant.now());

            assertThat(canActivate).isFalse();
        }

        @Test
        @DisplayName("SUSPENDED 상태에서는 기기 활성화 불가")
        void shouldNotActivateDeviceWhenSuspended() {
            License license = createActiveLicense();
            license.suspend("test");

            boolean canActivate = license.canActivate("device-1", Instant.now());

            assertThat(canActivate).isFalse();
        }

        @Test
        @DisplayName("maxActivations 초과 시 활성화 불가")
        void shouldNotActivateWhenMaxActivationsReached() {
            License license = createLicenseWithMaxActivations(2);
            license.addActivation("device-1", "1.0", "Windows", "10.0.0.1");
            license.addActivation("device-2", "1.0", "macOS", "10.0.0.2");

            boolean canActivate = license.canActivate("device-3", Instant.now());

            assertThat(canActivate).isFalse();
        }

        @Test
        @DisplayName("이미 활성화된 기기는 재활성화 가능 (maxActivations와 무관)")
        void shouldAllowReactivationOfExistingDevice() {
            License license = createLicenseWithMaxActivations(2);
            license.addActivation("device-1", "1.0", "Windows", "10.0.0.1");
            license.addActivation("device-2", "1.0", "macOS", "10.0.0.2");

            // device-1은 이미 활성화되어 있으므로 true
            boolean canActivate = license.canActivate("device-1", Instant.now());

            assertThat(canActivate).isTrue();
        }

        @Test
        @DisplayName("기기 활성화 추가 시 Activation 생성")
        void shouldCreateActivationWhenAddingDevice() {
            License license = createActiveLicense();

            Activation activation = license.addActivation(
                    "device-fingerprint-123",
                    "1.0.0",
                    "Windows 11",
                    "192.168.1.1"
            );

            assertThat(activation).isNotNull();
            assertThat(activation.getDeviceFingerprint()).isEqualTo("device-fingerprint-123");
            assertThat(activation.getStatus()).isEqualTo(ActivationStatus.ACTIVE);
            assertThat(license.getActivations()).hasSize(1);
        }

        @Test
        @DisplayName("동일 기기 재활성화 시 기존 Activation 갱신")
        void shouldUpdateExistingActivationWhenSameDevice() {
            License license = createActiveLicense();
            license.addActivation("device-1", "1.0", "Windows 10", "10.0.0.1");

            Activation reactivated = license.addActivation("device-1", "2.0", "Windows 11", "10.0.0.2");

            assertThat(license.getActivations()).hasSize(1);
            assertThat(reactivated.getClientVersion()).isEqualTo("2.0");
            assertThat(reactivated.getClientOs()).isEqualTo("Windows 11");
        }
    }

    // ==========================================
    // PolicySnapshot 추출
    // ==========================================

    @Nested
    @DisplayName("PolicySnapshot 추출")
    class PolicySnapshotExtraction {

        @Test
        @DisplayName("gracePeriodDays 기본값은 7일")
        void shouldReturnDefaultGracePeriodDays() {
            License license = createDefaultLicense();

            assertThat(license.getGracePeriodDays()).isEqualTo(7);
        }

        @Test
        @DisplayName("maxActivations 기본값은 3개")
        void shouldReturnDefaultMaxActivations() {
            License license = createDefaultLicense();

            assertThat(license.getMaxActivations()).isEqualTo(3);
        }

        @Test
        @DisplayName("maxConcurrentSessions 기본값은 2개")
        void shouldReturnDefaultMaxConcurrentSessions() {
            License license = createDefaultLicense();

            assertThat(license.getMaxConcurrentSessions()).isEqualTo(2);
        }

        @Test
        @DisplayName("policySnapshot에서 커스텀 값 추출")
        void shouldExtractCustomValuesFromPolicySnapshot() {
            Map<String, Object> policy = Map.of(
                    "maxActivations", 5,
                    "maxConcurrentSessions", 3,
                    "gracePeriodDays", 14
            );

            License license = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(OWNER_ID)
                    .productId(PRODUCT_ID)
                    .licenseType(LicenseType.SUBSCRIPTION)
                    .policySnapshot(policy)
                    .build();

            assertThat(license.getMaxActivations()).isEqualTo(5);
            assertThat(license.getMaxConcurrentSessions()).isEqualTo(3);
            assertThat(license.getGracePeriodDays()).isEqualTo(14);
        }
    }

    // ==========================================
    // 헬퍼 메서드
    // ==========================================

    private License createDefaultLicense() {
        return License.builder()
                .ownerType(OwnerType.USER)
                .ownerId(OWNER_ID)
                .productId(PRODUCT_ID)
                .licenseType(LicenseType.SUBSCRIPTION)
                .validUntil(Instant.now().plus(30, ChronoUnit.DAYS))
                .sourceOrderId(ORDER_ID)
                .build();
    }

    private License createActiveLicense() {
        License license = createDefaultLicense();
        license.activate();
        return license;
    }

    private License createLicenseWithValidUntil(Instant validUntil, int gracePeriodDays) {
        Map<String, Object> policy = Map.of("gracePeriodDays", gracePeriodDays);

        License license = License.builder()
                .ownerType(OwnerType.USER)
                .ownerId(OWNER_ID)
                .productId(PRODUCT_ID)
                .licenseType(LicenseType.SUBSCRIPTION)
                .validUntil(validUntil)
                .policySnapshot(policy)
                .build();
        license.activate();
        return license;
    }

    private License createLicenseWithMaxActivations(int maxActivations) {
        Map<String, Object> policy = Map.of("maxActivations", maxActivations);

        License license = License.builder()
                .ownerType(OwnerType.USER)
                .ownerId(OWNER_ID)
                .productId(PRODUCT_ID)
                .licenseType(LicenseType.SUBSCRIPTION)
                .validUntil(Instant.now().plus(30, ChronoUnit.DAYS))
                .policySnapshot(policy)
                .build();
        license.activate();
        return license;
    }
}
