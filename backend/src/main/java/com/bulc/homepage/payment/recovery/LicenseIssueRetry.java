package com.bulc.homepage.payment.recovery;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 결제는 성공했으나 라이선스 발급/연장이 실패한 건. 스케줄러가 재시도한다.
 *
 * <p>{@code source_order_id} 는 발급 멱등 키라 재시도해도 중복 발급되지 않는다.
 */
@Entity
@Table(name = "license_issue_retries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LicenseIssueRetry {

    /** 재시도 소진 기준. 기존 구독 결제 재시도(SubscriptionBillingService)와 동일하게 3회. */
    public static final int MAX_RETRY_COUNT = 3;

    public enum Operation {
        ISSUE,
        RENEW
    }

    public enum Status {
        PENDING,
        SUCCESS,
        EXHAUSTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "license_plan_id", nullable = false)
    private UUID licensePlanId;

    @Column(name = "source_order_id", nullable = false)
    private UUID sourceOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Operation operation;

    /** RENEW 전용 — 연장 후 만료 시각. ISSUE 는 null. */
    @Column(name = "valid_until")
    private Instant validUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "last_attempted_at")
    private LocalDateTime lastAttemptedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    public void markAttemptFailed(String error) {
        this.retryCount++;
        this.lastError = error;
        this.lastAttemptedAt = LocalDateTime.now();
        if (this.retryCount >= MAX_RETRY_COUNT) {
            this.status = Status.EXHAUSTED;
        }
    }

    public void markResolved() {
        this.status = Status.SUCCESS;
        this.resolvedAt = LocalDateTime.now();
        this.lastAttemptedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
