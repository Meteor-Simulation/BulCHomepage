package com.bulc.homepage.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentDetail {

    @Id
    @Column(name = "payment_id")
    private Long paymentId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "payment_provider", length = 50)
    private String paymentProvider;

    // 토스페이먼츠 주문 ID
    @Column(name = "order_id", length = 100)
    private String orderId;

    // 토스페이먼츠 결제 키
    @Column(name = "payment_key", length = 255)
    private String paymentKey;

    // 카드 결제 상세 정보
    @Column(name = "card_company", length = 50)
    private String cardCompany;

    @Column(name = "card_number", length = 50)
    private String cardNumber;

    @Column(name = "installment_months")
    private Integer installmentMonths;

    @Column(name = "approve_no", length = 50)
    private String approveNo;

    // 간편결제 제공자 (토스페이, 네이버페이, 카카오페이 등)
    @Column(name = "easy_pay_provider", length = 50)
    private String easyPayProvider;

    // 가상계좌/계좌이체 상세 정보
    @Column(name = "bank_code", length = 20)
    private String bankCode;

    @Column(name = "bank_name", length = 50)
    private String bankName;

    @Column(name = "account_number", length = 50)
    private String accountNumber;

    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Column(name = "depositor_name", length = 100)
    private String depositorName;

    @Column(name = "settlement_status", length = 20)
    private String settlementStatus;

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
