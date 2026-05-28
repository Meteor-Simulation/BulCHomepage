package com.bulc.homepage.licensing.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * License 도메인 유닛 테스트.
 *
 * TRIAL chaining(B안 지연 시작)을 위한 effective status / tryActivateIfDue 동작 검증.
 */
class LicenseTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Nested
    @DisplayName("calculateEffectiveStatus - PENDING 처리")
    class EffectiveStatusForPending {

        @Test
        @DisplayName("PENDING + validFrom 미래: PENDING 유지")
        void shouldReturnPendingWhenStartTimeNotYetReached() {
            Instant now = Instant.now();
            License license = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(OWNER_ID)
                    .productId(PRODUCT_ID)
                    .licenseType(LicenseType.TRIAL)
                    .validFrom(now.plus(7, ChronoUnit.DAYS))
                    .validUntil(now.plus(21, ChronoUnit.DAYS))
                    .build();
            // status는 생성자에서 PENDING으로 시작, activate() 호출하지 않음

            assertThat(license.calculateEffectiveStatus(now))
                    .isEqualTo(LicenseStatus.PENDING);
        }

        @Test
        @DisplayName("PENDING + validFrom 도달 + validUntil 미래: ACTIVE로 간주 (lazy 활성화)")
        void shouldReturnActiveWhenStartTimeReachedAndNotExpired() {
            Instant now = Instant.now();
            License license = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(OWNER_ID)
                    .productId(PRODUCT_ID)
                    .licenseType(LicenseType.TRIAL)
                    .validFrom(now.minus(1, ChronoUnit.HOURS))
                    .validUntil(now.plus(14, ChronoUnit.DAYS))
                    .build();

            assertThat(license.calculateEffectiveStatus(now))
                    .isEqualTo(LicenseStatus.ACTIVE);
        }

        @Test
        @DisplayName("PENDING + validFrom 도달 + validUntil 만료 + grace 내: EXPIRED_GRACE")
        void shouldReturnExpiredGraceForPendingPastValidUntilWithinGrace() {
            Instant now = Instant.now();
            Map<String, Object> policy = Map.of("gracePeriodDays", 7);
            License license = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(OWNER_ID)
                    .productId(PRODUCT_ID)
                    .licenseType(LicenseType.TRIAL)
                    .validFrom(now.minus(20, ChronoUnit.DAYS))
                    .validUntil(now.minus(3, ChronoUnit.DAYS))
                    .policySnapshot(policy)
                    .build();

            assertThat(license.calculateEffectiveStatus(now))
                    .isEqualTo(LicenseStatus.EXPIRED_GRACE);
        }

        @Test
        @DisplayName("PENDING이지만 SUSPENDED 호출 시 SUSPENDED 우선")
        void shouldReturnSuspendedEvenIfPending() {
            Instant now = Instant.now();
            License license = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(OWNER_ID)
                    .productId(PRODUCT_ID)
                    .licenseType(LicenseType.TRIAL)
                    .validFrom(now.plus(7, ChronoUnit.DAYS))
                    .validUntil(now.plus(21, ChronoUnit.DAYS))
                    .build();
            license.activate();
            license.suspend("test");

            assertThat(license.calculateEffectiveStatus(now))
                    .isEqualTo(LicenseStatus.SUSPENDED);
        }

        @Test
        @DisplayName("PERPETUAL(validUntil=null) + PENDING + validFrom 미래: PENDING 유지")
        void shouldReturnPendingForPerpetualWhenStartTimeNotReached() {
            Instant now = Instant.now();
            License license = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(OWNER_ID)
                    .productId(PRODUCT_ID)
                    .licenseType(LicenseType.PERPETUAL)
                    .validFrom(now.plus(3, ChronoUnit.DAYS))
                    .validUntil(null)
                    .build();

            assertThat(license.calculateEffectiveStatus(now))
                    .isEqualTo(LicenseStatus.PENDING);
        }

        @Test
        @DisplayName("PERPETUAL(validUntil=null) + PENDING + validFrom 도달: ACTIVE (lazy)")
        void shouldReturnActiveForPerpetualWhenStartTimeReached() {
            Instant now = Instant.now();
            License license = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(OWNER_ID)
                    .productId(PRODUCT_ID)
                    .licenseType(LicenseType.PERPETUAL)
                    .validFrom(now.minus(1, ChronoUnit.HOURS))
                    .validUntil(null)
                    .build();

            assertThat(license.calculateEffectiveStatus(now))
                    .isEqualTo(LicenseStatus.ACTIVE);
        }

        @Test
        @DisplayName("PENDING + validFrom == now 경계값: ACTIVE 처리 (isBefore는 strict)")
        void shouldReturnActiveWhenStartTimeEqualsNow() {
            Instant now = Instant.now();
            License license = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(OWNER_ID)
                    .productId(PRODUCT_ID)
                    .licenseType(LicenseType.TRIAL)
                    .validFrom(now)
                    .validUntil(now.plus(14, ChronoUnit.DAYS))
                    .build();

            // status가 PENDING이지만 validFrom == now (now.isBefore(validFrom) == false)
            // ⇒ Pending 조건을 빠져나와 ACTIVE로 평가
            assertThat(license.calculateEffectiveStatus(now))
                    .isEqualTo(LicenseStatus.ACTIVE);
        }

        @Test
        @DisplayName("PENDING + validFrom/validUntil 모두 과거 + grace 만료: EXPIRED_HARD")
        void shouldReturnExpiredHardForPendingFullyExpired() {
            Instant now = Instant.now();
            Map<String, Object> policy = Map.of("gracePeriodDays", 7);
            License license = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(OWNER_ID)
                    .productId(PRODUCT_ID)
                    .licenseType(LicenseType.TRIAL)
                    .validFrom(now.minus(60, ChronoUnit.DAYS))
                    .validUntil(now.minus(30, ChronoUnit.DAYS))
                    .policySnapshot(policy)
                    .build();

            assertThat(license.calculateEffectiveStatus(now))
                    .isEqualTo(LicenseStatus.EXPIRED_HARD);
        }

        @Test
        @DisplayName("PENDING + REVOKED 호출: REVOKED 우선 반환")
        void shouldReturnRevokedEvenIfPendingShape() {
            Instant now = Instant.now();
            License license = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(OWNER_ID)
                    .productId(PRODUCT_ID)
                    .licenseType(LicenseType.TRIAL)
                    .validFrom(now.plus(7, ChronoUnit.DAYS))
                    .validUntil(now.plus(21, ChronoUnit.DAYS))
                    .build();
            // PENDING 모양 + revoke (revoke는 어느 상태에서도 호출 가능)
            license.revoke("test");

            assertThat(license.calculateEffectiveStatus(now))
                    .isEqualTo(LicenseStatus.REVOKED);
        }
    }

    @Nested
    @DisplayName("tryActivateIfDue - 도래한 PENDING의 실제 전이")
    class TryActivateIfDue {

        @Test
        @DisplayName("PENDING + validFrom 도달: ACTIVE로 전이하고 true 반환")
        void shouldActivateWhenDue() {
            Instant now = Instant.now();
            License license = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(OWNER_ID)
                    .productId(PRODUCT_ID)
                    .licenseType(LicenseType.TRIAL)
                    .validFrom(now.minus(1, ChronoUnit.HOURS))
                    .validUntil(now.plus(14, ChronoUnit.DAYS))
                    .build();

            boolean activated = license.tryActivateIfDue(now);

            assertThat(activated).isTrue();
            assertThat(license.getStatus()).isEqualTo(LicenseStatus.ACTIVE);
        }

        @Test
        @DisplayName("PENDING + validFrom 미래: 전이 없이 false 반환")
        void shouldNotActivateWhenNotDue() {
            Instant now = Instant.now();
            License license = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(OWNER_ID)
                    .productId(PRODUCT_ID)
                    .licenseType(LicenseType.TRIAL)
                    .validFrom(now.plus(7, ChronoUnit.DAYS))
                    .validUntil(now.plus(21, ChronoUnit.DAYS))
                    .build();

            boolean activated = license.tryActivateIfDue(now);

            assertThat(activated).isFalse();
            assertThat(license.getStatus()).isEqualTo(LicenseStatus.PENDING);
        }

        @Test
        @DisplayName("이미 ACTIVE 상태: 멱등 - false 반환")
        void shouldBeNoOpWhenAlreadyActive() {
            Instant now = Instant.now();
            License license = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(OWNER_ID)
                    .productId(PRODUCT_ID)
                    .licenseType(LicenseType.TRIAL)
                    .validFrom(now.minus(1, ChronoUnit.HOURS))
                    .validUntil(now.plus(14, ChronoUnit.DAYS))
                    .build();
            license.activate();

            boolean activated = license.tryActivateIfDue(now);

            assertThat(activated).isFalse();
            assertThat(license.getStatus()).isEqualTo(LicenseStatus.ACTIVE);
        }

        @Test
        @DisplayName("REVOKED 상태: false 반환, 상태 변경 없음")
        void shouldNotActivateRevokedLicense() {
            Instant now = Instant.now();
            License license = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(OWNER_ID)
                    .productId(PRODUCT_ID)
                    .licenseType(LicenseType.TRIAL)
                    .validFrom(now.minus(1, ChronoUnit.HOURS))
                    .validUntil(now.plus(14, ChronoUnit.DAYS))
                    .build();
            license.revoke("test");

            boolean activated = license.tryActivateIfDue(now);

            assertThat(activated).isFalse();
            assertThat(license.getStatus()).isEqualTo(LicenseStatus.REVOKED);
        }

        @Test
        @DisplayName("SUSPENDED 상태: false 반환, 상태 변경 없음")
        void shouldNotActivateSuspendedLicense() {
            Instant now = Instant.now();
            License license = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(OWNER_ID)
                    .productId(PRODUCT_ID)
                    .licenseType(LicenseType.TRIAL)
                    .validFrom(now.minus(1, ChronoUnit.HOURS))
                    .validUntil(now.plus(14, ChronoUnit.DAYS))
                    .build();
            license.activate();
            license.suspend("test");

            boolean activated = license.tryActivateIfDue(now);

            assertThat(activated).isFalse();
            assertThat(license.getStatus()).isEqualTo(LicenseStatus.SUSPENDED);
        }

        @Test
        @DisplayName("PENDING + validFrom == now 경계값: true 반환, ACTIVE로 전이")
        void shouldActivateAtBoundary() {
            Instant now = Instant.now();
            License license = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(OWNER_ID)
                    .productId(PRODUCT_ID)
                    .licenseType(LicenseType.TRIAL)
                    .validFrom(now)
                    .validUntil(now.plus(14, ChronoUnit.DAYS))
                    .build();

            // now.isBefore(validFrom == now) == false → 활성화 진행
            boolean activated = license.tryActivateIfDue(now);

            assertThat(activated).isTrue();
            assertThat(license.getStatus()).isEqualTo(LicenseStatus.ACTIVE);
        }

        @Test
        @DisplayName("PENDING + validFrom == null (legacy): true 반환, 즉시 활성화")
        void shouldActivateLegacyPendingWithNullValidFrom() {
            Instant now = Instant.now();
            License license = License.builder()
                    .ownerType(OwnerType.USER)
                    .ownerId(OWNER_ID)
                    .productId(PRODUCT_ID)
                    .licenseType(LicenseType.TRIAL)
                    .validFrom(now)
                    .validUntil(now.plus(14, ChronoUnit.DAYS))
                    .build();
            // 빌더에서 validFrom이 null이면 Instant.now()로 기본값 세팅되므로
            // 강제로 null 주입하여 legacy 상황 재현
            ReflectionTestUtils.setField(license, "validFrom", null);

            boolean activated = license.tryActivateIfDue(now);

            assertThat(activated).isTrue();
            assertThat(license.getStatus()).isEqualTo(LicenseStatus.ACTIVE);
        }
    }
}
