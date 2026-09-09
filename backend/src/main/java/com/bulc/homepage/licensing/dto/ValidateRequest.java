package com.bulc.homepage.licensing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

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
 * - licenseId 미지정: 서버가 자동으로 최적의 라이선스 선택 (v0.3.0)
 *   - 후보 0개: 404 LICENSE_NOT_FOUND_FOR_PRODUCT
 *   - 후보 1개 이상: 서버 자동 선택 (Two-Pass Algorithm)
 *
 * v0.3.0 변경:
 * - strategy 필드 deprecated - 서버가 항상 자동 선택
 * - 빈 슬롯 있으면 바로 활성화 (resolution: OK)
 * - stale 세션 있으면 자동 종료 후 활성화 (resolution: AUTO_RECOVERED)
 * - 모두 full이면 세션 목록 반환 (resolution: USER_ACTION_REQUIRED)
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
        String clientOs,

        // v1.1.1: 기기 표시 이름 (선택) - UX용
        // v1.2.0 (MDP-791): DB 컬럼(device_display_name VARCHAR 100) 방어 - DTO 검증 추가
        @Size(max = 100, message = "기기 표시 이름은 100자를 초과할 수 없습니다")
        String deviceDisplayName,

        // v1.2.0 (MDP-790 B5): 활성화 클라이언트 종류 (gui|cli, 선택).
        // GUI·CLI 좌석 공유 시 관측용. 미전송/미상 값은 관대 수용(null 저장).
        String clientKind,

        // v1.1.3: 다중 라이선스 선택 전략
        // v0.3.0: deprecated - 서버가 항상 자동 선택 (이 필드는 무시됨)
        @Deprecated
        LicenseSelectionStrategy strategy
) {
    /**
     * v1.2.0 이전 8-인자 하위호환 생성자 (clientKind=null).
     * 기존 호출부·테스트를 깨지 않기 위해 유지한다.
     */
    public ValidateRequest(String productCode, UUID productId, UUID licenseId,
                           String deviceFingerprint, String clientVersion, String clientOs,
                           String deviceDisplayName, LicenseSelectionStrategy strategy) {
        this(productCode, productId, licenseId, deviceFingerprint, clientVersion, clientOs,
                deviceDisplayName, null, strategy);
    }

    /**
     * 제품 식별자가 유효한지 검증.
     */
    public boolean hasProductIdentifier() {
        return productCode != null || productId != null;
    }

    /**
     * 전략 반환 (null이면 기본값 FAIL_ON_MULTIPLE).
     * v0.3.0: deprecated - 서버가 항상 자동 선택하므로 더 이상 사용하지 않음
     */
    @Deprecated
    public LicenseSelectionStrategy getEffectiveStrategy() {
        return strategy != null ? strategy : LicenseSelectionStrategy.FAIL_ON_MULTIPLE;
    }
}
