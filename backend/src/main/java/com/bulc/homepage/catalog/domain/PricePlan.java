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

    /**
     * 자동갱신 시 청구할 금액. {@code null} 이면 {@link #price} 와 같다.
     *
     * <p>하드웨어가 포함된 요금제처럼 <b>첫 해에만 추가 대금이 붙는</b> 경우에만 채운다.
     * 예) 첫 결제 9,900,000(소프트웨어 600만 + 하드웨어 390만) / 갱신 6,000,000.
     *
     * <p>직접 읽지 말고 {@link #renewalAmount()} 를 쓴다 — 호출부마다 null 처리를 두면
     * 한 곳이 빠졌을 때 390만원이 더 청구되고, 그 사실은 1년 뒤에야 드러난다.
     */
    @Column(name = "renewal_price", precision = 18, scale = 2)
    private BigDecimal renewalPrice;

    /** 갱신 때 청구할 금액. 따로 정해두지 않았으면 첫 결제가와 같다. */
    public BigDecimal renewalAmount() {
        return renewalPrice != null ? renewalPrice : price;
    }

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
