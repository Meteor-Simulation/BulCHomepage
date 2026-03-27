package com.bulc.homepage.licensing.scheduler;

import com.bulc.homepage.licensing.service.SessionCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 라이선스 세션 자동 정리 스케줄러.
 *
 * 1. markStaleSessions: 5분마다 실행 - heartbeat 없는 ACTIVE 세션을 STALE로 전환
 * 2. deactivateExpiredStaleSessions: 10분마다 실행 - 오래된 STALE 세션을 DEACTIVATED로 전환
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SessionCleanupScheduler {

    private final SessionCleanupService sessionCleanupService;

    /**
     * ACTIVE → STALE 전환 (5분마다).
     * staleThresholdMinutes(30분) 이상 heartbeat 없는 ACTIVE 세션을 STALE로 전환.
     */
    @Scheduled(fixedRate = 300_000)
    public void markStaleSessions() {
        try {
            int count = sessionCleanupService.markStaleSessions();
            if (count > 0) {
                log.info("세션 Stale 전환: {}건", count);
            }
        } catch (Exception e) {
            log.error("세션 Stale 전환 스케줄러 오류: {}", e.getMessage(), e);
        }
    }

    /**
     * STALE → DEACTIVATED 전환 (10분마다).
     * sessionTtlMinutes(라이선스별 정책, 기본 60분) 이상 경과한 STALE 세션을 비활성화.
     */
    @Scheduled(fixedRate = 600_000)
    public void deactivateExpiredStaleSessions() {
        try {
            int count = sessionCleanupService.deactivateExpiredStaleSessions();
            if (count > 0) {
                log.info("Stale 세션 비활성화: {}건", count);
            }
        } catch (Exception e) {
            log.error("Stale 세션 비활성화 스케줄러 오류: {}", e.getMessage(), e);
        }
    }
}
