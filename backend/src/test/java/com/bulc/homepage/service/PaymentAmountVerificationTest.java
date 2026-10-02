package com.bulc.homepage.service;

import com.bulc.homepage.catalog.service.PromotionService;
import com.bulc.homepage.config.TossPaymentsConfig;
import com.bulc.homepage.dto.PaymentConfirmRequest;
import com.bulc.homepage.catalog.domain.PricePlan;
import com.bulc.homepage.catalog.domain.Promotion;
import com.bulc.homepage.entity.User;
import com.bulc.homepage.payment.port.LicenseIssuePort;
import com.bulc.homepage.repository.PaymentRepository;
import com.bulc.homepage.catalog.repository.PricePlanRepository;
import com.bulc.homepage.repository.SubscriptionRepository;
import com.bulc.homepage.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 결제 승인 시 금액 검증 (MDP-748).
 *
 * <p>여기가 돈을 지키는 지점이다. 클라이언트가 보낸 금액을 그대로 믿으면 임의 금액으로
 * 결제할 수 있으므로, 서버가 <b>정가 − 서버가 계산한 할인액</b> 을 기대값으로 만들어 대조한다.
 *
 * <p>검증은 토스 승인(캡처) <b>전에</b> 끝난다. 그래서 거부 케이스마다
 * {@code restTemplate} 이 한 번도 불리지 않았음을 확인한다 — 이것이 "돈이 움직이지 않았다" 의 증거다.
 *
 * <p>직전까지는 쿠폰 할인을 적용하면 이 대조에서 전부 걸려 결제가 실패했다. 서버가 쿠폰 적용
 * 사실을 알 방법이 없어 언제나 정가와 비교했기 때문이다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("결제 승인 금액 검증")
class PaymentAmountVerificationTest {

    private static final String ORDER_ID = "BULC_7_1759300000000_abc123";
    private static final String PAYMENT_KEY = "tviva20260101000000ABCDE";
    private static final String PRODUCT_CODE = "001";
    private static final String COUPON = "WELCOME10";
    private static final BigDecimal PRICE = new BigDecimal("1000000");

    private final UUID userId = UUID.randomUUID();

    @Mock private PaymentRepository paymentRepository;
    @Mock private PricePlanRepository pricePlanRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private UserRepository userRepository;
    @Mock private LicenseIssuePort licenseIssuePort;
    @Mock private TossPaymentsConfig tossPaymentsConfig;
    @Mock private RestTemplate restTemplate;
    @Mock private BillingKeyService billingKeyService;
    @Mock private PromotionService promotionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        // ObjectMapper 는 @Mock 이 아니라 실물을 쓴다 (JSON 파싱은 흉내낼 이유가 없다)
        org.springframework.test.util.ReflectionTestUtils
                .setField(paymentService, "objectMapper", objectMapper);

        PricePlan plan = new PricePlan();
        plan.setId(7L);
        plan.setProductCode(PRODUCT_CODE);
        plan.setName("1년");
        plan.setPrice(PRICE);
        plan.setCurrency("KRW");
        plan.setLicensePlanId(null); // 중복 구매 검사 경로를 타지 않게 한다

        when(pricePlanRepository.findById(7L)).thenReturn(Optional.of(plan));
        when(paymentRepository.existsByOrderId(anyString())).thenReturn(false);

