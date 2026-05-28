package com.bulc.homepage.licensing.query.view;

import com.bulc.homepage.licensing.domain.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record LicenseSummaryView(
        UUID id,
        String licenseKey,
        LicenseStatus status,
        LicenseType licenseType,
        UsageCategory usageCategory,
        UUID ownerId,
        OwnerType ownerType,
        UUID productId,
        UUID planId,
        Instant validFrom,
        Instant validUntil,
        Instant startsAt,
        int maxActivations,
        int usedActivations
) {
    public static LicenseSummaryView from(License license) {
        int maxActivations = getMaxActivations(license.getPolicySnapshot());
        int usedActivations = (int) license.getActivations().stream()
                .filter(a -> a.getStatus() == ActivationStatus.ACTIVE)
                .count();

        Instant now = Instant.now();
        LicenseStatus effectiveStatus = license.calculateEffectiveStatus(now);
        Instant startsAt = effectiveStatus == LicenseStatus.PENDING ? license.getValidFrom() : null;

        return new LicenseSummaryView(
                license.getId(),
                license.getLicenseKey(),
                effectiveStatus,
                license.getLicenseType(),
                license.getUsageCategory(),
                license.getOwnerId(),
                license.getOwnerType(),
                license.getProductId(),
                license.getPlanId(),
                license.getValidFrom(),
                license.getValidUntil(),
                startsAt,
                maxActivations,
                usedActivations
        );
    }

    private static int getMaxActivations(Map<String, Object> policySnapshot) {
        if (policySnapshot == null) {
            return 1;
        }
        Object value = policySnapshot.get("maxActivations");
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 1;
    }
}
