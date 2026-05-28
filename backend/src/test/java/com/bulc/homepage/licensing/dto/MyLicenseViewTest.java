package com.bulc.homepage.licensing.dto;

import com.bulc.homepage.licensing.domain.License;
import com.bulc.homepage.licensing.domain.LicenseStatus;
import com.bulc.homepage.licensing.domain.LicenseType;
import com.bulc.homepage.licensing.domain.OwnerType;
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
 * MyLicenseView / LicenseResponse 의 startsAt 노출 정책 테스트.
 *
 * 정책: effective status == PENDING 일 때만 startsAt = validFrom 노출,
 *       그 외 (ACTIVE / EXPIRED_GRACE / REVOKED / SUSPENDED 등) → null
 */
class MyLicenseViewTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Nested
    @DisplayName("MyLicenseView.from - startsAt 노출 정책")
    class MyLicenseViewStartsAt {

        @Test
        @DisplayName("ACTIVE 상태: startsAt = null")
        void shouldHideStartsAtWhenActive() {
            License license = buildActiveLicense();

            MyLicenseView view = MyLicenseView.from(license);

            assertThat(view.status()).isEqualTo(LicenseStatus.ACTIVE);
            assertThat(view.startsAt()).isNull();
        }

        @Test
        @DisplayName("PENDING + validFrom 미래: startsAt = validFrom")
        void shouldExposeStartsAtWhenPending() {
            Instant future = Instant.now().plus(7, ChronoUnit.DAYS);
            License license = buildPendingLicense(future, future.plus(14, ChronoUnit.DAYS));

            MyLicenseView view = MyLicenseView.from(license);

            assertThat(view.status()).isEqualTo(LicenseStatus.PENDING);
            assertThat(view.startsAt()).isEqualTo(future);
        }

        @Test
        @DisplayName("EXPIRED_GRACE 상태: startsAt = null")
        void shouldHideStartsAtWhenExpiredGrace() {
            License license = buildExpiredGraceLicense();

            MyLicenseView view = MyLicenseView.from(license);

            assertThat(view.status()).isEqualTo(LicenseStatus.EXPIRED_GRACE);
            assertThat(view.startsAt()).isNull();
        }

        @Test
        @DisplayName("REVOKED 상태: startsAt = null")
        void shouldHideStartsAtWhenRevoked() {
            License license = buildActiveLicense();
            license.revoke("test");

            MyLicenseView view = MyLicenseView.from(license);

            assertThat(view.status()).isEqualTo(LicenseStatus.REVOKED);
            assertThat(view.startsAt()).isNull();
        }
    }

    @Nested
    @DisplayName("LicenseResponse.from - startsAt 노출 정책")
    class LicenseResponseStartsAt {

        @Test
        @DisplayName("ACTIVE 상태: startsAt = null")
        void shouldHideStartsAtWhenActive() {
            License license = buildActiveLicense();

            LicenseResponse response = LicenseResponse.from(license);

            assertThat(response.status()).isEqualTo(LicenseStatus.ACTIVE);
            assertThat(response.startsAt()).isNull();
        }

        @Test
        @DisplayName("PENDING + validFrom 미래: startsAt = validFrom")
        void shouldExposeStartsAtWhenPending() {
            Instant future = Instant.now().plus(7, ChronoUnit.DAYS);
            License license = buildPendingLicense(future, future.plus(14, ChronoUnit.DAYS));

            LicenseResponse response = LicenseResponse.from(license);

            assertThat(response.status()).isEqualTo(LicenseStatus.PENDING);
            assertThat(response.startsAt()).isEqualTo(future);
        }

        @Test
        @DisplayName("EXPIRED_GRACE 상태: startsAt = null")
        void shouldHideStartsAtWhenExpiredGrace() {
            License license = buildExpiredGraceLicense();

            LicenseResponse response = LicenseResponse.from(license);

            assertThat(response.status()).isEqualTo(LicenseStatus.EXPIRED_GRACE);
            assertThat(response.startsAt()).isNull();
        }

        @Test
        @DisplayName("REVOKED 상태: startsAt = null")
        void shouldHideStartsAtWhenRevoked() {
            License license = buildActiveLicense();
            license.revoke("test");

            LicenseResponse response = LicenseResponse.from(license);

            assertThat(response.status()).isEqualTo(LicenseStatus.REVOKED);
            assertThat(response.startsAt()).isNull();
        }
    }

    // === 헬퍼 ===

    private License buildActiveLicense() {
        License license = License.builder()
                .ownerType(OwnerType.USER)
                .ownerId(OWNER_ID)
                .productId(PRODUCT_ID)
                .licenseType(LicenseType.SUBSCRIPTION)
                .validFrom(Instant.now().minus(1, ChronoUnit.DAYS))
                .validUntil(Instant.now().plus(30, ChronoUnit.DAYS))
                .policySnapshot(Map.of("entitlements", java.util.List.of("core")))
                .build();
        license.activate();
        ReflectionTestUtils.setField(license, "id", UUID.randomUUID());
        return license;
    }

    private License buildPendingLicense(Instant validFrom, Instant validUntil) {
        License license = License.builder()
                .ownerType(OwnerType.USER)
                .ownerId(OWNER_ID)
                .productId(PRODUCT_ID)
                .licenseType(LicenseType.TRIAL)
                .validFrom(validFrom)
                .validUntil(validUntil)
                .policySnapshot(Map.of("entitlements", java.util.List.of("core")))
                .build();
        // status는 빌더에서 PENDING으로 시작 - activate() 호출하지 않음
        ReflectionTestUtils.setField(license, "id", UUID.randomUUID());
        return license;
    }

    private License buildExpiredGraceLicense() {
        Map<String, Object> policy = Map.of("gracePeriodDays", 7);
        License license = License.builder()
                .ownerType(OwnerType.USER)
                .ownerId(OWNER_ID)
                .productId(PRODUCT_ID)
                .licenseType(LicenseType.SUBSCRIPTION)
                .validFrom(Instant.now().minus(40, ChronoUnit.DAYS))
                .validUntil(Instant.now().minus(3, ChronoUnit.DAYS))
                .policySnapshot(policy)
                .build();
        license.activate();
        ReflectionTestUtils.setField(license, "id", UUID.randomUUID());
        return license;
    }
}
