package com.bulc.homepage.licensing.query.view;

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
 * Admin 쿼리 뷰(LicenseSummaryView, LicenseDetailView)의 startsAt 노출 정책 테스트.
 *
 * 정책: effective status == PENDING 일 때만 startsAt = validFrom 노출, 그 외 null.
 */
class LicenseAdminViewStartsAtTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Nested
    @DisplayName("LicenseSummaryView.from - startsAt 노출 정책")
    class SummaryViewStartsAt {

        @Test
        @DisplayName("PENDING 상태: startsAt = validFrom")
        void shouldExposeStartsAtWhenPending() {
            Instant future = Instant.now().plus(7, ChronoUnit.DAYS);
            License license = buildPendingLicense(future, future.plus(14, ChronoUnit.DAYS));

            LicenseSummaryView view = LicenseSummaryView.from(license);

            assertThat(view.status()).isEqualTo(LicenseStatus.PENDING);
            assertThat(view.startsAt()).isEqualTo(future);
        }

        @Test
        @DisplayName("ACTIVE 상태: startsAt = null")
        void shouldHideStartsAtWhenActive() {
            License license = buildActiveLicense();

            LicenseSummaryView view = LicenseSummaryView.from(license);

            assertThat(view.status()).isEqualTo(LicenseStatus.ACTIVE);
            assertThat(view.startsAt()).isNull();
        }
    }

    @Nested
    @DisplayName("LicenseDetailView.from - startsAt 노출 정책")
    class DetailViewStartsAt {

        @Test
        @DisplayName("PENDING 상태: startsAt = validFrom")
        void shouldExposeStartsAtWhenPending() {
            Instant future = Instant.now().plus(7, ChronoUnit.DAYS);
            License license = buildPendingLicense(future, future.plus(14, ChronoUnit.DAYS));

            LicenseDetailView view = LicenseDetailView.from(license);

            assertThat(view.status()).isEqualTo(LicenseStatus.PENDING);
            assertThat(view.startsAt()).isEqualTo(future);
        }

        @Test
        @DisplayName("ACTIVE 상태: startsAt = null")
        void shouldHideStartsAtWhenActive() {
            License license = buildActiveLicense();

            LicenseDetailView view = LicenseDetailView.from(license);

            assertThat(view.status()).isEqualTo(LicenseStatus.ACTIVE);
            assertThat(view.startsAt()).isNull();
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
                .policySnapshot(Map.of(
                        "maxActivations", 3,
                        "maxConcurrentSessions", 2,
                        "gracePeriodDays", 7,
                        "entitlements", java.util.List.of("core")
                ))
                .licenseKey("TEST-AAAA-BBBB-CCCC")
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
                .policySnapshot(Map.of(
                        "maxActivations", 1,
                        "maxConcurrentSessions", 1,
                        "gracePeriodDays", 0,
                        "entitlements", java.util.List.of("core")
                ))
                .licenseKey("TEST-DDDD-EEEE-FFFF")
                .build();
        // PENDING 유지 - activate() 호출 안 함
        ReflectionTestUtils.setField(license, "id", UUID.randomUUID());
        return license;
    }
}
