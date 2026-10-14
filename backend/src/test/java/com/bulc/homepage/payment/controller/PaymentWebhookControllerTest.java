package com.bulc.homepage.payment.controller;

import com.bulc.homepage.payment.service.PaymentWebhookService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 웹훅 결과 → HTTP 상태 매핑.
 *
 * <p>토스는 2xx 가 아니면 최대 7회 재시도한다. 그래서 이 매핑이 곧 재시도 정책이다 —
 * "지금은 확인할 수 없다" 에 200 을 주면 입금 통보를 영구히 잃고, "처리할 것이 없다" 에
 * 5xx 를 주면 쓸데없는 재시도를 7번 받는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("웹훅 응답 코드")
class PaymentWebhookControllerTest {

    @Mock private PaymentWebhookService paymentWebhookService;

    @InjectMocks private PaymentWebhookController controller;

    private int statusFor(PaymentWebhookService.Result result) {
        when(paymentWebhookService.handle("{}")).thenReturn(result);
        return controller.handleWebhook("{}").getStatusCode().value();
    }

    @Test
    @DisplayName("처리했거나 처리할 것이 없으면 200 — 재시도시키지 않는다")
    void okWhenNothingToRetry() {
        assertThat(statusFor(PaymentWebhookService.Result.PROCESSED)).isEqualTo(200);
        assertThat(statusFor(PaymentWebhookService.Result.IGNORED)).isEqualTo(200);
    }

    @Test
    @DisplayName("secret 불일치는 403")
    void forbiddenWhenRejected() {
        assertThat(statusFor(PaymentWebhookService.Result.REJECTED)).isEqualTo(403);
    }

    @Test
    @DisplayName("일시적 실패는 5xx — 토스가 다시 보내야 한다")
    void serverErrorWhenRetryNeeded() {
        assertThat(statusFor(PaymentWebhookService.Result.RETRY)).isEqualTo(503);
    }

    @Test
    @DisplayName("Authorization 헤더를 받지 않는다 — 토스가 보내지 않는 헤더를 요구해 전부 403 이던 결함")
    void doesNotRequireAuthorizationHeader() {
        // 헤더를 선택적으로 받기만 해도 "없으면 거부" 로 되돌아갈 여지가 생긴다.
        // 파라미터가 본문 하나뿐이라는 사실로 그 여지를 없앤다.
        Method handler = java.util.Arrays.stream(PaymentWebhookController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("handleWebhook"))
                .findFirst()
                .orElseThrow();

        assertThat(handler.getParameterCount()).isEqualTo(1);
        assertThat(java.util.Arrays.stream(handler.getParameters())
                .anyMatch(p -> p.isAnnotationPresent(org.springframework.web.bind.annotation.RequestHeader.class)))
                .isFalse();
    }
}
