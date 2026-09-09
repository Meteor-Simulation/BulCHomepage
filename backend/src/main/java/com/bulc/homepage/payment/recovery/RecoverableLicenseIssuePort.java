package com.bulc.homepage.payment.recovery;

import com.bulc.homepage.payment.notification.LicenseIssuedEvent;
import com.bulc.homepage.payment.port.IssuedLicense;
import com.bulc.homepage.payment.port.LicenseIssueFailedException;
import com.bulc.homepage.payment.port.LicenseIssuePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * 라이선스 발급/연장에 즉시 재시도와 실패 적재를 얹는 데코레이터 (MDP-832).
 *
 * <p>결제 성공 후 발급이 실패하면 사용자는 돈만 내고 제품을 못 쓴다.
 * 2단으로 막는다.
 * <ol>
 *   <li>즉시 재시도 — 순간적인 DB 락·일시 오류를 결제 응답 안에서 해소한다.
 *       성공하면 사용자는 문제를 인지조차 못 한다.</li>
 *   <li>실패 적재 — 그래도 실패하면 재시도 큐에 남기고 예외를 그대로 던진다.
 *       {@link LicenseIssueRetryScheduler} 가 나중에 복구한다.</li>
 * </ol>
 *
 * <p>예외를 삼키지 않는 이유: 기존 호출부가 실패를 잡아 payments.fail_reason 을
 * 기록하고 사용자 응답에 안내를 넣는 동작을 그대로 유지하기 위해서다.
 *
 * <p>이 데코레이터가 {@code @Primary} 라, 발급 4개 경로(결제창·빌링키·웹훅·구독갱신)가
 * 호출부 수정 없이 모두 여기를 통과한다.
 */
@Component
@Primary
@Slf4j
public class RecoverableLicenseIssuePort implements LicenseIssuePort {

    /** 즉시 재시도 횟수. 결제 응답을 붙잡는 시간이라 짧게 유지한다. */
    private static final int IMMEDIATE_ATTEMPTS = 2;
    private static final long IMMEDIATE_BACKOFF_MS = 200L;

    private final LicenseIssuePort delegate;
    private final LicenseIssueRecoveryService recoveryService;
    private final ApplicationEventPublisher eventPublisher;

    public RecoverableLicenseIssuePort(@Qualifier("paymentLicenseIssueAdapter") LicenseIssuePort delegate,
                                       LicenseIssueRecoveryService recoveryService,
                                       ApplicationEventPublisher eventPublisher) {
        this.delegate = delegate;
        this.recoveryService = recoveryService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 구매 자격 검사는 결제 전 검증이라 재시도·적재 대상이 아니다. 그대로 위임한다.
     */
    @Override
    public void requirePurchasable(UUID userId, UUID licensePlanId) {
        delegate.requirePurchasable(userId, licensePlanId);
    }

    @Override
    public IssuedLicense issue(UUID userId, UUID licensePlanId, UUID sourceOrderId) {
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= IMMEDIATE_ATTEMPTS; attempt++) {
            try {
                IssuedLicense license = delegate.issue(userId, licensePlanId, sourceOrderId);
                if (attempt > 1) {
                    log.info("[발급복구] 즉시 재시도 성공 - sourceOrderId={}, attempt={}", sourceOrderId, attempt);
                }
                // 발급 완료 통지 (MDP-833). 이 데코레이터가 @Primary 라 발급 4개 경로가 모두 지나가므로
                // 여기 한 곳에서 발행하면 호출부 수정 없이 전 경로가 커버된다.
                // 재시도 큐를 통한 복구 발급은 이 데코레이터를 우회하므로
                // LicenseIssueRecoveryService.retryOne 에서 따로 발행한다.
                eventPublisher.publishEvent(new LicenseIssuedEvent(
                        userId, license.id(), license.licenseKey(), license.validUntil(),
                        sourceOrderId, false));
                return license;
            } catch (RuntimeException e) {
                lastFailure = e;
                logAttempt(attempt, sourceOrderId, e);
                backoffBeforeNextAttempt(attempt);
            }
        }

        recoveryService.enqueueIssue(userId, licensePlanId, sourceOrderId, lastFailure);
        throw lastFailure;
    }

    @Override
    public void renew(UUID userId, UUID licensePlanId, UUID sourceOrderId, Instant newValidUntil) {
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= IMMEDIATE_ATTEMPTS; attempt++) {
            try {
                delegate.renew(userId, licensePlanId, sourceOrderId, newValidUntil);
                if (attempt > 1) {
                    log.info("[발급복구] 즉시 재시도 성공(연장) - sourceOrderId={}, attempt={}", sourceOrderId, attempt);
                }
                return;
            } catch (RuntimeException e) {
                lastFailure = e;
                logAttempt(attempt, sourceOrderId, e);
                backoffBeforeNextAttempt(attempt);
            }
        }

        recoveryService.enqueueRenew(userId, licensePlanId, sourceOrderId, newValidUntil, lastFailure);
        throw lastFailure;
    }

    private void logAttempt(int attempt, UUID sourceOrderId, RuntimeException e) {
        log.warn("[발급복구] 시도 {}/{} 실패 - sourceOrderId={}, error={}",
                attempt, IMMEDIATE_ATTEMPTS, sourceOrderId, e.getMessage());
    }

    /**
     * 마지막 시도 뒤에는 대기하지 않는다 — 결제 응답을 불필요하게 붙잡지 않기 위해서다.
     */
    private void backoffBeforeNextAttempt(int attempt) {
        if (attempt >= IMMEDIATE_ATTEMPTS) {
            return;
        }
        try {
            Thread.sleep(IMMEDIATE_BACKOFF_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new LicenseIssueFailedException("라이선스 발급 재시도가 중단되었습니다.", ie);
        }
    }
}
