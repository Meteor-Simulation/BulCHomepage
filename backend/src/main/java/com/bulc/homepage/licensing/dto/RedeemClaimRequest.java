package com.bulc.homepage.licensing.dto;

import jakarta.validation.constraints.NotBlank;

public record RedeemClaimRequest(
        @NotBlank(message = "리딤 코드는 필수입니다")
        String code
) {
}
