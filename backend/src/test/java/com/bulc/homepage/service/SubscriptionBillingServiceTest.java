package com.bulc.homepage.service;

import com.bulc.homepage.entity.BillingKey;
import com.bulc.homepage.entity.PricePlan;
import com.bulc.homepage.entity.Product;
import com.bulc.homepage.entity.Subscription;
import com.bulc.homepage.entity.SubscriptionPayment;
import com.bulc.homepage.payment.port.LicenseIssuePort;
import com.bulc.homepage.repository.BillingKeyRepository;
import com.bulc.homepage.repository.SubscriptionPaymentRepository;
import com.bulc.homepage.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 구독 갱신 결제 경로 (MDP-880).
 *
 * <p>종전에는 이 경로를 확인할 방법이 운영 엔드포인트({@code TestController})로 구독 날짜를
 * 조작한 뒤 배치를 수동 실행해 보는 것뿐이었다. 그 엔드포인트의 배치 3종
 * (process-renewals · retry-failed · process-expired)에는 소유권 검사가 없어
 * 전 사용자 구독을 대상으로 실결제를 일으킬 수 있다 — {@code @Profile("dev")} 하나에
 * 결제 안전이 걸려 있는 구조였다.
 *
 * <p>여기서 같은 시나리오를 목으로 검증하므로 운영 코드에 그 스위치를 두지 않아도 된다.
 *
 * <p><b>한계</b>: {@code Subscription.isDueForRenewal()} 과 서비스가 {@code LocalDateTime.now()}
 * 를 직접 부른다. 지금은 상대 날짜로 우회하지만, 시각 의존 로직을 정밀하게 검증하려면
 * {@code Clock} 주입이 필요하다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SubscriptionBillingService 갱신")
class SubscriptionBillingServiceTest {

    private static final Long SUB_ID = 1L;
    private static final Long BILLING_KEY_ID = 10L;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID LICENSE_PLAN_ID = UUID.randomUUID();
    private static final LocalDateTime END = LocalDateTime.of(2027, 4, 9, 0, 0);

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionPaymentRepository subscriptionPaymentRepository;
    @Mock private BillingKeyRepository billingKeyRepository;
    @Mock private BillingKeyService billingKeyService;
    @Mock private LicenseIssuePort licenseIssuePort;

