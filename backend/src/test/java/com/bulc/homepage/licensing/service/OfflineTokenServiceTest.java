package com.bulc.homepage.licensing.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OfflineTokenService 유닛 테스트.
 *
 * 필수 보안 테스트:
 * - T-05: Absolute Cap (offlineToken exp ≤ license.validUntil)
 * - 갱신 임계값 정책 테스트
 */
class OfflineTokenServiceTest {

    private OfflineTokenService offlineTokenService;

    // 테스트용 RSA 개인키 (PEM 형식)
    private static final String TEST_PRIVATE_KEY = """
            -----BEGIN PRIVATE KEY-----
            ***REMOVED***
            ***REMOVED***
            ***REMOVED***
            ***REMOVED***
            ***REMOVED***
            ***REMOVED***
            ***REMOVED***
            ***REMOVED***
            ***REMOVED***
            ***REMOVED***
            ***REMOVED***
            ***REMOVED***
            ***REMOVED***
            ***REMOVED***
            ***REMOVED***
            ***REMOVED***
            ***REMOVED***
            ***REMOVED***
            ***REMOVED***
            ***REMOVED***
            ***REMOVED***
            ***REMOVED***
            ***REMOVED***
            ***REMOVED***
            ***REMOVED***
            ***REMOVED***
            -----END PRIVATE KEY-----
            """;

    @BeforeEach
    void setUp() {
        offlineTokenService = new OfflineTokenService(
                "bulc-license-server",
                TEST_PRIVATE_KEY,
                "test",
                0.5,  // renewalThresholdRatio
                3     // renewalThresholdDays
        );
        offlineTokenService.init();
    }

    // ==========================================
    // T-05: Absolute Cap 테스트 (Critical)
    // ==========================================

    @Nested
    @DisplayName("T-05: Absolute Cap 테스트 - offlineToken.exp ≤ license.validUntil")
    class AbsoluteCapTest {

        @Test
        @DisplayName("라이선스 잔여 기간 < allowOfflineDays 시 exp가 validUntil로 제한됨")
        void shouldCapExpToValidUntilWhenLicenseExpiresBeforeOfflinePeriod() {
            // given - 라이선스가 2일 후 만료, allowOfflineDays=30일
            UUID licenseId = UUID.randomUUID();
            String productCode = "BULC_EVAC";
            String deviceFingerprint = "device-123";
            List<String> entitlements = List.of("core-simulation");
            int allowOfflineDays = 30;
            Instant licenseValidUntil = Instant.now().plus(2, ChronoUnit.DAYS);

            // when
            OfflineTokenService.OfflineToken token = offlineTokenService.generateOfflineToken(
                    licenseId, productCode, deviceFingerprint, entitlements,
                    allowOfflineDays, licenseValidUntil
            );

            // then - exp는 now + 30일이 아니라 now + 2일 (licenseValidUntil)이어야 함
            assertThat(token).isNotNull();
            assertThat(token.expiresAt()).isBeforeOrEqualTo(licenseValidUntil);
            assertThat(token.expiresAt()).isAfter(Instant.now());
        }

        @Test
        @DisplayName("라이선스 잔여 기간 > allowOfflineDays 시 exp가 allowOfflineDays 기준으로 설정됨")
        void shouldUseAllowOfflineDaysWhenLicenseExpiresAfter() {
            // given - 라이선스가 365일 후 만료, allowOfflineDays=30일
            UUID licenseId = UUID.randomUUID();
            String productCode = "BULC_EVAC";
            String deviceFingerprint = "device-123";
            List<String> entitlements = List.of("core-simulation");
            int allowOfflineDays = 30;
            Instant licenseValidUntil = Instant.now().plus(365, ChronoUnit.DAYS);

            // when
            OfflineTokenService.OfflineToken token = offlineTokenService.generateOfflineToken(
                    licenseId, productCode, deviceFingerprint, entitlements,
                    allowOfflineDays, licenseValidUntil
            );

            // then - exp는 now + 30일 (allowOfflineDays)이어야 함
            assertThat(token).isNotNull();
            Instant expectedMax = Instant.now().plus(allowOfflineDays + 1, ChronoUnit.DAYS);
            Instant expectedMin = Instant.now().plus(allowOfflineDays - 1, ChronoUnit.DAYS);
            assertThat(token.expiresAt()).isBetween(expectedMin, expectedMax);
        }

        @Test
        @DisplayName("이미 만료된 라이선스는 exp가 현재 시간 이전 또는 직후")
        void shouldHandleAlreadyExpiredLicense() {
            // given - 라이선스가 이미 만료됨
            UUID licenseId = UUID.randomUUID();
            String productCode = "BULC_EVAC";
            String deviceFingerprint = "device-123";
            List<String> entitlements = List.of("core-simulation");
            int allowOfflineDays = 30;
            Instant licenseValidUntil = Instant.now().minus(1, ChronoUnit.HOURS);

            // when
            OfflineTokenService.OfflineToken token = offlineTokenService.generateOfflineToken(
                    licenseId, productCode, deviceFingerprint, entitlements,
                    allowOfflineDays, licenseValidUntil
            );

            // then - exp는 licenseValidUntil (이미 과거) 또는 그 부근
            assertThat(token).isNotNull();
            assertThat(token.expiresAt()).isBeforeOrEqualTo(licenseValidUntil);
        }

