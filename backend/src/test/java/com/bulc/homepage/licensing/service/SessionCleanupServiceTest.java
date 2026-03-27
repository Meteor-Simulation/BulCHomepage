package com.bulc.homepage.licensing.service;

import com.bulc.homepage.licensing.domain.*;
import com.bulc.homepage.licensing.repository.ActivationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * SessionCleanupService 유닛 테스트.
 */
@ExtendWith(MockitoExtension.class)
class SessionCleanupServiceTest {

    @Mock
    private ActivationRepository activationRepository;

    private SessionCleanupService sessionCleanupService;

    private static final int STALE_THRESHOLD_MINUTES = 30;

    @BeforeEach
    void setUp() {
        sessionCleanupService = new SessionCleanupService(activationRepository, STALE_THRESHOLD_MINUTES);
    }

    @Nested
    @DisplayName("markStaleSessions - ACTIVE → STALE 전환")
    class MarkStaleSessionsTest {

        @Test
        @DisplayName("staleThreshold 기준으로 markStaleActivations 호출")
        void shouldCallMarkStaleActivationsWithCorrectThreshold() {
            given(activationRepository.markStaleActivations(any(Instant.class), any(Instant.class)))
                    .willReturn(3);

            int count = sessionCleanupService.markStaleSessions();

            assertThat(count).isEqualTo(3);
            verify(activationRepository).markStaleActivations(any(Instant.class), any(Instant.class));
        }

        @Test
        @DisplayName("전환 대상 없으면 0 반환")
        void shouldReturnZeroWhenNoStaleSessions() {
            given(activationRepository.markStaleActivations(any(Instant.class), any(Instant.class)))
                    .willReturn(0);

            int count = sessionCleanupService.markStaleSessions();

            assertThat(count).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("deactivateExpiredStaleSessions - STALE → DEACTIVATED 전환")
    class DeactivateExpiredStaleSessionsTest {

        @Test
        @DisplayName("sessionTtlMinutes 초과한 STALE 세션 비활성화")
        void shouldDeactivateExpiredStaleSessions() {
            // sessionTtlMinutes = 60분인 라이선스
            License license = createLicenseWithSessionTtl(60);
            // 2시간 전에 lastSeenAt (60분 TTL 초과)
            Activation staleActivation = createStaleActivation(license, 120);

            given(activationRepository.findAllStaleWithLicense())
                    .willReturn(List.of(staleActivation));

            int count = sessionCleanupService.deactivateExpiredStaleSessions();

            assertThat(count).isEqualTo(1);
            assertThat(staleActivation.getStatus()).isEqualTo(ActivationStatus.DEACTIVATED);
            assertThat(staleActivation.getDeactivatedReason()).isEqualTo("SESSION_TIMEOUT");
        }

        @Test
        @DisplayName("sessionTtlMinutes 이내인 STALE 세션은 유지")
        void shouldNotDeactivateRecentStaleSessions() {
            // sessionTtlMinutes = 60분인 라이선스
            License license = createLicenseWithSessionTtl(60);
            // 10분 전에 lastSeenAt (60분 TTL 이내)
            Activation staleActivation = createStaleActivation(license, 10);

            given(activationRepository.findAllStaleWithLicense())
                    .willReturn(List.of(staleActivation));

            int count = sessionCleanupService.deactivateExpiredStaleSessions();

            assertThat(count).isEqualTo(0);
            assertThat(staleActivation.getStatus()).isEqualTo(ActivationStatus.STALE);
        }

        @Test
        @DisplayName("라이선스별 서로 다른 sessionTtlMinutes 적용")
        void shouldRespectPerLicenseSessionTtl() {
            // 라이선스 A: TTL 60분, lastSeen 90분 전 → 비활성화 대상
            License licenseA = createLicenseWithSessionTtl(60);
            Activation activationA = createStaleActivation(licenseA, 90);

            // 라이선스 B: TTL 120분, lastSeen 90분 전 → 아직 유효
            License licenseB = createLicenseWithSessionTtl(120);
            Activation activationB = createStaleActivation(licenseB, 90);

            given(activationRepository.findAllStaleWithLicense())
                    .willReturn(List.of(activationA, activationB));

            int count = sessionCleanupService.deactivateExpiredStaleSessions();

            assertThat(count).isEqualTo(1);
            assertThat(activationA.getStatus()).isEqualTo(ActivationStatus.DEACTIVATED);
            assertThat(activationB.getStatus()).isEqualTo(ActivationStatus.STALE);
        }

        @Test
        @DisplayName("STALE 세션 없으면 0 반환")
        void shouldReturnZeroWhenNoStaleActivations() {
            given(activationRepository.findAllStaleWithLicense())
                    .willReturn(Collections.emptyList());

            int count = sessionCleanupService.deactivateExpiredStaleSessions();

            assertThat(count).isEqualTo(0);
            verify(activationRepository).findAllStaleWithLicense();
        }

        @Test
        @DisplayName("비활성화 시 오프라인 토큰도 무효화됨")
        void shouldClearOfflineTokenOnDeactivation() {
            License license = createLicenseWithSessionTtl(60);
            Activation activation = createStaleActivation(license, 120);
            // 오프라인 토큰 설정
            activation.issueOfflineToken("test-token", Instant.now().plus(7, ChronoUnit.DAYS));

            given(activationRepository.findAllStaleWithLicense())
                    .willReturn(List.of(activation));

            sessionCleanupService.deactivateExpiredStaleSessions();

            assertThat(activation.getOfflineToken()).isNull();
            assertThat(activation.getOfflineTokenExpiresAt()).isNull();
        }
    }

    // === Helper Methods ===

    private License createLicenseWithSessionTtl(int sessionTtlMinutes) {
        License license = License.builder()
                .ownerType(OwnerType.USER)
                .ownerId(UUID.randomUUID())
                .productId(UUID.randomUUID())
                .licenseType(LicenseType.SUBSCRIPTION)
                .validUntil(Instant.now().plus(30, ChronoUnit.DAYS))
                .policySnapshot(Map.of("sessionTtlMinutes", sessionTtlMinutes))
                .build();
        license.activate();
        return license;
    }

    private Activation createStaleActivation(License license, int minutesAgo) {
        Activation activation = Activation.builder()
                .license(license)
                .deviceFingerprint("device-" + System.nanoTime())
                .clientVersion("1.0.0")
                .clientOs("Windows")
                .lastIp("127.0.0.1")
                .build();
        // STALE 상태로 전환
        activation.markAsStale();
        // lastSeenAt을 과거로 설정
        activation.forceSetLastSeenAt(Instant.now().minus(minutesAgo, ChronoUnit.MINUTES));
        return activation;
    }
}
