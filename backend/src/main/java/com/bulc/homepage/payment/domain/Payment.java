package com.bulc.homepage.payment.domain;

import com.bulc.homepage.entity.User;
import com.bulc.homepage.catalog.domain.PricePlan;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

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

    @Column(name = "user_id")
    private UUID userId;

    /**
     * 회원 관계. AdminController 가 결제 목록에 이름·이메일을 표시할 때 쓴다(4곳).
     * 회원 모듈이 서는 마지막 단계에 계약 경유로 바꾼다 — 그때까지의 부채다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    // 결제 시점 이메일 스냅샷 (FK 아님)
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

    /**
     * 결제 멱등키 — 같은 키로 두 번 요청해도 한 번만 승인된다.
     *
     * <p>DB 에 유니크 제약이 걸려 있다. 동시 요청 둘이 와도 INSERT 단계에서 하나가 지므로
     * 진 쪽은 토스를 부르지 못한다. "검사 후 실행" 이 아니라 "예약에 성공한 쪽만 실행" 이다.
     * 종전의 조회 기반 중복 차단은 결제 행이 토스 승인 뒤에 저장되는 탓에 정작 막아야 할
     * 7~9초 구간이 비어 있었다 (2026-10-07 · 10-12 이중 청구).
     *
     * <p>같은 값을 토스 {@code Idempotency-Key} 헤더로도 보내 2중으로 막는다.
     */
    @Column(name = "idempotency_key", length = 300)
    private String idempotencyKey;

    @Column(name = "fail_reason", columnDefinition = "TEXT")
    private String failReason;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    /**
     * 적용된 쿠폰 코드 (MDP-748). 결제 시점 스냅샷이며 FK 가 아니다 —
     * 프로모션이 나중에 삭제·수정돼도 결제 사실은 남아야 한다(회계 자료).
     */
    @Column(name = "promotion_code", length = 50)
    private String promotionCode;

    /**
     * 서버가 산정한 할인액 (MDP-748). {@code amount = 정가 - discountAmount} 다.
     * {@code amount} 에는 실제 청구액이 들어가므로, 정가를 복원하려면 이 값이 필요하다.
     */
    @Column(name = "discount_amount", precision = 18, scale = 2)
    private BigDecimal discountAmount;

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
