package com.bulc.homepage.licensing.dto;

import com.bulc.homepage.licensing.domain.LicenseType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * 라이선스 플랜 생성/수정 요청 DTO.
 */
public record LicensePlanRequest(
        @NotNull(message = "제품 ID는 필수입니다")
        UUID productId,

        @NotBlank(message = "플랜 코드는 필수입니다")
        String code,

        @NotBlank(message = "플랜 이름은 필수입니다")
        String name,

        String description,

        @NotNull(message = "라이선스 타입은 필수입니다")
        LicenseType licenseType,

        @Min(value = 0, message = "유효기간은 0일 이상이어야 합니다")
        int durationDays,

        @Min(value = 0, message = "유예기간은 0일 이상이어야 합니다")
        int graceDays,

        @Min(value = 1, message = "최대 기기 수는 1개 이상이어야 합니다")
        int maxActivations,

        @Min(value = 1, message = "최대 동시 세션은 1개 이상이어야 합니다")
        int maxConcurrentSessions,

        @Min(value = 0, message = "오프라인 허용 일수는 0일 이상이어야 합니다")
        int allowOfflineDays,

        List<String> entitlements
) {
}
