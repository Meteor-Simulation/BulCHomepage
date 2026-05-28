package com.bulc.homepage.licensing.scheduler;

import com.bulc.homepage.licensing.service.LicenseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * TRIAL chaining으로 지연 시작된 PENDING 라이선스의 자동 활성화 스케줄러.
 *
 * 1분마다 실행하여 validFrom이 도래한 PENDING 라이선스를 ACTIVE로 전이.
 * Lazy 평가(License.calculateEffectiveStatus)가 항상 정확한 상태를 반환하므로
 * 사용자 노출 측면에서는 즉시 반영되지만, DB raw status도 정기적으로 갱신하여
 * 통계/감사/외부 시스템 연동의 일관성을 유지합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PendingLicenseActivationScheduler {

    private final LicenseService licenseService;

    @Scheduled(fixedRate = 60_000)
    public void activatePendingLicenses() {
        try {
            int count = licenseService.activatePendingLicenses();
            if (count > 0) {
                log.info("PENDING → ACTIVE 전이: {}건", count);
            }
        } catch (Exception e) {
            log.error("PENDING 라이선스 활성화 스케줄러 오류: {}", e.getMessage(), e);
        }
    }
}
