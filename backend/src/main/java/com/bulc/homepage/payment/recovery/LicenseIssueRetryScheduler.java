package com.bulc.homepage.payment.recovery;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 결제 성공 후 실패한 라이선스 발급/연장을 주기적으로 복구한다 (MDP-832).
 *
 * <p>주기를 5분으로 잡은 이유: 이 구간은 사용자가 결제를 마치고도 제품을 못 쓰는
 * 시간이라 짧을수록 좋다. 즉시 재시도(RecoverableLicenseIssuePort)가 1차로 막고,
 * 그걸 통과한 실패만 여기 남으므로 대상 건수는 적다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LicenseIssueRetryScheduler {

    private final LicenseIssueRecoveryService recoveryService;

    @Scheduled(fixedRate = 300_000)
    public void retryFailedLicenseIssues() {
        try {
            List<LicenseIssueRetry> targets = recoveryService.findRetryable();
            if (targets.isEmpty()) {
                return;
            }

            log.info("[발급복구] 재시도 대상 {}건", targets.size());

            int recovered = 0;
            for (LicenseIssueRetry retry : targets) {
                if (recoveryService.retryOne(retry)) {
                    recovered++;
                }
            }

            log.info("[발급복구] 재시도 완료 - 대상={}건, 복구={}건", targets.size(), recovered);
        } catch (Exception e) {
            log.error("[발급복구] 스케줄러 오류: {}", e.getMessage(), e);
        }
    }
}
