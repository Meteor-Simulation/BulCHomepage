package com.bulc.homepage.licensing.query.view;

import com.bulc.homepage.licensing.domain.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LicenseDetailView(
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
        PolicySnapshotView policySnapshot,
        List<ActivationView> activations,
        Instant createdAt,
        Instant updatedAt
) {
    public static LicenseDetailView from(License license) {
        List<ActivationView> activationViews = license.getActivations().stream()
                .map(ActivationView::from)
                .toList();

        return new LicenseDetailView(
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
                PolicySnapshotView.from(license.getPolicySnapshot()),
                activationViews,
                license.getCreatedAt(),
                license.getUpdatedAt()
        );
    }
}
