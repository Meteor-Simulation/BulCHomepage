package com.bulc.homepage.payment.service;

import com.bulc.homepage.config.TossPaymentsConfig;
import com.bulc.homepage.payment.domain.Payment;
import com.bulc.homepage.payment.domain.PaymentDetail;
import com.bulc.homepage.payment.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 결제 취소·환불.
 *
 * <p>토스에 승인취소를 요청하고 <b>성공한 뒤에만</b> 우리 기록을 바꾼다. 순서를 뒤집으면
 * 토스는 살아 있는데 우리만 환불로 적힌 상태가 생겨 대조가 불가능해진다. 그래서 거부
 * 케이스마다 결제 상태가 그대로인지(= 기록을 건드리지 않았는지) 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("결제 취소")
class PaymentCancelServiceTest {

    private static final Long PAYMENT_ID = 42L;
    private static final String PAYMENT_KEY = "bill_20261007154919u6tM0";
    private static final String ORDER_ID = "BILL-10-1791355751063";
    private static final BigDecimal AMOUNT = new BigDecimal("100");

    private final UUID operator = UUID.randomUUID();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private PaymentRepository paymentRepository;
    @Mock private TossPaymentsConfig tossPaymentsConfig;
    @Mock private RestTemplate restTemplate;

    @InjectMocks
    private PaymentCancelService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
        when(tossPaymentsConfig.getSecretKey()).thenReturn("test_sk_dummy");
    }

    private Payment givenPayment(String status, boolean withKey) {
        Payment payment = new Payment();
        payment.setId(PAYMENT_ID);
        payment.setStatus(status);
        payment.setAmount(AMOUNT);

        PaymentDetail detail = new PaymentDetail();
        detail.setOrderId(ORDER_ID);
        detail.setPaymentKey(withKey ? PAYMENT_KEY : null);
        detail.setTossStatus("DONE");
        payment.setPaymentDetail(detail);

        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        return payment;
    }

    /** 토스 취소 응답을 흉내낸다. balanceAmount 0 = 전액 취소. */
    private void givenTossCancels(String tossStatus, String balanceAmount) {
        String body = """
                {"orderId":"%s","status":"%s","balanceAmount":%s,
                 "cancels":[{"cancelAmount":100,"cancelReason":"테스트"}]}
                """.formatted(ORDER_ID, tossStatus, balanceAmount);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));
    }

    private void assertTossNeverCalled() {
        verify(restTemplate, never()).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Nested
    @DisplayName("정상 취소")
    class Success {

        @Test
        @DisplayName("전액 취소되면 환불 상태로 바꾸고 사유·금액·시각을 남긴다")
        void marksRefunded() {
            Payment payment = givenPayment("C", true);
            givenTossCancels("CANCELED", "0");

            PaymentCancelService.CancelResult result = service.cancel(PAYMENT_ID, "중복 결제 취소", operator);

            assertThat(payment.getStatus()).isEqualTo("R");
            assertThat(payment.getRefundAmount()).isEqualByComparingTo(AMOUNT);
            assertThat(payment.getRefundReason()).isEqualTo("중복 결제 취소");
            assertThat(payment.getRefundedAt()).isNotNull();
            assertThat(payment.getPaymentDetail().getTossStatus()).isEqualTo("CANCELED");

            assertThat(result.orderId()).isEqualTo(ORDER_ID);
            assertThat(result.tossStatus()).isEqualTo("CANCELED");
            verify(paymentRepository).save(payment);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        @DisplayName("사유가 비면 기본 문구를 남긴다 — 환불 기록에 빈칸을 두지 않는다")
        void blankReasonGetsDefault(String reason) {
            Payment payment = givenPayment("C", true);
            givenTossCancels("CANCELED", "0");

            service.cancel(PAYMENT_ID, reason, operator);

            assertThat(payment.getRefundReason()).isEqualTo("관리자 취소");
        }

        @Test
        @DisplayName("사유가 null 이어도 기본 문구를 남긴다")
        void nullReasonGetsDefault() {
            Payment payment = givenPayment("C", true);
            givenTossCancels("CANCELED", "0");

            service.cancel(PAYMENT_ID, null, operator);

            assertThat(payment.getRefundReason()).isEqualTo("관리자 취소");
        }
    }

    @Nested
    @DisplayName("토스를 부르기 전에 거부하는 경우")
    class RejectedBeforeToss {

        @Test
        @DisplayName("이미 환불된 결제는 다시 취소할 수 없다")
        void alreadyRefunded() {
            givenPayment("R", true);

            assertThatThrownBy(() -> service.cancel(PAYMENT_ID, "사유", operator))
                    .isInstanceOf(PaymentCancelService.PaymentCancelException.class)
                    .hasMessageContaining("이미 환불된");

            assertTossNeverCalled();
        }

        @ParameterizedTest
        @ValueSource(strings = {"P", "F"})
        @DisplayName("완료되지 않은 결제는 취소할 승인이 없다 (대기·실패)")
        void notCompleted(String status) {
            givenPayment(status, true);

            assertThatThrownBy(() -> service.cancel(PAYMENT_ID, "사유", operator))
                    .isInstanceOf(PaymentCancelService.PaymentCancelException.class)
                    .hasMessageContaining("완료된 결제만");

            assertTossNeverCalled();
        }

        @Test
        @DisplayName("결제 키가 없으면 취소할 수 없다 — 대시보드로 안내한다")
        void missingPaymentKey() {
            givenPayment("C", false);

            assertThatThrownBy(() -> service.cancel(PAYMENT_ID, "사유", operator))
                    .isInstanceOf(PaymentCancelService.PaymentCancelException.class)
                    .hasMessageContaining("결제 키가 없어");

            assertTossNeverCalled();
        }

        @Test
        @DisplayName("없는 결제 id")
        void notFound() {
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancel(PAYMENT_ID, "사유", operator))
                    .isInstanceOf(PaymentCancelService.PaymentCancelException.class)
                    .hasMessageContaining("찾을 수 없습니다");

            assertTossNeverCalled();
        }
    }

    @Nested
    @DisplayName("토스 응답 처리")
    class TossResponse {

        @Test
        @DisplayName("전액 취소가 아니면 환불로 적지 않는다 — 부분 취소를 전액으로 기록하면 회계가 어긋난다")
        void partialCancelIsRefused() {
            Payment payment = givenPayment("C", true);
            givenTossCancels("PARTIAL_CANCELED", "50");

            assertThatThrownBy(() -> service.cancel(PAYMENT_ID, "사유", operator))
                    .isInstanceOf(PaymentCancelService.PaymentCancelException.class)
                    .hasMessageContaining("전액 취소되지 않았습니다");

            // 기록은 그대로여야 한다
            assertThat(payment.getStatus()).isEqualTo("C");
            assertThat(payment.getRefundedAt()).isNull();
            verify(paymentRepository, never()).save(any(Payment.class));
        }

        @Test
        @DisplayName("토스가 거부하면 그 사유를 그대로 올리고 기록을 건드리지 않는다")
        void tossRejectionPropagatesReason() {
            Payment payment = givenPayment("C", true);
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(HttpClientErrorException.create(
                            HttpStatus.BAD_REQUEST, "Bad Request", null,
                            "{\"code\":\"REFUND_ACCOUNT_REQUIRED\",\"message\":\"환불 계좌 정보가 필요합니다.\"}"
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8), null));

            assertThatThrownBy(() -> service.cancel(PAYMENT_ID, "사유", operator))
                    .isInstanceOf(PaymentCancelService.PaymentCancelException.class)
                    .hasMessageContaining("환불 계좌 정보가 필요합니다");

            assertThat(payment.getStatus()).isEqualTo("C");
            verify(paymentRepository, never()).save(any(Payment.class));
        }
    }
}
