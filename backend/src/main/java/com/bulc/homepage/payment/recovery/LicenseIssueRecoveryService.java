package com.bulc.homepage.payment.recovery;

import com.bulc.homepage.payment.notification.LicenseIssuedEvent;
import com.bulc.homepage.payment.port.IssuedLicense;
import com.bulc.homepage.payment.port.LicenseIssuePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 결제 성공 후 실패한 라이선스 발급/연장을 적재하고 재시도한다 (MDP-832).
 */
@Service
@Slf4j
public class LicenseIssueRecoveryService {

    /** 재시도는 데코레이터를 거치지 않고 실제 발급자에게 직접 간다 (중첩 적재 방지). */
    private final LicenseIssuePort delegate;
    private final LicenseIssueRetryRepository retryRepository;
    private final ApplicationEventPublisher eventPublisher;

    public LicenseIssueRecoveryService(
            @Qualifier("paymentLicenseIssueAdapter") LicenseIssuePort delegate,
            LicenseIssueRetryRepository retryRepository,
            ApplicationEventPublisher eventPublisher) {
        this.delegate = delegate;
        this.retryRepository = retryRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 발급 실패를 재시도 큐에 넣는다.
     *
     * <p>{@code REQUIRES_NEW} 인 이유: 발급 실패가 DB 오류였다면 바깥 트랜잭션이
     * rollback-only 로 표시됐을 수 있다. 같은 트랜잭션에 적재하면 복구 기록까지
     * 함께 사라져 실패를 영영 놓친다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueueIssue(UUID userId, UUID licensePlanId, UUID sourceOrderId, RuntimeException cause) {
        enqueue(userId, licensePlanId, sourceOrderId,
                LicenseIssueRetry.Operation.ISSUE, null, cause);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueueRenew(UUID userId, UUID licensePlanId, UUID sourceOrderId,
                             Instant newValidUntil, RuntimeException cause) {
        enqueue(userId, licensePlanId, sourceOrderId,
                LicenseIssueRetry.Operation.RENEW, newValidUntil, cause);
    }

    private void enqueue(UUID userId, UUID licensePlanId, UUID sourceOrderId,
                         LicenseIssueRetry.Operation operation, Instant validUntil,
                         RuntimeException cause) {
        String error = cause == null ? "원인 미상" : String.valueOf(cause.getMessage());
        try {
            // 같은 주문의 실패가 재차 들어와도 행을 새로 쌓지 않는다.
            LicenseIssueRetry retry = retryRepository
                    .findBySourceOrderIdAndOperation(sourceOrderId, operation)
                    .orElseGet(() -> LicenseIssueRetry.builder()
                            .userId(userId)
                            .licensePlanId(licensePlanId)
                            .sourceOrderId(sourceOrderId)
                            .operation(operation)
                            .validUntil(validUntil)
                            .build());

            retry.setLastError(error);
            retryRepository.save(retry);

            log.error("[발급복구] 재시도 큐 적재 - userId={}, sourceOrderId={}, operation={}, error={}",
                    userId, sourceOrderId, operation, error);
        } catch (Exception e) {
            // 적재 실패까지 예외를 키우지 않는다. 원래 발급 실패가 호출부로 전달되는 게 우선이다.
            log.error("[발급복구] 재시도 큐 적재 실패 - sourceOrderId={}, operation={}, error={}",
                    sourceOrderId, operation, e.getMessage(), e);
        }
    }

    /**
     * 재시도 대상 조회. 스케줄러가 이 목록을 받아 {@link #retryOne} 을 건별로 호출한다.
     *
     * <p>루프를 서비스 안에 두지 않는 이유: 같은 빈 안에서 {@code retryOne} 을 호출하면
     * Spring 프록시를 우회해 {@code REQUIRES_NEW} 가 적용되지 않는다.
     */
    @Transactional(readOnly = true)
    public List<LicenseIssueRetry> findRetryable() {
        return retryRepository.findByStatusAndRetryCountLessThan(
                LicenseIssueRetry.Status.PENDING, LicenseIssueRetry.MAX_RETRY_COUNT);
    }

    /**
     * 건별 독립 트랜잭션. 한 건이 실패해도 나머지 처리에 영향을 주지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean retryOne(LicenseIssueRetry retry) {
        try {
            if (retry.getOperation() == LicenseIssueRetry.Operation.RENEW) {
                delegate.renew(retry.getUserId(), retry.getLicensePlanId(),
                        retry.getSourceOrderId(), retry.getValidUntil());
                // 갱신 통지는 발송 여부가 법적검토 대상이라 보류 상태다 (MDP-843).
            } else {
                IssuedLicense license = delegate.issue(
                        retry.getUserId(), retry.getLicensePlanId(), retry.getSourceOrderId());
                // 복구 발급 통지 (MDP-833). 결제는 됐는데 발급이 늦어진 건이라
                // 사용자가 키를 받을 다른 경로가 없다 — 통지가 가장 필요한 경우다.
                eventPublisher.publishEvent(new LicenseIssuedEvent(
                        retry.getUserId(), license.id(), license.licenseKey(), license.validUntil(),
                        retry.getSourceOrderId(), true));
            }

            retry.markResolved();
            retryRepository.save(retry);
            log.info("[발급복구] 복구 성공 - id={}, userId={}, sourceOrderId={}, operation={}",
                    retry.getId(), retry.getUserId(), retry.getSourceOrderId(), retry.getOperation());
            return true;
        } catch (Exception e) {
            retry.markAttemptFailed(e.getMessage());
            retryRepository.save(retry);

            if (retry.getStatus() == LicenseIssueRetry.Status.EXHAUSTED) {
                // 자동 복구 불가. 운영 개입이 필요한 시점이다.
                log.error("[발급복구] 재시도 소진 — 수동 처리 필요. id={}, userId={}, sourceOrderId={}, error={}",
                        retry.getId(), retry.getUserId(), retry.getSourceOrderId(), e.getMessage());
            } else {
                log.warn("[발급복구] 재시도 실패 - id={}, retryCount={}/{}, error={}",
                        retry.getId(), retry.getRetryCount(), LicenseIssueRetry.MAX_RETRY_COUNT, e.getMessage());
            }
            return false;
        }
    }
}
