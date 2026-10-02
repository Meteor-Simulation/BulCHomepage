package com.bulc.homepage.service;

import com.bulc.homepage.catalog.service.PromotionService;
import com.bulc.homepage.catalog.domain.Promotion;
import com.bulc.homepage.catalog.repository.PromotionRepository;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 쓸 수 없는 할인 설정을 생성·수정 단계에서 막는다 (MDP-748).
 *
 * <p>할인 계산은 {@code discountType} 을 할인율(%)로만 쓴다. {@code discountValue}(정액)는
 * 아직 계산에 반영되지 않는다 — 할인 방식 설계는 MDP-747 의 결정 사항이다.
 *
 * <p>그래서 할인율이 없는 쿠폰은 "할인 0원" 이 되어 조용히 아무 일도 하지 않는다. 고객은
 * 쿠폰을 넣었는데 정가를 결제한다. <b>에러보다 조용히 틀리는 쪽이 더 나쁘다.</b>
 *
 * <p>방어를 두 곳에 둔다. 여기(생성·수정)서 막으면 앞으로 만들어지지 않고,
 * 사용 시점({@link PromotionConsumeCouponTest})에서 막으면 DB 에 직접 넣은 것과
 * 이 검증 전에 만들어진 것까지 걸러진다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("쿠폰 할인 설정 검증")
class PromotionDiscountValidationTest {

    @Mock
    private PromotionRepository promotionRepository;

    @InjectMocks
    private PromotionService promotionService;

    private Promotion coupon(Integer rate, BigDecimal fixedAmount) {
        Promotion p = new Promotion();
        p.setId(1L);
        p.setCode("TEST10");
        p.setName("테스트");
        p.setDiscountType(rate);
        p.setDiscountValue(fixedAmount);
        p.setUsageCount(0);
        p.setValidFrom(LocalDateTime.now().minusDays(1));
        p.setIsActive(true);
        return p;
    }

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("할인율 10% 는 생성된다")
        void percentIsAllowed() {
            Promotion p = coupon(10, BigDecimal.ZERO);
            when(promotionRepository.existsByCodeIgnoreCase(any())).thenReturn(false);
            when(promotionRepository.save(any())).thenReturn(p);

            assertThatCode(() -> promotionService.createPromotion(p)).doesNotThrowAnyException();
        }

        /** 정액 할인만 넣은 쿠폰. 만들 수 있게 두면 "넣었는데 안 깎이는" 쿠폰이 생긴다. */
        @Test
        @DisplayName("정액만 설정하면 거부한다 (아직 미지원)")
        void fixedAmountOnlyRejected() {
            Promotion p = coupon(0, new BigDecimal("50000"));

            assertThatThrownBy(() -> promotionService.createPromotion(p))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("1~100")
                    .hasMessageContaining("정액 할인은 아직 지원하지 않습니다");

            verify(promotionRepository, never()).save(any());
        }

        @Test
        @DisplayName("할인율이 null 이면 거부한다")
        void nullRateRejected() {
            assertThatThrownBy(() -> promotionService.createPromotion(coupon(null, null)))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(promotionRepository, never()).save(any());
        }

        @Test
        @DisplayName("음수 할인율은 거부한다 (금액이 올라간다)")
        void negativeRateRejected() {
            assertThatThrownBy(() -> promotionService.createPromotion(coupon(-10, null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("100% 초과는 거부한다")
        void overHundredRejected() {
            assertThatThrownBy(() -> promotionService.createPromotion(coupon(101, null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("100% 는 경계값으로 허용한다")
        void exactlyHundredAllowed() {
            Promotion p = coupon(100, null);
            when(promotionRepository.existsByCodeIgnoreCase(any())).thenReturn(false);
            when(promotionRepository.save(any())).thenReturn(p);

            assertThatCode(() -> promotionService.createPromotion(p)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("수정")
    class Update {

        /** 생성만 막으면 "정상으로 만들고 나서 0으로 수정" 으로 우회된다. */
        @Test
        @DisplayName("쓸 수 없는 상태로 바꾸는 것도 거부한다")
        void cannotUpdateIntoUnusableState() {
            Promotion existing = coupon(10, BigDecimal.ZERO);
            when(promotionRepository.findById(1L)).thenReturn(Optional.of(existing));

            Promotion patch = coupon(0, new BigDecimal("50000"));

            assertThatThrownBy(() -> promotionService.updatePromotion(1L, patch))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("1~100");

            // 기존 값이 훼손되지 않았는지 — 검증이 반영보다 먼저 일어나야 한다
            assertThat(existing.getDiscountType()).isEqualTo(10);
            verify(promotionRepository, never()).save(any());
        }

        @Test
        @DisplayName("유효한 할인율로는 수정된다")
        void validRateUpdates() {
            Promotion existing = coupon(10, BigDecimal.ZERO);
            when(promotionRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(promotionRepository.save(any())).thenReturn(existing);

            assertThatCode(() -> promotionService.updatePromotion(1L, coupon(25, BigDecimal.ZERO)))
                    .doesNotThrowAnyException();
            assertThat(existing.getDiscountType()).isEqualTo(25);
        }
    }

    @Nested
    @DisplayName("판정 함수")
    class Predicate {

        @Test
        @DisplayName("1~100 만 사용 가능으로 본다")
        void boundaries() {
            assertThat(PromotionService.isDiscountUsable(coupon(null, null))).isFalse();
            assertThat(PromotionService.isDiscountUsable(coupon(-1, null))).isFalse();
            assertThat(PromotionService.isDiscountUsable(coupon(0, null))).isFalse();
            assertThat(PromotionService.isDiscountUsable(coupon(1, null))).isTrue();
            assertThat(PromotionService.isDiscountUsable(coupon(100, null))).isTrue();
            assertThat(PromotionService.isDiscountUsable(coupon(101, null))).isFalse();
        }

        /** 정액 값이 있어도 통과시키지 않는다 — 계산식이 쓰지 않으므로 할인이 되지 않는다. */
        @Test
        @DisplayName("정액 값이 있어도 할인율이 없으면 사용 불가다")
        void fixedAmountDoesNotMakeItUsable() {
            assertThat(PromotionService.isDiscountUsable(coupon(0, new BigDecimal("99999")))).isFalse();
        }
    }
}
