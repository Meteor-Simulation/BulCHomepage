package com.bulc.homepage.payment.service;

import com.bulc.homepage.payment.domain.PaymentDetail;
import com.bulc.homepage.payment.repository.PaymentDetailRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 토스 웹훅 해석·검증.
 *
 * <p>테스트가 없어서 두 결함을 동시에 놓쳤다. 하나는 토스가 보내지 않는 {@code Authorization}
 * 헤더를 요구해 정상 웹훅을 전부 403 으로 거부한 것, 다른 하나는 가상계좌 입금 웹훅의 본문이
 * {@code eventType}·{@code data}·{@code paymentKey} 가 없는 평면 구조인데 그걸 모르고
 * {@code data.paymentKey} 를 꺼내 아무 분기에도 걸리지 않은 것이다. 두 번째는 첫 번째를 고쳐도
 * 남아 있어서, 403 만 걷어내면 끝난 것처럼 보이지만 입금은 여전히 반영되지 않는 상태였다.
 *
 * <p>그래서 이 테스트의 핵심은 <b>실제 토스 본문 그대로</b>를 넣어 보는 것이다. 아래 JSON 은
 * 토스 웹훅 이벤트 문서의 예시 형태를 따른다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("토스 웹훅 처리")
class PaymentWebhookServiceTest {

    private static final String ORDER_ID = "BULC-20261014-0001";
    private static final String PAYMENT_KEY = "pk_virtual_account_test";
    private static final String SECRET = "ps_secret_abcdef0123456789";

    @Mock private PaymentDetailRepository paymentDetailRepository;
    @Mock private PaymentService paymentService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private PaymentWebhookService service;

    /** 가상계좌 입금 완료 웹훅 — 평면 구조, paymentKey 가 없다 */
    private static String depositCallback(String secret) {
        return """
            {
              "createdAt": "2026-10-14T13:20:11.000000",
              "secret": "%s",
              "status": "DONE",
              "transactionKey": "tx_9f8e7d6c",
              "orderId": "%s"
            }
            """.formatted(secret, ORDER_ID);
    }

    /** 결제 상태 변경 웹훅 — eventType + data(Payment 객체) */
    private static String paymentStatusChanged(String status) {
        return """
            {
              "eventType": "PAYMENT_STATUS_CHANGED",
              "createdAt": "2026-10-14T13:20:11.000000",
              "data": {
                "paymentKey": "%s",
                "orderId": "%s",
                "status": "%s",
                "secret": "%s",
                "method": "가상계좌"
              }
            }
            """.formatted(PAYMENT_KEY, ORDER_ID, status, SECRET);
    }

    private void givenStoredPayment(String storedSecret) {
        PaymentDetail detail = PaymentDetail.builder()
                .orderId(ORDER_ID)
                .paymentKey(PAYMENT_KEY)
                .tossSecret(storedSecret)
                .build();
        when(paymentDetailRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(detail));
    }

    @Nested
    @DisplayName("본문 구조")
    class BodyShape {

        @Test
        @DisplayName("가상계좌 입금 웹훅(평면·paymentKey 없음)을 orderId 로 찾아 처리한다")
        void handlesFlatDepositCallback() {
            givenStoredPayment(SECRET);
            when(paymentService.verifyPaymentStatus(PAYMENT_KEY)).thenReturn("DONE");

            var result = service.handle(depositCallback(SECRET));

            assertThat(result).isEqualTo(PaymentWebhookService.Result.PROCESSED);
            // 본문에 paymentKey 가 없으므로 저장된 값을 써야 한다 — 종전 코드는 여기서 멈췄다
            verify(paymentService).handlePaymentComplete(PAYMENT_KEY, ORDER_ID);
        }

        @Test
        @DisplayName("결제 상태 변경 웹훅은 data 안의 paymentKey 를 쓴다")
        void handlesNestedStatusChanged() {
            givenStoredPayment(SECRET);
            when(paymentService.verifyPaymentStatus(PAYMENT_KEY)).thenReturn("DONE");

            var result = service.handle(paymentStatusChanged("DONE"));

            assertThat(result).isEqualTo(PaymentWebhookService.Result.PROCESSED);
            verify(paymentService).handlePaymentComplete(PAYMENT_KEY, ORDER_ID);
        }

