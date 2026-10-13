package com.bulc.homepage.payment.service;

import com.bulc.homepage.catalog.domain.PricePlan;
import com.bulc.homepage.catalog.service.PromotionService;
import com.bulc.homepage.config.TossPaymentsConfig;
import com.bulc.homepage.entity.User;
import com.bulc.homepage.payment.dto.BillingPaymentRequest;
import com.bulc.homepage.payment.port.LicenseIssuePort;
import com.bulc.homepage.payment.repository.PaymentRepository;
import com.bulc.homepage.catalog.repository.PricePlanRepository;
import com.bulc.homepage.payment.repository.RefundRequestRepository;
import com.bulc.homepage.payment.repository.SubscriptionRepository;
import com.bulc.homepage.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 빌링키 결제가 실패했을 때 기록이 남는지.
 *
 * <p>이 테스트가 없어서 결함을 놓쳤다. {@code payWithBillingKey} 는 {@code @Transactional} 이라
 * 그 안에서 실패 행을 저장하고 예외를 다시 던지면 Spring 이 트랜잭션 전체를 롤백해 기록까지
 * 지운다. 2026-10-13 카드 거절(잔액부족) 때 {@code payments} 에 status='F' 행이 한 건도
 * 남지 않은 것이 그 때문이다 — 코드는 기록하는 모양이었는데 실제로는 아무것도 남지 않았다.
 *
 * <p>그래서 기록은 {@link PaymentFailureRecorder}(REQUIRES_NEW) 에 맡긴다. 여기서는 그 빈이
 * <b>실제로 불렸는지</b>를 본다 — 롤백 동작 자체는 단위 테스트로 재현할 수 없으므로,
 * "독립 트랜잭션 빈에 위임했다" 는 사실을 고정하는 것이 이 테스트의 몫이다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("빌링 결제 실패 기록")
class BillingPaymentFailureTest {

    private static final long PLAN_ID = 10L;
    private static final BigDecimal PRICE = new BigDecimal("100");

    private final UUID userId = UUID.randomUUID();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private PaymentRepository paymentRepository;
    @Mock private PricePlanRepository pricePlanRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private UserRepository userRepository;
    @Mock private LicenseIssuePort licenseIssuePort;
    @Mock private TossPaymentsConfig tossPaymentsConfig;
    @Mock private RestTemplate restTemplate;
    @Mock private BillingKeyService billingKeyService;
    @Mock private PromotionService promotionService;
    @Mock private RefundRequestRepository refundRequestRepository;
    @Mock private PaymentFailureRecorder paymentFailureRecorder;

    @InjectMocks
    private PaymentService paymentService;

    private PricePlan plan;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "objectMapper", objectMapper);
        when(tossPaymentsConfig.getSecretKey()).thenReturn("test_sk_dummy");

        plan = new PricePlan();
        plan.setId(PLAN_ID);
        plan.setProductCode("001");
        plan.setName("[내부] 결제 점검 100원");
        plan.setPrice(PRICE);
        plan.setCurrency("KRW");
        plan.setLicensePlanId(null);
        plan.setIsInternal(false);
        when(pricePlanRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));

        User user = new User();
        user.setId(userId);
        user.setEmail("buyer@example.com");
        user.setName("구매자");
        user.setRolesCode("002");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    private void charge() {
        BillingPaymentRequest r = new BillingPaymentRequest();
        r.setPricePlanId(PLAN_ID);
        r.setBillingKeyId(5L);
        r.setIdempotencyKey("test-key-" + UUID.randomUUID());
        paymentService.payWithBillingKey(r, userId.toString(), "127.0.0.1");
    }

    @Test
    @DisplayName("카드가 거절되면 실패를 기록하고 거절 사유를 그대로 올린다")
    void recordsFailureOnDecline() {
        // 토스가 거절한 실제 사유 (2026-10-13 사례)
        when(billingKeyService.requestBillingPayment(
                anyLong(), anyString(), anyString(), anyInt(), any(UUID.class), anyString()))
                .thenThrow(new RuntimeException("잔액부족으로 결제에 실패했습니다."));

        assertThatThrownBy(this::charge)
                .isInstanceOf(RuntimeException.class)
                // 사유가 가공되지 않고 그대로 올라가야 한다 — 고객이 왜 실패했는지 알아야 한다
                .hasMessageContaining("잔액부족");

        verify(paymentFailureRecorder).recordBillingFailure(
                eq(userId), eq("buyer@example.com"), eq("구매자"),
                eq(plan), eq(100), anyString(), eq("127.0.0.1"),
                eq("잔액부족으로 결제에 실패했습니다."));
    }

    @Test
    @DisplayName("success=false 응답도 실패로 기록한다 (예외가 아닌 경로)")
    void recordsFailureOnUnsuccessfulResponse() {
        when(billingKeyService.requestBillingPayment(
                anyLong(), anyString(), anyString(), anyInt(), any(UUID.class), anyString()))
                .thenReturn(Map.of("success", false));

        assertThatThrownBy(this::charge).isInstanceOf(RuntimeException.class);

        verify(paymentFailureRecorder).recordBillingFailure(
                eq(userId), anyString(), anyString(), eq(plan), eq(100),
                anyString(), anyString(), eq("빌링 결제 실패"));
    }

    @Test
    @DisplayName("성공하면 실패 기록을 남기지 않는다")
    void noRecordOnSuccess() {
        when(billingKeyService.requestBillingPayment(
                anyLong(), anyString(), anyString(), anyInt(), any(UUID.class), anyString()))
                .thenReturn(Map.of("success", true, "paymentKey", "bill_test"));

        try {
            charge();
        } catch (RuntimeException ignored) {
            // 구독 생성 등 이후 단계는 모킹하지 않아 실패할 수 있다 — 관심사가 아니다
        }

        verify(paymentFailureRecorder, never()).recordBillingFailure(
                any(), any(), any(), any(), anyInt(), any(), any(), any());
    }
}
