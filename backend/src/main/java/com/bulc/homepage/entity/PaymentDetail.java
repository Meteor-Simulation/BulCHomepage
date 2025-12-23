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
