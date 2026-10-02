package com.bulc.homepage.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentConfirmRequest {

    @NotBlank(message = "paymentKey는 필수입니다")
    private String paymentKey;

    @NotBlank(message = "orderId는 필수입니다")
    private String orderId;

    @NotNull(message = "amount는 필수입니다")
    @Positive(message = "amount는 양수여야 합니다")
    private Integer amount;

    @NotNull(message = "pricePlanId는 필수입니다")
    private Long pricePlanId;

    /**
     * 적용한 쿠폰 코드 (선택, MDP-748).
     *
     * <p>이 값은 "어떤 쿠폰을 썼는지" 만 알려준다. <b>할인액은 서버가 다시 계산한다</b> —
     * 클라이언트가 보낸 할인액을 믿으면 임의 금액으로 결제할 수 있다.
     *
     * <p>이 필드가 없던 동안은 서버가 쿠폰 적용 사실을 알 수 없어, 할인가로 들어온 결제를
     * 정가와 대조해 전부 거부했다(결제 실패). 그래서 쿠폰 기능이 사실상 막혀 있었다.
     */
    private String couponCode;
}
