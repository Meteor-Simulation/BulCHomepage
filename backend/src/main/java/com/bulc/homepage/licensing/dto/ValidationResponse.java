package com.bulc.homepage.licensing.dto;

import com.bulc.homepage.licensing.domain.LicenseStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 라이선스 검증 응답 DTO.
 */
public record ValidationResponse(
        boolean valid,
        UUID licenseId,
        LicenseStatus status,
        Instant validUntil,
        List<String> entitlements,
        String offlineToken,
        Instant offlineTokenExpiresAt,
        String errorCode,
        String errorMessage
) {
    public static ValidationResponse success(UUID licenseId, LicenseStatus status, Instant validUntil,
                                              List<String> entitlements, String offlineToken, Instant offlineTokenExpiresAt) {
        return new ValidationResponse(true, licenseId, status, validUntil, entitlements,
                offlineToken, offlineTokenExpiresAt, null, null);
    }

    public static ValidationResponse failure(String errorCode, String errorMessage) {
        return new ValidationResponse(false, null, null, null, null, null, null, errorCode, errorMessage);
    }
}
