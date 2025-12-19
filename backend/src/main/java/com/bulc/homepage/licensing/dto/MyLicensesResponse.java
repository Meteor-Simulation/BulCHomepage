package com.bulc.homepage.licensing.dto;

import java.util.List;

/**
 * 내 라이선스 목록 응답 래퍼 DTO.
 * v1.1에서 추가됨.
 */
public record MyLicensesResponse(
        List<MyLicenseView> licenses
) {
    public static MyLicensesResponse of(List<MyLicenseView> licenses) {
        return new MyLicensesResponse(licenses);
    }
}
