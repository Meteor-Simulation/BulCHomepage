package com.bulc.homepage.payment.dto;

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

    /**
     * 결제 멱등키 — 클라이언트가 결제 시도마다 하나 만들어 보낸다(UUID 권장).
     *
     * <p>버튼이 두 번 눌려 요청이 두 번 와도 같은 키가 실려 오므로 서버가 두 번째를 거부한다.
     * 비어 있으면 서버가 사용자·요금제·시간창으로 대체 키를 만든다 — 캐시된 구버전 화면에서도
     * 보호가 끊기지 않아야 한다.
     */
    private String idempotencyKey;
}
