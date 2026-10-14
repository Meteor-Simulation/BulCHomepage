package com.bulc.homepage.payment.domain;

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

    /** 토스가 준 카드사 코드 원본({@code card.issuerCode}). cardCompany 는 이를 변환한 이름이다. */
    @Column(name = "card_issuer_code", length = 10)
    private String cardIssuerCode;

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

    @Column(name = "toss_status", length = 50)
    private String tossStatus;

    @Column(name = "toss_response_summary", columnDefinition = "TEXT")
    private String tossResponseSummary;

    /**
     * 웹훅 검증용 토스 secret (결제 승인 응답의 최상위 {@code secret}, 최대 50자).
     *
     * <p>가상계좌 입금 완료 웹훅 본문의 {@code secret} 과 대조한다. 값이 다르면 제3자가 보낸
     * 위조 요청이다. 가상계좌 결제에만 담기므로 카드 결제에서는 NULL 로 남는다 —
     * NULL 이면 대조를 건너뛰고 재조회 검증만 적용한다(과거 결제의 웹훅을 막아선 안 된다).
     */
    @Column(name = "toss_secret", length = 64)
    private String tossSecret;

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
