package com.bulc.homepage.payment.recovery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LicenseIssueRetryRepository extends JpaRepository<LicenseIssueRetry, Long> {

    Optional<LicenseIssueRetry> findBySourceOrderIdAndOperation(
            UUID sourceOrderId, LicenseIssueRetry.Operation operation);

    /** 재시도 대상: 미해결(PENDING) + 소진 전 */
    List<LicenseIssueRetry> findByStatusAndRetryCountLessThan(
            LicenseIssueRetry.Status status, int maxRetryCount);

    /** 재시도가 소진되어 운영 개입이 필요한 건 */
    List<LicenseIssueRetry> findByStatus(LicenseIssueRetry.Status status);
}
