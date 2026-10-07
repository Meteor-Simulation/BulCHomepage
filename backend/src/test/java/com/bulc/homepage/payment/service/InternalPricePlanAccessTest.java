package com.bulc.homepage.payment.service;

import com.bulc.homepage.catalog.service.PromotionService;
import com.bulc.homepage.config.TossPaymentsConfig;
import com.bulc.homepage.payment.dto.BillingPaymentRequest;
import com.bulc.homepage.payment.dto.PaymentConfirmRequest;
import com.bulc.homepage.catalog.domain.PricePlan;
import com.bulc.homepage.entity.User;
import com.bulc.homepage.payment.port.LicenseIssuePort;
import com.bulc.homepage.payment.repository.PaymentRepository;
import com.bulc.homepage.catalog.repository.PricePlanRepository;
import com.bulc.homepage.payment.repository.SubscriptionRepository;
import com.bulc.homepage.repository.UserRepository;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 내부 전용 요금제 접근 제어.
 *
 * <p>소액 결제 점검용 요금제({@code is_internal})는 매니저 이상(roles_code 000·001)만 결제할 수 있다.
 * 목록 조회에서 가리는 것만으로는 부족하다 — {@code pricePlanId} 는 추측 가능한 연속 숫자이고,
 * 결제 API 는 직접 호출할 수 있다. 그래서 <b>돈이 움직이는 두 경로</b> 모두에서 검사한다.
 *
 * <p>거부 케이스마다 {@code restTemplate} 이 불리지 않았음을 확인한다 — "돈이 움직이지 않았다" 의 증거다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("내부 전용 요금제 접근 제어")
class InternalPricePlanAccessTest {

    private static final long PLAN_ID = 11L;
    private static final String ORDER_ID = "BULC_11_1759300000000_abc123";
    private static final String PRODUCT_CODE = "001";
    private static final BigDecimal PRICE = new BigDecimal("100");

    private static final String ADMIN = "000";
    private static final String MANAGER = "001";
    private static final String MEMBER = "002";

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

