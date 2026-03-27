package com.bulc.homepage.licensing.service;

import com.bulc.homepage.licensing.domain.Activation;
import com.bulc.homepage.licensing.domain.License;
import com.bulc.homepage.licensing.repository.ActivationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 세션 자동 정리 서비스.
 *
 * 두 단계로 비활성 세션을 정리:
 * 1. ACTIVE → STALE: staleThresholdMinutes(30분) 이상 heartbeat 없는 세션
 * 2. STALE → DEACTIVATED: 라이선스별 sessionTtlMinutes(기본 60분) 이상 경과한 세션
 */
@Slf4j
@Service
public class SessionCleanupService {

    private final ActivationRepository activationRepository;
    private final int staleThresholdMinutes;

    public SessionCleanupService(
            ActivationRepository activationRepository,
            @Value("${bulc.licensing.stale-threshold-minutes:30}") int staleThresholdMinutes) {
        this.activationRepository = activationRepository;
        this.staleThresholdMinutes = staleThresholdMinutes;
    }

    /**
     * Step 1: ACTIVE → STALE 일괄 전환.
     * staleThresholdMinutes 이상 heartbeat 없는 ACTIVE 세션을 STALE로 전환.
     *
     * @return 전환된 세션 수
     */
    @Transactional
    public int markStaleSessions() {
        Instant now = Instant.now();
        Instant threshold = now.minusSeconds(staleThresholdMinutes * 60L);
        return activationRepository.markStaleActivations(threshold, now);
    }

    /**
     * Step 2: STALE → DEACTIVATED 전환.
     * 각 라이선스의 sessionTtlMinutes 기준으로
     * 충분히 오래된 STALE 세션을 DEACTIVATED(SESSION_TIMEOUT)로 전환.
     *
     * @return 비활성화된 세션 수
     */
    @Transactional
    public int deactivateExpiredStaleSessions() {
        Instant now = Instant.now();

        List<Activation> staleActivations = activationRepository.findAllStaleWithLicense();
        if (staleActivations.isEmpty()) {
            return 0;
        }

        int deactivatedCount = 0;
        for (Activation activation : staleActivations) {
            License license = activation.getLicense();
            int sessionTtlMinutes = license.getSessionTtlMinutes();
            Instant sessionDeadline = activation.getLastSeenAt()
                    .plusSeconds(sessionTtlMinutes * 60L);

            if (now.isAfter(sessionDeadline)) {
                activation.deactivate("SESSION_TIMEOUT");
                deactivatedCount++;
            }
        }

        return deactivatedCount;
    }
}
