package com.bulc.homepage.licensing.dto;

import com.bulc.homepage.licensing.domain.RedeemCode;
import com.bulc.homepage.licensing.domain.RedeemCodeType;

import java.time.Instant;
import java.util.UUID;

public record RedeemCodeResponse(
        UUID id,
        UUID campaignId,
        RedeemCodeType codeType,
        int maxRedemptions,
        int currentRedemptions,
        boolean active,
        Instant expiresAt,
        Instant createdAt
) {
    public static RedeemCodeResponse fromEntity(RedeemCode code) {
        return new RedeemCodeResponse(
                code.getId(),
                code.getCampaignId(),
                code.getCodeType(),
                code.getMaxRedemptions(),
                code.getCurrentRedemptions(),
                code.isActive(),
                code.getExpiresAt(),
                code.getCreatedAt()
        );
    }
}
