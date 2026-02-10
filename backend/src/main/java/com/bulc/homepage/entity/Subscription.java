package com.bulc.homepage.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(name = "product_code", nullable = false, length = 3)
    private String productCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_code", referencedColumnName = "code", insertable = false, updatable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_plan_id", nullable = false)
    private PricePlan pricePlan;

    // A: Active(활성), E: Expired(만료), C: Canceled(취소)
    @Column(nullable = false, length = 1)
    @Builder.Default
    private String status = "A";

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Column(name = "auto_renew", nullable = false)
    @Builder.Default
    private Boolean autoRenew = false;

    @Column(name = "billing_key_id")
    private Long billingKeyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_key_id", insertable = false, updatable = false)
    private BillingKey billingKey;

    @Column(name = "next_billing_date")
    private LocalDateTime nextBillingDate;

    // MONTHLY, YEARLY, QUARTERLY 등
    @Column(name = "billing_cycle", length = 20)
    @Builder.Default
    private String billingCycle = "YEARLY";

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 자동 갱신 활성화 (빌링키 연결)
     */
    public void enableAutoRenew(Long billingKeyId, String billingCycle) {
        this.autoRenew = true;
        this.billingKeyId = billingKeyId;
        this.billingCycle = billingCycle;
        calculateNextBillingDate();
    }

    /**
     * 자동 갱신 비활성화
     */
    public void disableAutoRenew() {
        this.autoRenew = false;
        this.nextBillingDate = null;
    }

    /**
     * 다음 결제 예정일 계산
     */
    public void calculateNextBillingDate() {
        if (!this.autoRenew || this.endDate == null) {
            this.nextBillingDate = null;
            return;
        }
        // 구독 종료일 기준으로 다음 결제일 설정 (7일 전)
        this.nextBillingDate = this.endDate.minusDays(7);
    }

    /**
     * 구독 갱신 (결제 성공 후 호출)
     */
    public void renew() {
        LocalDateTime newStartDate = this.endDate;
        LocalDateTime newEndDate;

        switch (this.billingCycle) {
            case "MONTHLY":
                newEndDate = newStartDate.plusMonths(1);
                break;
            case "QUARTERLY":
                newEndDate = newStartDate.plusMonths(3);
                break;
            case "YEARLY":
            default:
                newEndDate = newStartDate.plusYears(1);
                break;
        }

        this.startDate = newStartDate;
        this.endDate = newEndDate;
        this.status = "A";
        calculateNextBillingDate();
    }

    /**
     * 구독 취소
     */
    public void cancel() {
        this.status = "C";
        this.autoRenew = false;
        this.nextBillingDate = null;
    }

    /**
     * 구독 만료
     */
    public void expire() {
        this.status = "E";
    }

    /**
     * 구독이 활성 상태인지 확인
     */
    public boolean isActive() {
        return "A".equals(this.status);
    }

    /**
     * 구독이 갱신 대상인지 확인 (결제일이 도래한 경우)
     */
    public boolean isDueForRenewal() {
        if (!this.autoRenew || this.nextBillingDate == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(this.nextBillingDate) ||
               LocalDateTime.now().isEqual(this.nextBillingDate);
    }
}
