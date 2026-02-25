package com.bulc.homepage.licensing.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "redeem_campaigns")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RedeemCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "license_plan_id", nullable = false)
    private UUID licensePlanId;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_category", nullable = false, length = 30)
    private UsageCategory usageCategory;

    @Column(name = "seat_limit")
    private Integer seatLimit;

    @Column(name = "seats_used", nullable = false)
    private int seatsUsed;

    @Column(name = "per_user_limit", nullable = false)
    private int perUserLimit;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RedeemCampaignStatus status;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    private RedeemCampaign(String name, String description, UUID productId, UUID licensePlanId,
                           UsageCategory usageCategory, Integer seatLimit, int perUserLimit,
                           Instant validFrom, Instant validUntil, UUID createdBy) {
        this.name = name;
        this.description = description;
        this.productId = productId;
        this.licensePlanId = licensePlanId;
        this.usageCategory = usageCategory != null ? usageCategory : UsageCategory.COMMERCIAL;
        this.seatLimit = seatLimit;
        this.seatsUsed = 0;
        this.perUserLimit = perUserLimit > 0 ? perUserLimit : 1;
        this.status = RedeemCampaignStatus.ACTIVE;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // === 도메인 메서드 ===

    public boolean isAvailable() {
        if (this.status != RedeemCampaignStatus.ACTIVE) {
            return false;
        }
        Instant now = Instant.now();
        if (this.validFrom != null && now.isBefore(this.validFrom)) {
            return false;
        }
        if (this.validUntil != null && now.isAfter(this.validUntil)) {
            return false;
        }
        if (this.seatLimit != null && this.seatsUsed >= this.seatLimit) {
            return false;
        }
        return true;
    }

    public void pause() {
        if (this.status != RedeemCampaignStatus.ACTIVE) {
            throw new IllegalStateException("ACTIVE 상태에서만 일시정지할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = RedeemCampaignStatus.PAUSED;
        this.updatedAt = Instant.now();
    }

    public void end() {
        if (this.status == RedeemCampaignStatus.ENDED) {
            throw new IllegalStateException("이미 종료된 캠페인입니다.");
        }
        this.status = RedeemCampaignStatus.ENDED;
        this.updatedAt = Instant.now();
    }

    public void resume() {
        if (this.status != RedeemCampaignStatus.PAUSED) {
            throw new IllegalStateException("PAUSED 상태에서만 재개할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = RedeemCampaignStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void update(String name, String description, UsageCategory usageCategory,
                       Integer seatLimit, int perUserLimit, Instant validFrom, Instant validUntil) {
        this.name = name;
        this.description = description;
        this.usageCategory = usageCategory;
        this.seatLimit = seatLimit;
        this.perUserLimit = perUserLimit;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.updatedAt = Instant.now();
    }

    public void incrementSeatsUsed() {
        this.seatsUsed++;
        this.updatedAt = Instant.now();
    }
}
