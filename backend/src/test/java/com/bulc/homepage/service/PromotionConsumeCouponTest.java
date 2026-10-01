package com.bulc.homepage.service;

import com.bulc.homepage.entity.Promotion;
import com.bulc.homepage.repository.PromotionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 쿠폰 검증 + 사용 횟수 차감 (MDP-748 · MDP-749).
 *
 * <p>돈이 걸린 계산이므로 경계값을 함께 검증한다. 특히 "검증은 통과했는데 차감이 실패하는"
 * 경로(한도 소진 경합)가 결제를 반드시 막아야 한다 — 여기서 통과시키면 한도를 넘겨 할인이 나간다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("쿠폰 사용(consumeCoupon)")
class PromotionConsumeCouponTest {

    private static final String CODE = "WELCOME10";
    private static final String PRODUCT = "001";
    private static final BigDecimal PRICE = new BigDecimal("1000000");

    @Mock
    private PromotionRepository promotionRepository;

    @InjectMocks
    private PromotionService promotionService;

    private Promotion promotion(int percent, Integer usageLimit, int usageCount) {
        Promotion p = new Promotion();
        p.setId(1L);
        p.setCode(CODE);
        p.setName("신규 10% 할인");
        p.setDiscountType(percent);
        p.setDiscountValue(BigDecimal.ZERO);
        p.setUsageLimit(usageLimit);
        p.setUsageCount(usageCount);
        p.setValidFrom(LocalDateTime.now().minusDays(1));
        p.setValidUntil(LocalDateTime.now().plusDays(1));
        p.setIsActive(true);
        return p;
    }

    private void given(Promotion p) {
        when(promotionRepository.findByCodeIgnoreCase(CODE)).thenReturn(Optional.of(p));
    }

    private void givenConsumeSucceeds() {
        when(promotionRepository.consumeUsage(anyLong())).thenReturn(1);
    }

    @Nested
    @DisplayName("정상 적용")
    class Success {

        @Test
        @DisplayName("할인액을 산정하고 사용 횟수를 차감한다")
        void consumesAndReturnsDiscount() {
            given(promotion(10, 100, 0));
            givenConsumeSucceeds();

            var result = promotionService.consumeCoupon(CODE, PRODUCT, PRICE);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getDiscountAmount()).isEqualByComparingTo("100000");
            verify(promotionRepository).consumeUsage(1L);
        }

        @Test
        @DisplayName("사용 한도가 없으면(무제한) 통과한다")
        void unlimitedUsage() {
            given(promotion(10, null, 999999));
            givenConsumeSucceeds();

            assertThat(promotionService.consumeCoupon(CODE, PRODUCT, PRICE).isValid()).isTrue();
        }

        @Test
        @DisplayName("상품 제한이 없는 쿠폰은 어느 상품에도 적용된다")
        void noProductRestriction() {
            Promotion p = promotion(10, 100, 0);
            p.setProductCode(null);
            given(p);
            givenConsumeSucceeds();

            assertThat(promotionService.consumeCoupon(CODE, "아무상품", PRICE).isValid()).isTrue();
        }

        /**
         * 원 단위로 끊어야 한다 — 토스에 소수점 금액을 보낼 수 없다.
         * {@code calculateDiscount} 가 DOWN 으로 버리므로 할인이 1원이라도 과하게 나가지 않는다.
         */
        @Test
        @DisplayName("할인액에 소수점이 생기면 버린다 (고객 유리하게가 아니라 원 단위 절사)")
        void truncatesFraction() {
            given(promotion(33, 100, 0));
            givenConsumeSucceeds();

            // 1,000,000 * 0.33 = 330,000 — 나누어떨어지지 않는 경우도 정수로 끊긴다
            var result = promotionService.consumeCoupon(CODE, PRODUCT, new BigDecimal("1000001"));

            assertThat(result.getDiscountAmount().scale()).isLessThanOrEqualTo(0);
            assertThat(result.getDiscountAmount()).isEqualByComparingTo("330000");
        }
    }

    @Nested
    @DisplayName("거부")
    class Rejected {

        @Test
        @DisplayName("없는 코드면 차감하지 않는다")
        void unknownCode() {
            when(promotionRepository.findByCodeIgnoreCase(CODE)).thenReturn(Optional.empty());

            var result = promotionService.consumeCoupon(CODE, PRODUCT, PRICE);

            assertThat(result.isValid()).isFalse();
            verify(promotionRepository, never()).consumeUsage(anyLong());
        }

        @Test
        @DisplayName("비활성 쿠폰")
        void inactive() {
            Promotion p = promotion(10, 100, 0);
            p.setIsActive(false);
            given(p);

            assertThat(promotionService.consumeCoupon(CODE, PRODUCT, PRICE).isValid()).isFalse();
            verify(promotionRepository, never()).consumeUsage(anyLong());
        }

        @Test
        @DisplayName("기간 만료")
        void expired() {
            Promotion p = promotion(10, 100, 0);
            p.setValidUntil(LocalDateTime.now().minusSeconds(1));
            given(p);

            assertThat(promotionService.consumeCoupon(CODE, PRODUCT, PRICE).isValid()).isFalse();
            verify(promotionRepository, never()).consumeUsage(anyLong());
        }

        @Test
        @DisplayName("아직 시작 전")
        void notStarted() {
            Promotion p = promotion(10, 100, 0);
            p.setValidFrom(LocalDateTime.now().plusDays(1));
            given(p);

            assertThat(promotionService.consumeCoupon(CODE, PRODUCT, PRICE).isValid()).isFalse();
        }

        @Test
        @DisplayName("사용 한도 도달 (검증 단계에서 걸림)")
        void usageLimitReached() {
            given(promotion(10, 5, 5));

            var result = promotionService.consumeCoupon(CODE, PRODUCT, PRICE);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("횟수");
            verify(promotionRepository, never()).consumeUsage(anyLong());
        }

        @Test
        @DisplayName("다른 상품 전용 쿠폰")
        void otherProductOnly() {
            Promotion p = promotion(10, 100, 0);
            p.setProductCode("002");
            given(p);

            assertThat(promotionService.consumeCoupon(CODE, "001", PRICE).isValid()).isFalse();
        }

        /**
         * 핵심 경합 케이스. 검증 시점에는 남은 횟수가 있었지만, 차감 UPDATE 가 0행을 갱신했다
         * = 그 사이 다른 결제가 마지막 1회를 가져갔다. 여기서 통과시키면 한도를 넘겨 할인이 나간다.
         */
        @Test
        @DisplayName("검증 후 한도가 소진되면(동시 결제) 거부한다")
        void losesRaceForLastUse() {
            given(promotion(10, 5, 4));
            when(promotionRepository.consumeUsage(anyLong())).thenReturn(0);

            var result = promotionService.consumeCoupon(CODE, PRODUCT, PRICE);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("횟수");
        }
    }
}