    @InjectMocks
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "objectMapper", objectMapper);
        when(paymentRepository.existsByOrderId(anyString())).thenReturn(false);
        when(tossPaymentsConfig.getSecretKey()).thenReturn("test_sk_dummy");
    }

    /** @param internal 내부 전용 여부. 점검용 요금제는 라이선스를 발급하지 않으므로 licensePlanId 는 항상 null 이다. */
    private void givenPlan(boolean internal) {
        PricePlan plan = new PricePlan();
        plan.setId(PLAN_ID);
        plan.setProductCode(PRODUCT_CODE);
        plan.setName("[내부] 결제 점검 100원");
        plan.setPrice(PRICE);
        plan.setCurrency("KRW");
        plan.setLicensePlanId(null);
        plan.setIsInternal(internal);
        when(pricePlanRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
    }

    private void givenUserWithRole(String rolesCode) {
        User user = new User();
        user.setId(userId);
        user.setRolesCode(rolesCode);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    private void confirm() {
        PaymentConfirmRequest r = new PaymentConfirmRequest();
        r.setPaymentKey("tviva20260101000000ABCDE");
        r.setOrderId(ORDER_ID);
        r.setAmount(PRICE.intValue());
        r.setPricePlanId(PLAN_ID);
        paymentService.confirmPayment(r, userId.toString(), "127.0.0.1");
    }

    private void payWithBillingKey() {
        BillingPaymentRequest r = new BillingPaymentRequest();
        r.setPricePlanId(PLAN_ID);
        r.setBillingKeyId(5L);
        paymentService.payWithBillingKey(r, userId.toString(), "127.0.0.1");
    }

    private void assertTossNeverCalled() {
        verify(restTemplate, never()).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    /** 토스 호출까지 갔는지로 "접근 검사를 통과했다" 를 판정한다. */
    private void assertReachedTossApi() {
        verify(restTemplate).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Nested
    @DisplayName("결제 승인 경로 (confirmPayment)")
    class ConfirmPath {

        @Test
        @DisplayName("일반 회원은 내부 전용 요금제를 결제할 수 없다 — 토스를 부르지 않는다")
        void memberIsRejected() {
            givenPlan(true);
            givenUserWithRole(MEMBER);

            assertThatThrownBy(InternalPricePlanAccessTest.this::confirm)
                    .isInstanceOf(RuntimeException.class);

            assertTossNeverCalled();
        }

        @ParameterizedTest
        @ValueSource(strings = {ADMIN, MANAGER})
        @DisplayName("매니저·관리자는 통과해 토스 승인까지 간다")
        void elevatedRolesPass(String rolesCode) {
            givenPlan(true);
            givenUserWithRole(rolesCode);

            // 토스 응답은 모킹하지 않았으므로 이후 단계에서 실패한다. 여기서 확인하려는 것은
            // 접근 검사를 통과해 토스 호출까지 도달했다는 사실뿐이다.
            try {
                confirm();
            } catch (RuntimeException ignored) {
                // 토스 응답 부재로 인한 실패는 이 테스트의 관심사가 아니다
            }

            assertReachedTossApi();
        }

        @Test
        @DisplayName("공개 요금제는 일반 회원도 그대로 결제할 수 있다 (기존 동작 보존)")
        void publicPlanUnaffectedForMember() {
            givenPlan(false);
            givenUserWithRole(MEMBER);

            try {
                confirm();
            } catch (RuntimeException ignored) {
                // 토스 응답 부재로 인한 실패는 관심사가 아니다
            }

            assertReachedTossApi();
        }

        @Test
        @DisplayName("거부될 때 라이선스 발급자를 건드리지 않는다")
        void rejectionDoesNotTouchLicensing() {
            givenPlan(true);
            givenUserWithRole(MEMBER);

            assertThatThrownBy(InternalPricePlanAccessTest.this::confirm)
                    .isInstanceOf(RuntimeException.class);

            verifyNoInteractions(licenseIssuePort);
        }

        @Test
        @DisplayName("거부 메시지로 내부 요금제의 존재를 알려주지 않는다")
        void rejectionDoesNotLeakExistence() {
            givenPlan(true);
            givenUserWithRole(MEMBER);

            // '권한이 없다' 고 답하면 그 pricePlanId 가 실재한다는 사실이 새어 나간다.
            // 없는 요금제를 조회했을 때와 같은 문구로 돌려준다.
            assertThatThrownBy(InternalPricePlanAccessTest.this::confirm)
                    .hasMessageContaining("요금제를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("빌링키 청구 경로 (payWithBillingKey)")
    class BillingPath {

        @Test
        @DisplayName("일반 회원은 내부 전용 요금제를 청구할 수 없다 — 카드에 긁지 않는다")
        void memberIsRejected() {
            givenPlan(true);
            givenUserWithRole(MEMBER);

            assertThatThrownBy(InternalPricePlanAccessTest.this::payWithBillingKey)
                    .isInstanceOf(RuntimeException.class);

            // 이 경로의 과금은 billingKeyService 가 수행한다. 불리지 않았어야 한다.
            verify(billingKeyService, never())
                    .requestBillingPayment(anyLong(), anyString(), anyString(), anyInt(), any(UUID.class));
        }

        @Test
        @DisplayName("매니저는 청구 단계까지 간다")
        void managerReachesCharge() {
            givenPlan(true);
            givenUserWithRole(MANAGER);

            try {
                payWithBillingKey();
            } catch (RuntimeException ignored) {
                // 청구 응답을 모킹하지 않아 이후 단계에서 실패한다 — 관심사가 아니다
            }

            verify(billingKeyService)
                    .requestBillingPayment(anyLong(), anyString(), anyString(), anyInt(), any(UUID.class));
        }
    }
}
