package com.bulc.homepage.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_email_fk", length = 255)
    private String userEmailFk;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_email_fk", referencedColumnName = "email", insertable = false, updatable = false)
    private User user;

    @Column(name = "user_email", nullable = false, length = 255)
    private String userEmail;

    @Column(name = "user_name", length = 100)
    private String userName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_plan_id")
    private PricePlan pricePlan;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "KRW";

    // P: Pending(대기), C: Completed(완료), F: Failed(실패), R: Refunded(환불)
    @Column(nullable = false, length = 1)
    @Builder.Default
    private String status = "P";

    @Column(name = "order_name", length = 255)
    private String orderName;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "refund_amount", precision = 18, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "refund_reason", columnDefinition = "TEXT")
    private String refundReason;

    @OneToOne(mappedBy = "payment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private PaymentDetail paymentDetail;

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
}
