package com.bulc.homepage.licensing.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 라이선스 플랜별 활성화 기능(Entitlement).
 * 플랜이 어떤 기능을 제공하는지 정의.
 */
@Entity
@Table(name = "license_plan_entitlements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LicensePlanEntitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private LicensePlan plan;

    @Column(name = "entitlement_key", nullable = false, length = 100)
    private String entitlementKey;

    public LicensePlanEntitlement(LicensePlan plan, String entitlementKey) {
        this.plan = plan;
        this.entitlementKey = entitlementKey;
    }
}
