package com.bulc.homepage.payment.service;

import com.bulc.homepage.catalog.domain.PricePlan;
import com.bulc.homepage.catalog.repository.PricePlanRepository;
import com.bulc.homepage.catalog.service.PromotionService;
import com.bulc.homepage.config.TossPaymentsConfig;
import com.bulc.homepage.entity.User;
import com.bulc.homepage.payment.domain.Payment;
import com.bulc.homepage.payment.domain.Subscription;
import com.bulc.homepage.payment.port.IssuedLicense;
import com.bulc.homepage.payment.port.LicenseIssuePort;
import com.bulc.homepage.payment.repository.PaymentRepository;
import com.bulc.homepage.payment.repository.RefundRequestRepository;
import com.bulc.homepage.payment.repository.SubscriptionRepository;
import com.bulc.homepage.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 가상계좌 입금 웹훅이 구독·라이선스를 실제로 만드는지.
 *
 * <p>이 경로는 한 번도 성공한 적이 없었다(운영 결제 7건 전부 빌링키 경로). 그래서 결함이
 * 드러나지 않았다 — {@code handlePaymentComplete} 는 {@code payment.user_email} 을
 * <b>이메일로</b> 조회해 구독을 만들려 했는데, 이 경로는 그 칸에 UUID 문자열을 넣어 둔다.
 * 그래서 입금 웹훅이 와도 "사용자를 찾을 수 없습니다" 로 터지고 {@code @Transactional} 이
 * 전부 롤백해, 고객은 입금했는데 결제는 P 로 남고 라이선스도 나오지 않았다.
 *
 * <p>웹훅 403 문제를 고친 뒤에야 도달할 수 있게 된 지점이라, 그 수정만으로는 가상계좌가
 * 동작하지 않았다. 이 테스트는 "웹훅이 끝까지 간다"를 고정한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("웹훅 입금 완료 처리")
class WebhookCompletionTest {

    private static final String PAYMENT_KEY = "pk_va_test";
    private static final String ORDER_ID = "BULC-20261014-9001";
    private static final UUID LICENSE_PLAN_ID = UUID.randomUUID();

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
    @Mock private RefundRequestRepository refundRequestRepository;
    @Mock private PaymentFailureRecorder paymentFailureRecorder;

    @InjectMocks private PaymentService paymentService;

    private PricePlan plan;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "objectMapper", new ObjectMapper());

        plan = new PricePlan();
        plan.setId(11L);
        plan.setProductCode("001");
        plan.setName("[내부] 무통장입금 점검 1000원");
        plan.setPrice(new BigDecimal("1000"));
        plan.setCurrency("KRW");
        plan.setLicensePlanId(LICENSE_PLAN_ID);

        when(subscriptionRepository.save(any(Subscription.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    /** 가상계좌 결제가 입금 대기(P) 로 저장된 상태 */
    private Payment pendingVirtualAccountPayment() {
        Payment payment = Payment.builder()
                .amount(new BigDecimal("1000"))
                .currency("KRW")
                .orderName("[내부] 무통장입금 점검 1000원")
                .status("P")
                .userId(userId)
                .userEmail("buyer@example.com")
                .userName("구매자")
                .pricePlan(plan)
                .build();
        ReflectionTestUtils.setField(payment, "id", 42L);
        return payment;
    }

    @Test
    @DisplayName("입금이 확인되면 결제를 완료로 바꾸고 구독과 라이선스를 만든다")
    void completesPaymentAndIssuesLicense() {
        Payment payment = pendingVirtualAccountPayment();
        when(paymentRepository.findByPaymentKey(PAYMENT_KEY)).thenReturn(Optional.of(payment));
        when(licenseIssuePort.issue(any(), any(), any()))
                .thenReturn(new IssuedLicense(UUID.randomUUID(), "BULC-TEST-KEY", java.time.Instant.now().plusSeconds(86400)));

        paymentService.handlePaymentComplete(PAYMENT_KEY, ORDER_ID);

        assertThat(payment.getStatus()).isEqualTo("C");
        assertThat(payment.getPaidAt()).isNotNull();

        // 구독이 결제의 userId 로 만들어져야 한다 — 이메일 조회로는 찾을 수 없는 값이다
        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);

        verify(licenseIssuePort).issue(
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq(LICENSE_PLAN_ID),
                any(UUID.class));
    }

    @Test
    @DisplayName("user_email 이 이메일이 아니어도 구독을 만든다 (종전 결함의 재현 지점)")
    void doesNotDependOnEmailLookup() {
        Payment payment = pendingVirtualAccountPayment();
        // 이 경로가 실제로 넣던 값: 이메일 칸에 UUID 문자열
        payment.setUserEmail(userId.toString());
        when(paymentRepository.findByPaymentKey(PAYMENT_KEY)).thenReturn(Optional.of(payment));
        when(licenseIssuePort.issue(any(), any(), any()))
                .thenReturn(new IssuedLicense(UUID.randomUUID(), "BULC-TEST-KEY", java.time.Instant.now().plusSeconds(86400)));

        // 종전에는 findByEmail(UUID) 가 비어 "사용자를 찾을 수 없습니다" 로 터졌다
        paymentService.handlePaymentComplete(PAYMENT_KEY, ORDER_ID);

        assertThat(payment.getStatus()).isEqualTo("C");
        verify(subscriptionRepository).save(any(Subscription.class));
        // 이메일로 사용자를 찾으려 해선 안 된다
        verify(userRepository, never()).findByEmail(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("라이선스를 주지 않는 요금제면 구독만 만들고 발급은 건너뛴다")
    void skipsLicenseWhenPlanHasNone() {
        plan.setLicensePlanId(null);
        Payment payment = pendingVirtualAccountPayment();
        when(paymentRepository.findByPaymentKey(PAYMENT_KEY)).thenReturn(Optional.of(payment));

        paymentService.handlePaymentComplete(PAYMENT_KEY, ORDER_ID);

        assertThat(payment.getStatus()).isEqualTo("C");
        verify(subscriptionRepository).save(any(Subscription.class));
        verify(licenseIssuePort, never()).issue(any(), any(), any());
    }

    @Test
    @DisplayName("이미 완료된 결제는 중복 반영하지 않는다 (웹훅이 두 번 올 수 있다)")
    void idempotentOnDuplicateWebhook() {
        Payment payment = pendingVirtualAccountPayment();
        payment.setStatus("C");
        when(paymentRepository.findByPaymentKey(PAYMENT_KEY)).thenReturn(Optional.of(payment));

        paymentService.handlePaymentComplete(PAYMENT_KEY, ORDER_ID);

        verify(subscriptionRepository, never()).save(any(Subscription.class));
        verify(licenseIssuePort, never()).issue(any(), any(), any());
    }

    @Test
    @DisplayName("결제에 사용자 정보가 없으면 완료 처리를 거부한다 (조용히 넘기면 구독 없는 결제가 된다)")
    void refusesWhenUserIdMissing() {
        Payment payment = pendingVirtualAccountPayment();
        payment.setUserId(null);
        when(paymentRepository.findByPaymentKey(PAYMENT_KEY)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.handlePaymentComplete(PAYMENT_KEY, ORDER_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("사용자 정보가 없습니다");

        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }
}
