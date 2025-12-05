package com.bulc.homepage.licensing.domain;

/**
 * 라이선스 소유자 유형.
 * v1에서는 USER만 지원하며, 향후 ORG(조직) 추가 예정.
 */
public enum OwnerType {
    USER,   // 개인 사용자 (v1)
    ORG     // 조직/교육기관 (v2 예정)
}
