package com.bulc.homepage.licensing.dto;

/**
 * v1.1.3: 다중 라이선스 선택 전략.
 *
 * 사용자가 동일 제품에 여러 라이선스를 보유한 경우 서버의 처리 방식을 결정합니다.
 */
public enum LicenseSelectionStrategy {
    /**
     * (기본값) 다중 라이선스 시 409 반환하여 클라이언트에서 선택하도록 함.
     * 클라이언트 UX를 위해 candidates 목록 제공.
     */
    FAIL_ON_MULTIPLE,

    /**
     * 서버가 자동으로 최적의 라이선스 선택.
     * 우선순위: ACTIVE > EXPIRED_GRACE > 최신 validUntil
     */
    AUTO_PICK_BEST,

    /**
     * 가장 최근 validUntil인 라이선스 자동 선택.
     */
    AUTO_PICK_LATEST
}
