package com.bulc.homepage.licensing.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "redeem_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RedeemCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "code_hash", nullable = false, unique = true, length = 64)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "code_type", nullable = false, length = 10)
    private RedeemCodeType codeType;

    @Column(name = "max_redemptions", nullable = false)
    private int maxRedemptions;

    @Column(name = "current_redemptions", nullable = false)
    private int currentRedemptions;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    private RedeemCode(UUID campaignId, String codeHash, RedeemCodeType codeType,
                       int maxRedemptions, Instant expiresAt) {
        this.campaignId = campaignId;
        this.codeHash = codeHash;
        this.codeType = codeType != null ? codeType : RedeemCodeType.RANDOM;
        this.maxRedemptions = maxRedemptions > 0 ? maxRedemptions : 1;
        this.currentRedemptions = 0;
        this.active = true;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // === 도메인 메서드 ===

    public boolean isRedeemable() {
        if (!this.active) {
            return false;
        }
        if (this.expiresAt != null && Instant.now().isAfter(this.expiresAt)) {
            return false;
        }
        if (this.currentRedemptions >= this.maxRedemptions) {
            return false;
        }
        return true;
    }

    public void incrementRedemptions() {
        this.currentRedemptions++;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }
}
