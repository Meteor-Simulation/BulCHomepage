package com.bulc.homepage.licensing.dto;

import com.bulc.homepage.licensing.domain.LicensePlan;
import com.bulc.homepage.licensing.domain.LicenseType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 라이선스 플랜 응답 DTO.
 */
public record LicensePlanResponse(
        UUID id,
        UUID productId,
        String code,
        String name,
        String description,
        LicenseType licenseType,
        int durationDays,
        int graceDays,
        int maxActivations,
        int maxConcurrentSessions,
        int allowOfflineDays,
        boolean active,
        boolean deleted,
        List<String> entitlements,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * 엔티티를 DTO로 변환.
     */
    public static LicensePlanResponse fromEntity(LicensePlan plan) {
        return new LicensePlanResponse(
                plan.getId(),
                plan.getProductId(),
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                plan.getLicenseType(),
                plan.getDurationDays(),
                plan.getGraceDays(),
                plan.getMaxActivations(),
                plan.getMaxConcurrentSessions(),
                plan.getAllowOfflineDays(),
                plan.isActive(),
                plan.isDeleted(),
                new ArrayList<>(plan.getEntitlementKeys()),
                plan.getCreatedAt(),
                plan.getUpdatedAt()
        );
    }
}
