package com.bulc.homepage.licensing.dto;

import com.bulc.homepage.licensing.domain.LicenseStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 라이선스 검증 응답 DTO.
 *
 * 성공 시: valid=true, licenseId/status/validUntil/entitlements/offlineToken 제공
 * 실패 시: valid=false, errorCode/errorMessage 제공
 * 선택 필요 시: valid=false, errorCode=LICENSE_SELECTION_REQUIRED, candidates 제공
 */
public record ValidationResponse(
        boolean valid,
        UUID licenseId,
        LicenseStatus status,
        Instant validUntil,
        List<String> entitlements,
        String offlineToken,
        Instant offlineTokenExpiresAt,
        Instant serverTime,           // 클라이언트 시간 조작 방어용
        String errorCode,
        String errorMessage,
        List<LicenseCandidate> candidates  // 복수 라이선스 선택 시
) {
    /**
     * 복수 라이선스 선택 시 후보 정보.
     * licenseKey를 노출하지 않고 사용자가 선택할 수 있는 정보만 제공.
     */
    public record LicenseCandidate(
            UUID licenseId,
            String planName,           // "Pro 연간 구독"
            String licenseType,        // "SUBSCRIPTION", "PERPETUAL"
            LicenseStatus status,      // ACTIVE, EXPIRED_GRACE
            Instant validUntil,
            String ownerScope,         // "개인" / "조직: ABC Corp"
            int activeDevices,         // 현재 활성화된 기기 수
            int maxDevices,            // 최대 기기 수
            String label               // 사용자 지정 라벨 (옵션)
    ) {}

    public static ValidationResponse success(UUID licenseId, LicenseStatus status, Instant validUntil,
                                              List<String> entitlements, String offlineToken, Instant offlineTokenExpiresAt) {
        return new ValidationResponse(true, licenseId, status, validUntil, entitlements,
                offlineToken, offlineTokenExpiresAt, Instant.now(), null, null, null);
    }

    public static ValidationResponse failure(String errorCode, String errorMessage) {
        return new ValidationResponse(false, null, null, null, null, null, null,
                Instant.now(), errorCode, errorMessage, null);
    }

    /**
     * 복수 라이선스 선택 필요 시 응답.
     */
    public static ValidationResponse selectionRequired(List<LicenseCandidate> candidates) {
        return new ValidationResponse(false, null, null, null, null, null, null,
                Instant.now(), "LICENSE_SELECTION_REQUIRED",
                "복수의 라이선스가 존재합니다. licenseId를 지정해주세요", candidates);
    }
}
