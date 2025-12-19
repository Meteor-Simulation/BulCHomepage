package com.bulc.homepage.licensing.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * 라이선스 검증 요청 DTO (v1.1 계정 기반).
 *
 * 제품 식별:
 * - productCode 권장 (예: "BULC_EVAC") - 배포/운영에서 관리 용이
 * - productId도 지원 (하위 호환)
 * - 둘 다 없으면 400 에러
 *
 * 라이선스 선택:
 * - licenseId 지정: 해당 라이선스 사용 (소유자 검증)
 * - licenseId 미지정:
 *   - 후보 0개: 404 LICENSE_NOT_FOUND_FOR_PRODUCT
 *   - 후보 1개: 자동 선택
 *   - 후보 2개 이상: 409 LICENSE_SELECTION_REQUIRED + candidates 반환
 */
public record ValidateRequest(
        // 제품 식별 (둘 중 하나 필수)
        String productCode,      // 권장: "BULC_EVAC", "METEOR_PRO"
        UUID productId,          // 대안: UUID

        // 라이선스 선택 (선택적)
        UUID licenseId,          // 복수 라이선스 시 명시적 선택

        // 기기 정보 (필수)
        @NotBlank(message = "기기 fingerprint는 필수입니다")
        String deviceFingerprint,

        // 클라이언트 정보 (선택)
        String clientVersion,
        String clientOs
) {
    /**
     * 제품 식별자가 유효한지 검증.
     */
    public boolean hasProductIdentifier() {
        return productCode != null || productId != null;
    }
}