        @Test
        @DisplayName("licenseValidUntil이 null이면 allowOfflineDays 기준으로만 설정")
        void shouldUseAllowOfflineDaysOnlyWhenValidUntilIsNull() {
            // given - PERPETUAL 라이선스 (validUntil = null)
            UUID licenseId = UUID.randomUUID();
            String productCode = "BULC_EVAC";
            String deviceFingerprint = "device-123";
            List<String> entitlements = List.of("core-simulation");
            int allowOfflineDays = 30;

            // when
            OfflineTokenService.OfflineToken token = offlineTokenService.generateOfflineToken(
                    licenseId, productCode, deviceFingerprint, entitlements,
                    allowOfflineDays, null  // PERPETUAL
            );

            // then - exp는 now + 30일
            assertThat(token).isNotNull();
            Instant expectedMax = Instant.now().plus(allowOfflineDays + 1, ChronoUnit.DAYS);
            Instant expectedMin = Instant.now().plus(allowOfflineDays - 1, ChronoUnit.DAYS);
            assertThat(token.expiresAt()).isBetween(expectedMin, expectedMax);
        }
    }

    // ==========================================
    // 갱신 임계값 테스트
    // ==========================================

    @Nested
    @DisplayName("갱신 임계값 테스트")
    class RenewalThresholdTest {

        @Test
        @DisplayName("토큰이 없으면 갱신 필요")
        void shouldRenewWhenNoToken() {
            assertThat(offlineTokenService.shouldRenew(null, 30)).isTrue();
        }

        @Test
        @DisplayName("토큰이 이미 만료되면 갱신 필요")
        void shouldRenewWhenExpired() {
            Instant expiredAt = Instant.now().minus(1, ChronoUnit.HOURS);
            assertThat(offlineTokenService.shouldRenew(expiredAt, 30)).isTrue();
        }

        @Test
        @DisplayName("남은 기간이 50% 미만이면 갱신 필요 (30일 중 14일 남음)")
        void shouldRenewWhenLessThan50PercentRemaining() {
            // 30일 TTL 중 14일 남음 (46.7% < 50%)
            Instant expiresAt = Instant.now().plus(14, ChronoUnit.DAYS);
            assertThat(offlineTokenService.shouldRenew(expiresAt, 30)).isTrue();
        }

        @Test
        @DisplayName("남은 기간이 3일 미만이면 갱신 필요")
        void shouldRenewWhenLessThan3DaysRemaining() {
            // 2일 남음 (< 3일)
            Instant expiresAt = Instant.now().plus(2, ChronoUnit.DAYS);
            assertThat(offlineTokenService.shouldRenew(expiresAt, 30)).isTrue();
        }

        @Test
        @DisplayName("남은 기간이 50% 이상이고 3일 이상이면 갱신 불필요")
        void shouldNotRenewWhenSufficientTimeRemaining() {
            // 30일 TTL 중 20일 남음 (66.7% > 50%, 20일 > 3일)
            Instant expiresAt = Instant.now().plus(20, ChronoUnit.DAYS);
            assertThat(offlineTokenService.shouldRenew(expiresAt, 30)).isFalse();
        }
    }

    // ==========================================
    // 토큰 형식 테스트
    // ==========================================

    @Nested
    @DisplayName("토큰 형식 테스트")
    class TokenFormatTest {

        @Test
        @DisplayName("RS256 JWS 형식으로 토큰 생성")
        void shouldGenerateRS256Token() {
            // given
            UUID licenseId = UUID.randomUUID();
            String productCode = "BULC_EVAC";
            String deviceFingerprint = "device-123";
            List<String> entitlements = List.of("core-simulation");
            int allowOfflineDays = 30;
            Instant licenseValidUntil = Instant.now().plus(365, ChronoUnit.DAYS);

            // when
            OfflineTokenService.OfflineToken token = offlineTokenService.generateOfflineToken(
                    licenseId, productCode, deviceFingerprint, entitlements,
                    allowOfflineDays, licenseValidUntil
            );

            // then - JWT 형식 (header.payload.signature)
            assertThat(token).isNotNull();
            assertThat(token.token()).isNotNull();
            String[] parts = token.token().split("\\.");
            assertThat(parts).hasSize(3);
        }

        @Test
        @DisplayName("키가 없으면 null 반환 (dev 환경)")
        void shouldReturnNullWhenNoKey() {
            // given - 키 없이 초기화
            OfflineTokenService noKeyService = new OfflineTokenService(
                    "bulc-license-server",
                    "",  // 빈 키
                    "dev",
                    0.5, 3
            );
            noKeyService.init();

            // when
            OfflineTokenService.OfflineToken token = noKeyService.generateOfflineToken(
                    UUID.randomUUID(), "BULC_EVAC", "device-123",
                    List.of("core"), 30, Instant.now().plus(30, ChronoUnit.DAYS)
            );

            // then
            assertThat(token).isNull();
        }
    }
}
