package com.bulc.homepage.licensing.dto;

import com.bulc.homepage.licensing.domain.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 라이선스 응답 DTO.
 */
public record LicenseResponse(
        UUID id,
        OwnerType ownerType,
        UUID ownerId,
        UUID productId,
        UUID planId,
        LicenseType licenseType,
        UsageCategory usageCategory,
        LicenseStatus status,
        Instant issuedAt,
        Instant validFrom,
        Instant validUntil,
        String licenseKey,
        Map<String, Object> policySnapshot,
        List<ActivationResponse> activations,
        Instant createdAt,
        Instant updatedAt
) {
    public static LicenseResponse from(License license) {
        return new LicenseResponse(
                license.getId(),
                license.getOwnerType(),
                license.getOwnerId(),
                license.getProductId(),
                license.getPlanId(),
                license.getLicenseType(),
                license.getUsageCategory(),
                license.getStatus(),
                license.getIssuedAt(),
                license.getValidFrom(),
                license.getValidUntil(),
                license.getLicenseKey(),
                license.getPolicySnapshot(),
                license.getActivations().stream().map(ActivationResponse::from).toList(),
                license.getCreatedAt(),
                license.getUpdatedAt()
        );
    }
}