        @Test
        @DisplayName("관심 없는 이벤트는 그냥 넘긴다")
        void ignoresOtherEvents() {
            var result = service.handle("""
                {"eventType":"PAYOUT_CHANGED","createdAt":"2026-10-14T13:20:11.000000","data":{}}
                """);

            assertThat(result).isEqualTo(PaymentWebhookService.Result.IGNORED);
            verify(paymentService, never()).handlePaymentComplete(anyString(), anyString());
        }

        @Test
        @DisplayName("JSON 이 아닌 본문은 재시도를 유발하지 않는다")
        void ignoresMalformedBody() {
            var result = service.handle("not json at all");

            assertThat(result).isEqualTo(PaymentWebhookService.Result.IGNORED);
        }
    }

    @Nested
    @DisplayName("secret 검증")
    class SecretVerification {

        @Test
        @DisplayName("secret 이 다르면 거부한다")
        void rejectsMismatchedSecret() {
            givenStoredPayment(SECRET);

            var result = service.handle(depositCallback("ps_forged_value"));

            assertThat(result).isEqualTo(PaymentWebhookService.Result.REJECTED);
            // 재조회조차 하지 않는다 — 1차 관문에서 끊는 것이 이 대조의 목적이다
            verify(paymentService, never()).verifyPaymentStatus(anyString());
            verify(paymentService, never()).handlePaymentComplete(anyString(), anyString());
        }

        @Test
        @DisplayName("저장된 secret 이 없으면(카드·과거 결제) 대조를 건너뛰고 재조회로 판단한다")
        void skipsComparisonWhenNotStored() {
            givenStoredPayment(null);
            when(paymentService.verifyPaymentStatus(PAYMENT_KEY)).thenReturn("DONE");

            var result = service.handle(depositCallback(SECRET));

            // 과거 결제의 웹훅을 막아서는 안 된다
            assertThat(result).isEqualTo(PaymentWebhookService.Result.PROCESSED);
            verify(paymentService).handlePaymentComplete(PAYMENT_KEY, ORDER_ID);
        }

        @Test
        @DisplayName("모르는 주문은 거부가 아니라 무시한다")
        void ignoresUnknownOrder() {
            when(paymentDetailRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

            var result = service.handle(depositCallback(SECRET));

            // 재시도해도 생기지 않으므로 403 이 아니라 200 이어야 한다
            assertThat(result).isEqualTo(PaymentWebhookService.Result.IGNORED);
        }
    }

    @Nested
    @DisplayName("재조회 결과에 따른 처리")
    class Requery {

        @Test
        @DisplayName("본문이 DONE 이라도 토스가 DONE 이 아니면 반영하지 않는다")
        void trustsTossNotBody() {
            givenStoredPayment(SECRET);
            // 본문은 DONE 이라 주장하지만 실제로는 입금 대기다
            when(paymentService.verifyPaymentStatus(PAYMENT_KEY)).thenReturn("WAITING_FOR_DEPOSIT");

            var result = service.handle(depositCallback(SECRET));

            assertThat(result).isEqualTo(PaymentWebhookService.Result.IGNORED);
            verify(paymentService, never()).handlePaymentComplete(anyString(), anyString());
        }

        @Test
        @DisplayName("재조회에 실패하면 재시도를 요청한다 (200 을 주면 입금 통보를 영구히 잃는다)")
        void asksRetryWhenRequeryFails() {
            givenStoredPayment(SECRET);
            when(paymentService.verifyPaymentStatus(PAYMENT_KEY)).thenReturn(null);

            var result = service.handle(depositCallback(SECRET));

            assertThat(result).isEqualTo(PaymentWebhookService.Result.RETRY);
        }

        @Test
        @DisplayName("완료 처리 중 예외가 나면 재시도를 요청한다")
        void asksRetryWhenCompletionFails() {
            givenStoredPayment(SECRET);
            when(paymentService.verifyPaymentStatus(PAYMENT_KEY)).thenReturn("DONE");
            doThrow(new RuntimeException("라이선스 발급 실패"))
                    .when(paymentService).handlePaymentComplete(eq(PAYMENT_KEY), eq(ORDER_ID));

            var result = service.handle(depositCallback(SECRET));

            assertThat(result).isEqualTo(PaymentWebhookService.Result.RETRY);
        }
    }
}
