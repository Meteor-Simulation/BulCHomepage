package com.bulc.homepage.licensing.dto;

import com.bulc.homepage.licensing.domain.UsageCategory;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 관리자용 라이선스 수동 발급 요청 DTO.
 *
 * 관리자가 사용자에게 직접 라이선스를 발급할 때 사용합니다.
 * Plan 기반으로 발급하며, 결제 없이 발급됩니다.
 */
public record AdminLicenseIssueRequest(
        @NotNull(message = "사용자 ID는 필수입니다")
        UUID userId,

        @NotNull(message = "플랜 ID는 필수입니다")
        UUID planId,

        UsageCategory usageCategory,

        String memo  // 관리자 메모 (발급 사유 등)
) {
    public UsageCategory usageCategoryOrDefault() {
        return usageCategory != null ? usageCategory : UsageCategory.COMMERCIAL;
    }
}
