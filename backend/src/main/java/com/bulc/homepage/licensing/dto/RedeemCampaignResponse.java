package com.bulc.homepage.licensing.dto;

import com.bulc.homepage.licensing.domain.RedeemCampaign;
import com.bulc.homepage.licensing.domain.RedeemCampaignStatus;
import com.bulc.homepage.licensing.domain.UsageCategory;

import java.time.Instant;
import java.util.UUID;

public record RedeemCampaignResponse(
        UUID id,
        String name,
        String description,
        UUID productId,
        String productName,
        UUID licensePlanId,
        String planName,
        UsageCategory usageCategory,
        Integer seatLimit,
        int seatsUsed,
        int perUserLimit,
        RedeemCampaignStatus status,
        Instant validFrom,
        Instant validUntil,
        UUID createdBy,
        long codeCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static RedeemCampaignResponse fromEntity(RedeemCampaign campaign,
                                                     String productName,
                                                     String planName,
                                                     long codeCount) {
        return new RedeemCampaignResponse(
                campaign.getId(),
                campaign.getName(),
                campaign.getDescription(),
                campaign.getProductId(),
                productName,
                campaign.getLicensePlanId(),
                planName,
                campaign.getUsageCategory(),
                campaign.getSeatLimit(),
                campaign.getSeatsUsed(),
                campaign.getPerUserLimit(),
                campaign.getStatus(),
                campaign.getValidFrom(),
                campaign.getValidUntil(),
                campaign.getCreatedBy(),
                codeCount,
                campaign.getCreatedAt(),
                campaign.getUpdatedAt()
        );
    }
}
