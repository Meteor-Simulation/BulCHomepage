package com.bulc.homepage.licensing.domain;

/**
 * 라이선스 상태.
 */
public enum LicenseStatus {
    PENDING,        // 발급 대기 중
    ACTIVE,         // 정상 사용 가능
    EXPIRED_GRACE,  // 만료됨 (유예 기간 중)
    EXPIRED_HARD,   // 완전 만료 (사용 불가)
    SUSPENDED,      // 일시 정지 (관리자에 의해)
    REVOKED         // 회수됨 (환불 등)
}
