package com.bulc.homepage.licensing.domain;

/**
 * 라이선스 사용 용도/목적.
 * LicenseStatus(상태)와 직교 관계로, 독립적으로 조합 가능.
 */
public enum UsageCategory {
    COMMERCIAL,             // 상업적 사용
    RESEARCH_NON_COMMERCIAL,// 비영리 연구 목적
    EDUCATION,              // 교육 목적
    INTERNAL_EVAL           // 내부 평가/검토용
}
