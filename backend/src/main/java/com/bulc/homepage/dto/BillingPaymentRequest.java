package com.bulc.homepage.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 등록 카드(빌링키)로 즉시 결제 요청.
 * - pricePlanId: 구매할 요금제
 * - billingKeyId: 결제에 사용할 등록 카드(빌링키)
 * - enableAutoRenew: 구독형이면 true (첫 결제 후 자동갱신 ON). 단건(영구)이면 false.
 */
@Getter
@Setter
public class BillingPaymentRequest {

    @NotNull(message = "pricePlanId는 필수입니다")
    private Long pricePlanId;

    @NotNull(message = "billingKeyId는 필수입니다")
    private Long billingKeyId;

    private boolean enableAutoRenew = false;
}
