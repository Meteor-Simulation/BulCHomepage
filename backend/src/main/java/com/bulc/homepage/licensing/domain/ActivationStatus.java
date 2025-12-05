package com.bulc.homepage.licensing.domain;

/**
 * 기기 활성화 상태.
 */
public enum ActivationStatus {
    ACTIVE,         // 정상 사용 중 (최근 접속 기록 있음)
    STALE,          // 장기간 미접속 (계약은 유효)
    DEACTIVATED,    // 명시적 비활성화 (사용자/관리자에 의해)
    EXPIRED         // 라이선스 만료로 인한 비활성화
}
