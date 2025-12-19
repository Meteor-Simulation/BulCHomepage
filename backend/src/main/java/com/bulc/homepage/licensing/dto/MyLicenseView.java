package com.bulc.homepage.licensing.dto;

import com.bulc.homepage.licensing.domain.License;
import com.bulc.homepage.licensing.domain.LicenseStatus;
import com.bulc.homepage.licensing.domain.LicenseType;
import com.bulc.homepage.licensing.domain.ActivationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 내 라이선스 목록 조회 응답 DTO.
 * v1.1에서 추가됨.
 */
public record MyLicenseView(
        UUID id,
        UUID productId,
        String productName,
        String planName,
        LicenseType licenseType,
        LicenseStatus status,
        Instant validFrom,
        Instant validUntil,
        List<String> entitlements,
        int usedActivations,
        int maxActivations
) {
    @SuppressWarnings("unchecked")
    public static MyLicenseView from(License license) {
        List<String> entitlements = List.of();
        if (license.getPolicySnapshot() != null && license.getPolicySnapshot().containsKey("entitlements")) {
            Object ent = license.getPolicySnapshot().get("entitlements");
            if (ent instanceof List<?>) {
                entitlements = (List<String>) ent;
            }
        }

        int usedActivations = (int) license.getActivations().stream()
                .filter(a -> a.getStatus() == ActivationStatus.ACTIVE || a.getStatus() == ActivationStatus.STALE)
                .count();

        return new MyLicenseView(
                license.getId(),
                license.getProductId(),
                null, // productName은 Product 도메인에서 조회 필요
                null, // planName은 Plan 도메인에서 조회 필요
                license.getLicenseType(),
                license.getStatus(),
                license.getValidFrom(),
                license.getValidUntil(),
                entitlements,
                usedActivations,
                license.getMaxActivations()
        );
    }
}