        User user = new User();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(tossPaymentsConfig.getSecretKey()).thenReturn("test_sk_dummy");
    }

    private PaymentConfirmRequest request(int amount, String couponCode) {
        PaymentConfirmRequest r = new PaymentConfirmRequest();
        r.setPaymentKey(PAYMENT_KEY);
        r.setOrderId(ORDER_ID);
        r.setAmount(amount);
        r.setPricePlanId(7L);
        r.setCouponCode(couponCode);
        return r;
    }

    private void givenCouponGives(String discount) {
        Promotion p = new Promotion();
        p.setId(1L);
        p.setCode(COUPON);
        when(promotionService.consumeCoupon(eq(COUPON), eq(PRODUCT_CODE), eq(PRICE)))
                .thenReturn(PromotionService.PromotionValidationResult.valid(p, new BigDecimal(discount)));
    }

    private void confirm(PaymentConfirmRequest r) {
        paymentService.confirmPayment(r, userId.toString(), "127.0.0.1");
    }

    /** 토스 호출까지 갔는지로 "금액 검증을 통과했다" 를 판정한다. */
    private void assertReachedTossApi() {
        verify(restTemplate).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    private void assertTossNeverCalled() {
        verify(restTemplate, never()).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Nested
    @DisplayName("쿠폰 없는 결제")
    class WithoutCoupon {

        @Test
        @DisplayName("정가와 같으면 통과한다")
        void exactPricePasses() {
            // 토스 호출은 mock 이 null 을 돌려주므로 이후 단계에서 터진다 — 금액 검증 통과만 본다
            assertThatThrownBy(() -> confirm(request(1_000_000, null)))
                    .isInstanceOf(RuntimeException.class);

            assertReachedTossApi();
            verifyNoInteractions(promotionService);
        }

        @Test
        @DisplayName("정가보다 적으면 거부한다 (토스 호출 전)")
        void lessThanPriceRejected() {
            assertThatThrownBy(() -> confirm(request(1, null)))
                    .hasMessageContaining("상품 가격과 일치하지 않습니다");

            assertTossNeverCalled();
        }

        @Test
        @DisplayName("정가보다 많아도 거부한다")
        void moreThanPriceRejected() {
            assertThatThrownBy(() -> confirm(request(2_000_000, null)))
                    .hasMessageContaining("상품 가격과 일치하지 않습니다");

            assertTossNeverCalled();
        }
    }

    @Nested
    @DisplayName("쿠폰 적용 결제")
    class WithCoupon {

        @Test
        @DisplayName("정가 − 서버가 계산한 할인액이면 통과한다")
        void discountedAmountPasses() {
            givenCouponGives("100000");

            assertThatThrownBy(() -> confirm(request(900_000, COUPON)))
                    .isInstanceOf(RuntimeException.class);

            assertReachedTossApi();
        }

        /**
         * 쿠폰을 적용했는데 정가를 보낸 경우. 서버 기대값은 할인가이므로 불일치로 거부된다.
         * 고객이 손해 보는 방향이라도 금액이 어긋나면 진행하지 않는다.
         */
        @Test
        @DisplayName("쿠폰을 보냈는데 정가를 청구하면 거부한다")
        void fullPriceWithCouponRejected() {
            givenCouponGives("100000");

            assertThatThrownBy(() -> confirm(request(1_000_000, COUPON)))
                    .hasMessageContaining("상품 가격과 일치하지 않습니다");

            assertTossNeverCalled();
        }

        /** 클라이언트가 할인액을 임의로 키워 보내는 시나리오 — 서버 계산값만 인정한다. */
        @Test
        @DisplayName("클라이언트가 할인액을 조작하면 거부한다")
        void forgedDiscountRejected() {
            givenCouponGives("100000");

            assertThatThrownBy(() -> confirm(request(1, COUPON)))
                    .hasMessageContaining("상품 가격과 일치하지 않습니다");

            assertTossNeverCalled();
        }

        @Test
        @DisplayName("쿠폰이 무효하면 거부한다 (사유를 그대로 전달)")
        void invalidCouponRejected() {
            when(promotionService.consumeCoupon(anyString(), anyString(), any()))
                    .thenReturn(PromotionService.PromotionValidationResult
                            .invalid("사용 기간이 만료된 쿠폰입니다."));

            assertThatThrownBy(() -> confirm(request(900_000, COUPON)))
                    .hasMessageContaining("사용 기간이 만료된 쿠폰입니다");

            assertTossNeverCalled();
        }

        @Test
        @DisplayName("쿠폰 코드는 대문자로 정규화해 조회한다")
        void normalizesCodeToUpperCase() {
            givenCouponGives("100000");

            assertThatThrownBy(() -> confirm(request(900_000, "  welcome10  ")))
                    .isInstanceOf(RuntimeException.class);

            verify(promotionService).consumeCoupon(eq(COUPON), eq(PRODUCT_CODE), eq(PRICE));
        }

        @Test
        @DisplayName("빈 문자열 쿠폰은 미적용으로 본다")
        void blankCouponTreatedAsNone() {
            assertThatThrownBy(() -> confirm(request(1_000_000, "   ")))
                    .isInstanceOf(RuntimeException.class);

            assertReachedTossApi();
            verifyNoInteractions(promotionService);
        }

        /**
         * 할인액이 정가를 넘어도 기대값이 음수로 내려가지 않는지 확인한다.
         * 음수가 되면 어떤 양수 금액과도 일치하지 않아 전부 거부되는데, 그 원인이
         * "금액 조작" 으로 보여 진단이 어려워진다.
         *
         * <p><b>주의 — 100% 할인 쿠폰은 이 경로로 결제를 완료할 수 없다.</b>
         * {@code PaymentConfirmRequest.amount} 에 {@code @Positive} 가 걸려 있어
         * 0 원 요청은 컨트롤러의 {@code @Valid} 에서 400 으로 막힌다(서비스까지 오지 않는다).
         * 무료 지급은 결제가 아니라 리딤 코드 경로로 처리해야 한다.
         */
        @Test
        @DisplayName("할인액이 정가를 넘어도 기대값이 음수가 되지 않는다")
        void fullDiscountFloorsAtZero() {
            givenCouponGives("1500000");

            assertThatThrownBy(() -> confirm(request(0, COUPON)))
                    .isInstanceOf(RuntimeException.class);

            // 기대값이 0 이라 amount 0 과 일치 → 금액 검증을 통과했다는 뜻
            assertReachedTossApi();
        }
    }
}
