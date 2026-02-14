package com.bulc.homepage.licensing.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "redeem_user_campaign_counters",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "campaign_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RedeemUserCampaignCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "count", nullable = false)
    private int count;

    public RedeemUserCampaignCounter(UUID userId, UUID campaignId) {
        this.userId = userId;
        this.campaignId = campaignId;
        this.count = 0;
    }

    public void increment() {
        this.count++;
    }
}
