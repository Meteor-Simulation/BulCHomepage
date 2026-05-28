package com.bulc.homepage.licensing.dto;

import com.bulc.homepage.licensing.domain.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 라이선스 응답 DTO.
 *
 * v1.1 변경: licenseKey 제거 (계정 기반 인증으로 전환)
 * - User API에서 licenseKey 노출 불필요
 * - 보안상 라이선스 키는 발급 시점에만 Billing 모듈에 전달
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
        Map<String, Object> policySnapshot,
        List<ActivationResponse> activations,
        Instant createdAt,
        Instant updatedAt
) {
    public static LicenseResponse from(License license) {
        LicenseStatus effectiveStatus = license.getStatus() == LicenseStatus.PENDING
                ? license.getStatus()
                : license.calculateEffectiveStatus(Instant.now());
        return new LicenseResponse(
                license.getId(),
                license.getOwnerType(),
                license.getOwnerId(),
                license.getProductId(),
                license.getPlanId(),
                license.getLicenseType(),
                license.getUsageCategory(),
                effectiveStatus,
                license.getIssuedAt(),
                license.getValidFrom(),
                license.getValidUntil(),
                license.getPolicySnapshot(),
                license.getActivations().stream().map(ActivationResponse::from).toList(),
                license.getCreatedAt(),
                license.getUpdatedAt()
        );
    }
}
