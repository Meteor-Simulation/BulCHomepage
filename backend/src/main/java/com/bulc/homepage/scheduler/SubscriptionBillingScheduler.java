package com.bulc.homepage.scheduler;

import com.bulc.homepage.service.SubscriptionBillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 구독 결제 스케줄러
 *
 * 주요 기능:
 * 1. 갱신 대상 구독 결제 처리 (매일 오전 9시)
 * 2. 실패한 결제 재시도 (매일 오후 2시, 6시)
 * 3. 만료된 구독 처리 (매일 자정)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionBillingScheduler {

    private final SubscriptionBillingService subscriptionBillingService;

    /**
     * 갱신 대상 구독 결제 처리
     * 매일 오전 9시 실행
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void processDueSubscriptions() {
        log.info("===== 구독 갱신 결제 스케줄러 시작 =====");
        try {
            subscriptionBillingService.processDueSubscriptions();
        } catch (Exception e) {
            log.error("구독 갱신 결제 스케줄러 오류: {}", e.getMessage(), e);
        }
        log.info("===== 구독 갱신 결제 스케줄러 종료 =====");
    }

    /**
     * 실패한 결제 재시도
     * 매일 오후 2시, 6시 실행
     */
    @Scheduled(cron = "0 0 14,18 * * *")
    public void retryFailedPayments() {
        log.info("===== 결제 재시도 스케줄러 시작 =====");
        try {
            subscriptionBillingService.retryFailedPayments();
        } catch (Exception e) {
            log.error("결제 재시도 스케줄러 오류: {}", e.getMessage(), e);
        }
        log.info("===== 결제 재시도 스케줄러 종료 =====");
    }

    /**
     * 만료된 구독 처리
     * 매일 자정 실행
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void processExpiredSubscriptions() {
        log.info("===== 만료 구독 처리 스케줄러 시작 =====");
        try {
            subscriptionBillingService.processExpiredSubscriptions();
        } catch (Exception e) {
            log.error("만료 구독 처리 스케줄러 오류: {}", e.getMessage(), e);
        }
        log.info("===== 만료 구독 처리 스케줄러 종료 =====");
    }
}
