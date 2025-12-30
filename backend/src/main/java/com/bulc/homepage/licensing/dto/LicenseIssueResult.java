package com.bulc.homepage.licensing.dto;

import com.bulc.homepage.licensing.domain.License;

import java.time.Instant;
import java.util.UUID;

/**
 * 라이선스 발급 결과 DTO.
 * Billing 모듈 내부에서 결제 완료 시 사용.
 * licenseKey를 포함하여 결제 성공 화면에 표시할 수 있도록 함.
 */
public record LicenseIssueResult(
        UUID id,
        String licenseKey,
        Instant validUntil
) {
    public static LicenseIssueResult from(License license) {
        return new LicenseIssueResult(
                license.getId(),
                license.getLicenseKey(),
                license.getValidUntil()
        );
    }
}
