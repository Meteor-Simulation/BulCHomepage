package com.bulc.homepage.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

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

    @Column(name = "user_email", length = 255)
    private String userEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_email", referencedColumnName = "email", insertable = false, updatable = false)
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
