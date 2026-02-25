package com.bulc.homepage.licensing.dto;

import com.bulc.homepage.licensing.domain.RedeemCodeType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record RedeemCodeGenerateRequest(
        @NotNull(message = "캠페인 ID는 필수입니다")
        UUID campaignId,

        @NotNull(message = "코드 타입은 필수입니다")
        RedeemCodeType codeType,

        String customCode,

        @Min(value = 1, message = "생성 수량은 1 이상이어야 합니다")
        @Max(value = 1000, message = "한 번에 최대 1000개까지 생성 가능합니다")
        int count,

        @Min(value = 1, message = "최대 사용 횟수는 1 이상이어야 합니다")
        int maxRedemptions,

        Instant expiresAt
) {
}
