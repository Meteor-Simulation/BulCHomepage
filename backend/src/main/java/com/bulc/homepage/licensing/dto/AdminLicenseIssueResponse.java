package com.bulc.homepage.licensing.dto;

import com.bulc.homepage.licensing.domain.License;
import com.bulc.homepage.licensing.domain.LicenseStatus;
import com.bulc.homepage.licensing.domain.LicenseType;

import java.time.Instant;
import java.util.UUID;

/**
 * 관리자용 라이선스 발급 응답 DTO.
 */
public record AdminLicenseIssueResponse(
        UUID id,
        String licenseKey,
        UUID userId,
        UUID productId,
        UUID planId,
        LicenseType licenseType,
        LicenseStatus status,
        Instant validFrom,
        Instant validUntil,
        Instant createdAt,
        String message
) {
    public static AdminLicenseIssueResponse from(License license) {
        return new AdminLicenseIssueResponse(
                license.getId(),
                license.getLicenseKey(),
                license.getOwnerId(),
                license.getProductId(),
                license.getPlanId(),
                license.getLicenseType(),
                license.getStatus(),
                license.getValidFrom(),
                license.getValidUntil(),
                license.getCreatedAt(),
                "라이선스가 성공적으로 발급되었습니다."
        );
    }
}
