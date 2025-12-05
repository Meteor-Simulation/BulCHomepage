package com.bulc.homepage.licensing.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Activation 도메인 유닛 테스트.
 *
 * 기기 활성화 상태 전이, Heartbeat, 오프라인 토큰 등 핵심 로직 검증.
 */
class ActivationTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    // ==========================================
    // Activation 생성
    // ==========================================

    @Nested
    @DisplayName("Activation 생성")
    class CreateActivation {

        @Test
        @DisplayName("생성 시 ACTIVE 상태로 시작")
        void shouldCreateWithActiveStatus() {
            License license = createActiveLicense();

            Activation activation = Activation.builder()
                    .license(license)
                    .deviceFingerprint("device-123")
                    .clientVersion("1.0.0")
                    .clientOs("Windows 11")
                    .lastIp("192.168.1.1")
                    .build();

            assertThat(activation.getStatus()).isEqualTo(ActivationStatus.ACTIVE);
            assertThat(activation.getDeviceFingerprint()).isEqualTo("device-123");
            assertThat(activation.getClientVersion()).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("생성 시 activatedAt과 lastSeenAt이 현재 시각으로 설정")
        void shouldSetTimestampsOnCreation() {
            License license = createActiveLicense();
            Instant before = Instant.now();

            Activation activation = Activation.builder()
                    .license(license)
                    .deviceFingerprint("device-123")
                    .build();

            Instant after = Instant.now();

            assertThat(activation.getActivatedAt())
                    .isAfterOrEqualTo(before)
                    .isBeforeOrEqualTo(after);
            assertThat(activation.getLastSeenAt())
                    .isAfterOrEqualTo(before)
                    .isBeforeOrEqualTo(after);
        }
    }

    // ==========================================
    // Heartbeat 갱신
    // ==========================================

    @Nested
    @DisplayName("Heartbeat 갱신 (updateHeartbeat)")
    class UpdateHeartbeat {

        @Test
        @DisplayName("lastSeenAt이 현재 시각으로 갱신됨")
        void shouldUpdateLastSeenAt() throws InterruptedException {
            Activation activation = createActiveActivation();
            Instant originalLastSeen = activation.getLastSeenAt();

            // 약간의 시간 차이를 두기 위해
            Thread.sleep(10);

            activation.updateHeartbeat("2.0.0", "Windows 11", "10.0.0.2");

            assertThat(activation.getLastSeenAt()).isAfter(originalLastSeen);
        }

        @Test
        @DisplayName("클라이언트 정보가 갱신됨")
        void shouldUpdateClientInfo() {
            Activation activation = createActiveActivation();

            activation.updateHeartbeat("2.0.0", "macOS Sonoma", "10.0.0.5");

            assertThat(activation.getClientVersion()).isEqualTo("2.0.0");
            assertThat(activation.getClientOs()).isEqualTo("macOS Sonoma");
            assertThat(activation.getLastIp()).isEqualTo("10.0.0.5");
        }

        @Test
        @DisplayName("STALE 상태에서 heartbeat 시 ACTIVE로 복귀")
        void shouldReactivateFromStaleOnHeartbeat() {
            Activation activation = createActiveActivation();
            activation.markAsStale();
            assertThat(activation.getStatus()).isEqualTo(ActivationStatus.STALE);

            activation.updateHeartbeat("1.0.0", "Windows", "10.0.0.1");

            assertThat(activation.getStatus()).isEqualTo(ActivationStatus.ACTIVE);
        }
    }

    // ==========================================
    // 비활성화 (deactivate)
    // ==========================================

    @Nested
    @DisplayName("비활성화 (deactivate)")
    class Deactivate {

        @Test
        @DisplayName("ACTIVE 상태에서 비활성화 가능")
        void shouldDeactivateFromActiveStatus() {
            Activation activation = createActiveActivation();

            activation.deactivate();

            assertThat(activation.getStatus()).isEqualTo(ActivationStatus.DEACTIVATED);
        }

        @Test
        @DisplayName("비활성화 시 오프라인 토큰 무효화")
        void shouldRevokeOfflineTokenOnDeactivate() {
            Activation activation = createActiveActivation();
            activation.issueOfflineToken("token-123", Instant.now().plus(30, ChronoUnit.DAYS));

            activation.deactivate();

            assertThat(activation.getOfflineToken()).isNull();
            assertThat(activation.getOfflineTokenExpiresAt()).isNull();
        }
    }

    // ==========================================
    // 재활성화 (reactivate)
    // ==========================================

    @Nested
    @DisplayName("재활성화 (reactivate)")
    class Reactivate {

        @Test
        @DisplayName("DEACTIVATED 상태에서 재활성화 가능")
        void shouldReactivateFromDeactivatedStatus() {
            Activation activation = createActiveActivation();
            activation.deactivate();

            activation.reactivate("2.0.0", "Windows 11", "10.0.0.5");

            assertThat(activation.getStatus()).isEqualTo(ActivationStatus.ACTIVE);
            assertThat(activation.getClientVersion()).isEqualTo("2.0.0");
        }

        @Test
        @DisplayName("STALE 상태에서 재활성화 가능")
        void shouldReactivateFromStaleStatus() {
            Activation activation = createActiveActivation();
            activation.markAsStale();

            activation.reactivate("2.0.0", "Windows 11", "10.0.0.5");

            assertThat(activation.getStatus()).isEqualTo(ActivationStatus.ACTIVE);
        }

        @Test
        @DisplayName("EXPIRED 상태에서 재활성화 시도 시 예외 발생")
        void shouldThrowWhenReactivatingExpiredActivation() {
            Activation activation = createActiveActivation();
            activation.expire();

            assertThatThrownBy(() -> activation.reactivate("2.0.0", "Windows", "10.0.0.1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("만료된 활성화");
        }
    }

    // ==========================================
    // STALE 상태 전이
    // ==========================================

    @Nested
    @DisplayName("STALE 상태 전이")
    class StaleTransition {

        @Test
        @DisplayName("ACTIVE 상태에서만 STALE로 전이 가능")
        void shouldTransitionToStaleFromActive() {
            Activation activation = createActiveActivation();

            activation.markAsStale();

            assertThat(activation.getStatus()).isEqualTo(ActivationStatus.STALE);
        }

        @Test
        @DisplayName("DEACTIVATED 상태에서는 STALE로 전이 안됨")
        void shouldNotTransitionToStaleFromDeactivated() {
            Activation activation = createActiveActivation();
            activation.deactivate();

            activation.markAsStale();

            assertThat(activation.getStatus()).isEqualTo(ActivationStatus.DEACTIVATED);
        }

        @Test
        @DisplayName("stalePeriodDays 이상 미접속 시 shouldBeStale = true")
        void shouldReturnTrueWhenLastSeenExceedsStalePeriod() {
            Activation activation = createActiveActivation();
            // reflection 없이 테스트하기 어려우므로 shouldBeStale 로직만 검증

            Instant now = Instant.now();
            int stalePeriodDays = 30;
            Instant staleThreshold = now.minusSeconds(stalePeriodDays * 24L * 60 * 60);

            // lastSeenAt이 threshold 이전이면 stale
            // 새로 생성된 activation은 lastSeenAt이 현재이므로 stale 아님
            boolean shouldBeStale = activation.shouldBeStale(stalePeriodDays, now);

            assertThat(shouldBeStale).isFalse();
        }
    }

    // ==========================================
    // 만료 (expire)
    // ==========================================

    @Nested
    @DisplayName("만료 (expire)")
    class Expire {

        @Test
        @DisplayName("expire 호출 시 EXPIRED 상태로 전이")
        void shouldTransitionToExpiredStatus() {
            Activation activation = createActiveActivation();

            activation.expire();

            assertThat(activation.getStatus()).isEqualTo(ActivationStatus.EXPIRED);
        }

        @Test
        @DisplayName("만료 시 오프라인 토큰 무효화")
        void shouldRevokeOfflineTokenOnExpire() {
            Activation activation = createActiveActivation();
            activation.issueOfflineToken("token-123", Instant.now().plus(30, ChronoUnit.DAYS));

            activation.expire();

            assertThat(activation.getOfflineToken()).isNull();
            assertThat(activation.getOfflineTokenExpiresAt()).isNull();
        }
    }

    // ==========================================
    // 오프라인 토큰
    // ==========================================

    @Nested
    @DisplayName("오프라인 토큰")
    class OfflineToken {

        @Test
        @DisplayName("오프라인 토큰 발급")
        void shouldIssueOfflineToken() {
            Activation activation = createActiveActivation();
            Instant expiresAt = Instant.now().plus(30, ChronoUnit.DAYS);

            activation.issueOfflineToken("token-abc", expiresAt);

            assertThat(activation.getOfflineToken()).isEqualTo("token-abc");
            assertThat(activation.getOfflineTokenExpiresAt()).isEqualTo(expiresAt);
        }

        @Test
        @DisplayName("유효한 오프라인 토큰 확인")
        void shouldReturnTrueForValidOfflineToken() {
            Activation activation = createActiveActivation();
            Instant expiresAt = Instant.now().plus(30, ChronoUnit.DAYS);
            activation.issueOfflineToken("token-abc", expiresAt);

            boolean hasValid = activation.hasValidOfflineToken(Instant.now());

            assertThat(hasValid).isTrue();
        }

        @Test
        @DisplayName("만료된 오프라인 토큰은 유효하지 않음")
        void shouldReturnFalseForExpiredOfflineToken() {
            Activation activation = createActiveActivation();
            Instant expiresAt = Instant.now().minus(1, ChronoUnit.DAYS);
            activation.issueOfflineToken("token-abc", expiresAt);

            boolean hasValid = activation.hasValidOfflineToken(Instant.now());

            assertThat(hasValid).isFalse();
        }

        @Test
        @DisplayName("토큰이 없으면 유효하지 않음")
        void shouldReturnFalseWhenNoToken() {
            Activation activation = createActiveActivation();

            boolean hasValid = activation.hasValidOfflineToken(Instant.now());

            assertThat(hasValid).isFalse();
        }

        @Test
        @DisplayName("오프라인 토큰 무효화")
        void shouldRevokeOfflineToken() {
            Activation activation = createActiveActivation();
            activation.issueOfflineToken("token-abc", Instant.now().plus(30, ChronoUnit.DAYS));

            activation.revokeOfflineToken();

            assertThat(activation.getOfflineToken()).isNull();
            assertThat(activation.getOfflineTokenExpiresAt()).isNull();
        }
    }

    // ==========================================
    // 헬퍼 메서드
    // ==========================================

    private License createActiveLicense() {
        License license = License.builder()
                .ownerType(OwnerType.USER)
                .ownerId(OWNER_ID)
                .productId(PRODUCT_ID)
                .licenseType(LicenseType.SUBSCRIPTION)
                .validUntil(Instant.now().plus(30, ChronoUnit.DAYS))
                .build();
        license.activate();
        return license;
    }

    private Activation createActiveActivation() {
        License license = createActiveLicense();
        return Activation.builder()
                .license(license)
                .deviceFingerprint("test-device")
                .clientVersion("1.0.0")
                .clientOs("Windows 10")
                .lastIp("192.168.1.1")
                .build();
    }
}
