package com.bulc.homepage.catalog.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "price_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // v1.2.0 (MDP-791): products.code 폭 확장(3→32)에 맞춰 FK 컬럼 동시 확장.
    @Column(name = "product_code", nullable = false, length = 32)
    private String productCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_code", referencedColumnName = "code", insertable = false, updatable = false)
    private Product product;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String description;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "KRW";

    @Column(name = "license_plan_id")
    private UUID licensePlanId;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * 내부 전용 요금제 — 매니저 이상(roles_code 000·001)에게만 노출되고 그 역할만 결제할 수 있다.
     *
     * <p>{@code isActive} 와 축이 다르다. 비활성 요금제는 결제 자체가 막혀 테스트에 쓸 수 없으므로,
     * "살 수는 있지만 아무에게나 보이지는 않는" 상태를 따로 뒀다 (소액 결제 점검용).
     *   <ul>
     *     <li>{@code isActive}   = 판매 가능 여부</li>
     *     <li>{@code isInternal} = 노출·결제 대상 제한</li>
     *   </ul>
     */
    @Column(name = "is_internal", nullable = false)
    @Builder.Default
    private Boolean isInternal = false;

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
