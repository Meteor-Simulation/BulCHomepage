package com.bulc.homepage.licensing.dto;

import com.bulc.homepage.licensing.domain.UsageCategory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record RedeemCampaignRequest(
        @NotBlank(message = "캠페인 이름은 필수입니다")
        String name,

        String description,

        @NotNull(message = "제품 ID는 필수입니다")
        UUID productId,

        @NotNull(message = "라이선스 플랜 ID는 필수입니다")
        UUID licensePlanId,

        UsageCategory usageCategory,

        Integer seatLimit,

        @Min(value = 1, message = "사용자별 한도는 1 이상이어야 합니다")
        int perUserLimit,

        Instant validFrom,

        Instant validUntil
) {
}
