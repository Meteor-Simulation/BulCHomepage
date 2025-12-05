package com.bulc.homepage.licensing.dto;

import com.bulc.homepage.licensing.domain.LicenseType;
import com.bulc.homepage.licensing.domain.OwnerType;
import com.bulc.homepage.licensing.domain.UsageCategory;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 라이선스 발급 요청 DTO.
 */
public record LicenseIssueRequest(
        @NotNull OwnerType ownerType,
        @NotNull UUID ownerId,
        @NotNull UUID productId,
        UUID planId,
        @NotNull LicenseType licenseType,
        UsageCategory usageCategory,
        Instant validFrom,
        Instant validUntil,
        Map<String, Object> policySnapshot,
        UUID sourceOrderId
) {
    public UsageCategory usageCategoryOrDefault() {
        return usageCategory != null ? usageCategory : UsageCategory.COMMERCIAL;
    }
}
