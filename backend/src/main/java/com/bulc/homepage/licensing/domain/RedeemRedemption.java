package com.bulc.homepage.licensing.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "redeem_redemptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RedeemRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code_id", nullable = false)
    private UUID codeId;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "license_id")
    private UUID licenseId;

    @Column(name = "redeemed_at", nullable = false)
    private Instant redeemedAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Builder
    private RedeemRedemption(UUID codeId, UUID campaignId, UUID userId, UUID licenseId,
                             String ipAddress, String userAgent) {
        this.codeId = codeId;
        this.campaignId = campaignId;
        this.userId = userId;
        this.licenseId = licenseId;
        this.redeemedAt = Instant.now();
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }
}
