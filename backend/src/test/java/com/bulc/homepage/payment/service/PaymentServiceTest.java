package com.bulc.homepage.payment.service;

import com.bulc.homepage.entity.User;
import com.bulc.homepage.licensing.exception.LicenseException;
import com.bulc.homepage.licensing.exception.LicenseException.ErrorCode;
import com.bulc.homepage.licensing.service.LicenseService;
import com.bulc.homepage.payment.config.TossPaymentsConfig;
import com.bulc.homepage.payment.domain.Payment;
import com.bulc.homepage.payment.domain.PaymentDetail;
import com.bulc.homepage.payment.domain.PricePlan;
import com.bulc.homepage.payment.dto.PaymentConfirmRequest;
import com.bulc.homepage.payment.dto.PaymentHistoryResponse;
import com.bulc.homepage.payment.repository.PaymentRepository;
import com.bulc.homepage.payment.repository.PricePlanRepository;
import com.bulc.homepage.payment.repository.SubscriptionRepository;
import com.bulc.homepage.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

/**
 * 결제 승인 방어 로직 / 내역 상태 매핑 / 웹훅 멱등성 검증.
 *
 * 토스 API 호출(RestTemplate) 이전에 걸러져야 하는 조건들을 주로 본다.
 * 승인 요청이 한 번 나가면 되돌리려면 취소 API 를 또 태워야 하므로,
 * "거부되는 요청은 토스에 도달하지 않는다"가 이 클래스의 핵심 관심사다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PricePlanRepository pricePlanRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private UserRepository userRepository;
    @Mock private LicenseService licenseService;
    @Mock private TossPaymentsConfig tossPaymentsConfig;
    @Mock private RestTemplate restTemplate;
    @Mock private BillingKeyService billingKeyService;

    private PaymentService paymentService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID LICENSE_PLAN_ID = UUID.randomUUID();
    private static final Long PRICE_PLAN_ID = 1L;
    private static final int PRICE = 1_200_000;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentRepository, pricePlanRepository, subscriptionRepository,
                userRepository, licenseService, tossPaymentsConfig,
                new ObjectMapper(), restTemplate, billingKeyService
        );

        User user = User.builder().email("buyer@example.com").name("구매자").build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        given(paymentRepository.existsByOrderId(anyString())).willReturn(false);
        given(pricePlanRepository.findById(PRICE_PLAN_ID)).willReturn(Optional.of(pricePlan()));
    }

    private PricePlan pricePlan() {
        return PricePlan.builder()
                .id(PRICE_PLAN_ID)
                .productCode("BLC")
                .name("BUL:C 프로")
                .price(BigDecimal.valueOf(PRICE))
                .currency("KRW")
                .licensePlanId(LICENSE_PLAN_ID)
                .build();
    }

    private PaymentConfirmRequest request(int amount) {
        return new PaymentConfirmRequest("pay_key_123", "ORDER-001", amount, PRICE_PLAN_ID);
    }

    /** 토스 승인 API 가 호출되지 않았음을 확인한다. */
    private void assertTossNotCalled() {
        verify(restTemplate, never()).postForEntity(anyString(), any(), eq(String.class));
    }

    @Nested
    @DisplayName("결제 승인 - 토스 호출 전 방어")
    class ConfirmGuards {

        @Test
        @DisplayName("요청 금액이 요금제 가격과 다르면 거부하고 토스를 호출하지 않는다")
        void rejectsAmountMismatch() {
            // 클라이언트가 보낸 금액을 그대로 믿으면 가격 변조가 통한다
            assertThatThrownBy(() ->
                    paymentService.confirmPayment(request(1_000), USER_ID.toString(), "127.0.0.1"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("결제 금액이 상품 가격과 일치하지 않습니다");

            assertTossNotCalled();
        }

        @Test
        @DisplayName("이미 처리된 주문번호는 거부한다 (중복 결제 방지)")
        void rejectsDuplicateOrderId() {
            given(paymentRepository.existsByOrderId("ORDER-001")).willReturn(true);

            assertThatThrownBy(() ->
                    paymentService.confirmPayment(request(PRICE), USER_ID.toString(), "127.0.0.1"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("이미 처리된 주문입니다");

            assertTossNotCalled();
        }

        @Test
        @DisplayName("사용자 식별자가 없으면 거부한다")
        void rejectsMissingUser() {
            assertThatThrownBy(() ->
                    paymentService.confirmPayment(request(PRICE), "  ", "127.0.0.1"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("사용자 인증이 필요합니다");

            assertTossNotCalled();
        }

        @Test
        @DisplayName("사용자 식별자가 UUID 형식이 아니면 거부한다")
        void rejectsMalformedUserId() {
            assertThatThrownBy(() ->
                    paymentService.confirmPayment(request(PRICE), "not-a-uuid", "127.0.0.1"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("사용자 인증 정보가 올바르지 않습니다");

            assertTossNotCalled();
        }

        @Test
        @DisplayName("존재하지 않는 요금제면 거부한다")
        void rejectsUnknownPricePlan() {
            given(pricePlanRepository.findById(PRICE_PLAN_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    paymentService.confirmPayment(request(PRICE), USER_ID.toString(), "127.0.0.1"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("요금제를 찾을 수 없습니다");

            assertTossNotCalled();
        }

        @Test
        @DisplayName("이미 같은 제품 라이선스를 보유하면 거부한다 (중복 구매)")
        void rejectsDuplicatePurchase() {
            willThrow(new LicenseException(ErrorCode.LICENSE_ALREADY_EXISTS))
                    .given(licenseService).requireUserCanPurchasePlan(USER_ID, LICENSE_PLAN_ID);

            assertThatThrownBy(() ->
                    paymentService.confirmPayment(request(PRICE), USER_ID.toString(), "127.0.0.1"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("이미 해당 제품의 라이선스를 보유하고 있습니다");

            assertTossNotCalled();
        }

        @Test
        @DisplayName("중복 구매 외의 라이선스 오류는 그대로 전파한다")
        void propagatesOtherLicenseErrors() {
            willThrow(new LicenseException(ErrorCode.LICENSE_NOT_FOUND))
                    .given(licenseService).requireUserCanPurchasePlan(USER_ID, LICENSE_PLAN_ID);

            assertThatThrownBy(() ->
                    paymentService.confirmPayment(request(PRICE), USER_ID.toString(), "127.0.0.1"))
                    .isInstanceOf(LicenseException.class);

            assertTossNotCalled();
        }
    }

    @Nested
    @DisplayName("결제 내역 조회")
    class History {

        private Payment payment(String status, PaymentDetail detail) {
            Payment p = Payment.builder()
                    .amount(BigDecimal.valueOf(PRICE))
                    .currency("KRW")
                    .orderName("BUL:C 프로")
                    .status(status)
                    .userId(USER_ID)
                    .paidAt(LocalDateTime.now())
                    .build();
            p.setPaymentDetail(detail);
            return p;
        }

        private PaymentDetail detail() {
            return PaymentDetail.builder()
                    .orderId("ORDER-001")
                    .paymentMethod("CARD")
                    .cardCompany("신한")
                    .cardNumber("12345678****123*")
                    .build();
        }

        @Test
        @DisplayName("DB 상태코드를 사람이 읽는 값으로 바꾼다")
        void mapsStatusCodes() {
            given(paymentRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).willReturn(List.of(
                    payment("P", detail()),
                    payment("C", detail()),
                    payment("F", detail()),
                    payment("R", detail())
            ));

            List<PaymentHistoryResponse> result = paymentService.getMyPaymentHistory(USER_ID);

            assertThat(result).extracting(PaymentHistoryResponse::status)
                    .containsExactly("PENDING", "COMPLETED", "FAILED", "REFUNDED");
        }

        @Test
        @DisplayName("모르는 상태코드는 원문을 그대로 둔다")
        void keepsUnknownStatusAsIs() {
            given(paymentRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                    .willReturn(List.of(payment("X", detail())));

            assertThat(paymentService.getMyPaymentHistory(USER_ID).get(0).status()).isEqualTo("X");
        }

        @Test
        @DisplayName("상세 정보가 없어도 터지지 않고 null 로 응답한다")
        void toleratesMissingDetail() {
            given(paymentRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                    .willReturn(List.of(payment("C", null)));

            PaymentHistoryResponse r = paymentService.getMyPaymentHistory(USER_ID).get(0);

            assertThat(r.orderId()).isNull();
            assertThat(r.paymentMethod()).isNull();
            assertThat(r.cardNumber()).isNull();
            assertThat(r.status()).isEqualTo("COMPLETED");
        }
    }

    @Nested
    @DisplayName("웹훅 결제 완료 처리")
    class Webhook {

        @Test
        @DisplayName("이미 완료된 결제는 다시 처리하지 않는다 (웹훅 재전송 대비)")
        void isIdempotent() {
            Payment done = Payment.builder().status("C").userEmail(USER_ID.toString()).build();
            given(paymentRepository.findByPaymentKey("pay_key_123")).willReturn(Optional.of(done));

            paymentService.handlePaymentComplete("pay_key_123", "ORDER-001");

            // 저장도, 라이선스 재발급도 일어나면 안 된다
            verify(paymentRepository, never()).save(any());
            verifyNoInteractions(licenseService);
        }

        @Test
        @DisplayName("대기 상태면 완료로 바꾸고 결제시각을 남긴다")
        void completesPendingPayment() {
            Payment pending = Payment.builder().status("P").userEmail(USER_ID.toString()).build();
            given(paymentRepository.findByPaymentKey("pay_key_123")).willReturn(Optional.of(pending));

            paymentService.handlePaymentComplete("pay_key_123", "ORDER-001");

            assertThat(pending.getStatus()).isEqualTo("C");
            assertThat(pending.getPaidAt()).isNotNull();
            verify(paymentRepository).save(pending);
        }

        @Test
        @DisplayName("결제 정보를 못 찾으면 예외를 던진다")
        void failsOnUnknownPaymentKey() {
            given(paymentRepository.findByPaymentKey("nope")).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.handlePaymentComplete("nope", "ORDER-001"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("결제 정보를 찾을 수 없습니다");
        }
    }
}