    private SubscriptionBillingService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionBillingService(
                subscriptionRepository,
                subscriptionPaymentRepository,
                billingKeyRepository,
                billingKeyService,
                licenseIssuePort);
    }

    private Subscription dueSubscription() {
        PricePlan plan = new PricePlan();
        plan.setId(1L);
        plan.setPrice(new BigDecimal("99000"));
        plan.setLicensePlanId(LICENSE_PLAN_ID);

        Product product = new Product();
        product.setName("BUL:C");

        Subscription s = Subscription.builder()
                .id(SUB_ID)
                .userId(USER_ID)
                .status("A")
                .startDate(END.minusYears(1))
                .endDate(END)
                .billingCycle("YEARLY")
                .pricePlan(plan)
                .product(product)
                .build();
        s.enableAutoRenew(BILLING_KEY_ID, "YEARLY");
        return s;
    }

    private BillingKey activeKey() {
        BillingKey k = new BillingKey();
        k.setId(BILLING_KEY_ID);
        k.setUserId(USER_ID);
        k.setIsActive(true);
        return k;
    }

    private void givenPaymentSucceeds() {
        when(billingKeyService.requestBillingPayment(anyLong(), anyString(), anyString(), anyInt(), any()))
                .thenReturn(Map.of("success", true, "paymentKey", "test_payment_key"));
    }

    @Nested
    @DisplayName("결제 성공")
    class PaymentSuccess {

        @Test
        @DisplayName("구독 기간이 1년 연장된다")
        void extendsSubscription() {
            Subscription s = dueSubscription();
            when(billingKeyRepository.findByIdAndIsActiveTrue(BILLING_KEY_ID))
                    .thenReturn(Optional.of(activeKey()));
            givenPaymentSucceeds();

            service.processSubscriptionRenewal(s);

            assertThat(s.getStartDate()).isEqualTo(END);
            assertThat(s.getEndDate()).isEqualTo(END.plusYears(1));
            verify(subscriptionRepository).save(s);
        }

        @Test
        @DisplayName("라이선스 유효기간도 함께 연장된다")
        void renewsLicense() {
            Subscription s = dueSubscription();
            when(billingKeyRepository.findByIdAndIsActiveTrue(BILLING_KEY_ID))
                    .thenReturn(Optional.of(activeKey()));
            givenPaymentSucceeds();

            service.processSubscriptionRenewal(s);

            verify(licenseIssuePort).renew(eq(USER_ID), eq(LICENSE_PLAN_ID), any(UUID.class), any(Instant.class));
        }

        /**
         * 같은 회차를 두 번 처리해도 라이선스가 중복 연장되면 안 된다.
         * sourceOrderId 는 구독 ID + 종료일로 만들어지므로 회차마다 값이 달라야 한다.
         */
        @Test
        @DisplayName("갱신 회차마다 서로 다른 멱등 키를 쓴다")
        void usesDistinctIdempotencyKeyPerCycle() {
            Subscription s = dueSubscription();
            when(billingKeyRepository.findByIdAndIsActiveTrue(BILLING_KEY_ID))
                    .thenReturn(Optional.of(activeKey()));
            givenPaymentSucceeds();

            service.processSubscriptionRenewal(s);
            service.processSubscriptionRenewal(s);

            ArgumentCaptor<UUID> keys = ArgumentCaptor.forClass(UUID.class);
            verify(licenseIssuePort, atLeastOnce())
                    .renew(any(), any(), keys.capture(), any());
            assertThat(keys.getAllValues()).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("결제 이력이 성공으로 기록된다")
        void recordsPaymentSuccess() {
            Subscription s = dueSubscription();
            when(billingKeyRepository.findByIdAndIsActiveTrue(BILLING_KEY_ID))
                    .thenReturn(Optional.of(activeKey()));
            givenPaymentSucceeds();

            service.processSubscriptionRenewal(s);

            ArgumentCaptor<SubscriptionPayment> captor = ArgumentCaptor.forClass(SubscriptionPayment.class);
            verify(subscriptionPaymentRepository, atLeastOnce()).save(captor.capture());
            SubscriptionPayment last = captor.getValue();
            assertThat(last.getStatus()).isEqualTo(SubscriptionPayment.PaymentStatus.SUCCESS);
            assertThat(last.getAmount()).isEqualByComparingTo("99000");
        }
    }

    @Nested
    @DisplayName("결제 실패")
    class PaymentFailure {

        @Test
        @DisplayName("구독 기간이 연장되지 않는다")
        void doesNotExtend() {
            Subscription s = dueSubscription();
            when(billingKeyRepository.findByIdAndIsActiveTrue(BILLING_KEY_ID))
                    .thenReturn(Optional.of(activeKey()));
            when(billingKeyService.requestBillingPayment(anyLong(), anyString(), anyString(), anyInt(), any()))
                    .thenReturn(Map.of("success", false));

            service.processSubscriptionRenewal(s);

            assertThat(s.getEndDate()).isEqualTo(END);
            verify(licenseIssuePort, never()).renew(any(), any(), any(), any());
        }

        @Test
        @DisplayName("결제 API 가 예외를 던져도 전파하지 않는다")
        void swallowsPaymentException() {
            Subscription s = dueSubscription();
            when(billingKeyRepository.findByIdAndIsActiveTrue(BILLING_KEY_ID))
                    .thenReturn(Optional.of(activeKey()));
            when(billingKeyService.requestBillingPayment(anyLong(), anyString(), anyString(), anyInt(), any()))
                    .thenThrow(new RuntimeException("토스 API 500"));

            assertThatCode(() -> service.processSubscriptionRenewal(s)).doesNotThrowAnyException();
            assertThat(s.getEndDate()).isEqualTo(END);
        }
    }

    @Nested
    @DisplayName("갱신 불가 조건 — 자동 갱신을 끄고 종료한다")
    class CannotRenew {

        @Test
        @DisplayName("빌링키가 설정되지 않은 구독")
        void noBillingKeyId() {
            Subscription s = dueSubscription();
            s.setBillingKeyId(null);

            service.processSubscriptionRenewal(s);

            assertThat(s.getAutoRenew()).isFalse();
            verify(billingKeyService, never())
                    .requestBillingPayment(anyLong(), anyString(), anyString(), anyInt(), any());
        }

        /**
         * 운영에서 실제로 걸릴 수 있는 경로다 — 2026-09-17 기준 tosspayment@msimul.com 의
         * 카드가 is_active=false 상태다. 이 경우 결제를 시도하지 않고 자동 갱신을 끈다.
         */
        @Test
        @DisplayName("빌링키가 비활성인 구독")
        void inactiveBillingKey() {
            Subscription s = dueSubscription();
            when(billingKeyRepository.findByIdAndIsActiveTrue(BILLING_KEY_ID))
                    .thenReturn(Optional.empty());

            service.processSubscriptionRenewal(s);

            assertThat(s.getAutoRenew()).isFalse();
            assertThat(s.getNextBillingDate()).isNull();
            verify(billingKeyService, never())
                    .requestBillingPayment(anyLong(), anyString(), anyString(), anyInt(), any());
        }
    }

    @Nested
    @DisplayName("라이선스 연장 생략 조건")
    class LicenseRenewalSkipped {

        @Test
        @DisplayName("요금제에 라이선스 플랜이 연결되지 않았으면 결제는 하되 연장은 건너뛴다")
        void skipsWhenNoLicensePlan() {
            Subscription s = dueSubscription();
            s.getPricePlan().setLicensePlanId(null);
            when(billingKeyRepository.findByIdAndIsActiveTrue(BILLING_KEY_ID))
                    .thenReturn(Optional.of(activeKey()));
            givenPaymentSucceeds();

            service.processSubscriptionRenewal(s);

            assertThat(s.getEndDate()).isEqualTo(END.plusYears(1));
            verify(licenseIssuePort, never()).renew(any(), any(), any(), any());
        }
    }
}
