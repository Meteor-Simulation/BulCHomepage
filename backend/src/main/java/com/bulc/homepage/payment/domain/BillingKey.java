package com.bulc.homepage.payment.domain;

import com.bulc.homepage.crypto.BillingKeyCryptoConverter;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "billing_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // 회원 엔티티로의 @ManyToOne 관계를 제거했다 (결제 모듈 분리).
    // 읽기 전용 매핑이었고 getUser() 를 쓰는 곳이 없었다. 유지하면 결제 모듈이 회원 엔티티를
    // 알아야 해서 따로 떼어낼 수 없다. 조회는 userId 기반이고 user_id 컬럼은 위 userId 가 매핑한다.

    @Convert(converter = BillingKeyCryptoConverter.class)
    @Column(name = "billing_key", nullable = false, length = 255)
    private String billingKey;

    @Column(name = "customer_key", nullable = false, length = 255)
    private String customerKey;

    @Column(name = "card_company", length = 50)
    private String cardCompany;

    /**
     * 토스가 준 카드사 코드 원본({@code card.issuerCode}, 예 "61").
     * cardCompany 는 이 코드를 {@link CardIssuer} 로 변환한 이름이다. 표에 없는 코드가 와서
     * 이름이 비어도 코드는 남겨 추적할 수 있게 둘을 함께 저장한다.
     */
    @Column(name = "card_issuer_code", length = 10)
    private String cardIssuerCode;

    @Column(name = "card_number", length = 20)
    private String cardNumber;

    @Column(name = "card_type", length = 20)
    private String cardType;

    @Column(name = "owner_type", length = 20)
    private String ownerType;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

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
     * 빌링키 비활성화
     */
    public void deactivate() {
        this.isActive = false;
        this.isDefault = false;
    }

    /**
     * 기본 결제 수단으로 설정
     */
    public void setAsDefault() {
        this.isDefault = true;
    }

    /**
     * 기본 결제 수단 해제
     */
    public void unsetDefault() {
        this.isDefault = false;
    }
}
