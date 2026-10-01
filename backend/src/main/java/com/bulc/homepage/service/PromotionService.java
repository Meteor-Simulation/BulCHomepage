package com.bulc.homepage.service;

import com.bulc.homepage.entity.Promotion;
import com.bulc.homepage.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromotionService {

    private final PromotionRepository promotionRepository;

    /**
     * 전체 프로모션 목록 조회
     */
    public List<Promotion> getAllPromotions() {
        return promotionRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * 활성화된 프로모션 목록 조회
     */
    public List<Promotion> getActivePromotions() {
        return promotionRepository.findByIsActiveTrueOrderByCreatedAtDesc();
    }

    /**
     * ID로 프로모션 조회
     */
    public Optional<Promotion> getPromotionById(Long id) {
        return promotionRepository.findById(id);
    }

    /**
     * 쿠폰 코드로 프로모션 조회
     */
    public Optional<Promotion> getPromotionByCode(String code) {
        return promotionRepository.findByCodeIgnoreCase(code);
    }

    /**
     * 쿠폰 유효성 검증 및 할인 금액 계산
     */
    public PromotionValidationResult validateCoupon(String code, String productCode, BigDecimal orderAmount) {
        Optional<Promotion> promotionOpt = promotionRepository.findByCodeIgnoreCase(code);

        if (promotionOpt.isEmpty()) {
            return PromotionValidationResult.invalid("존재하지 않는 쿠폰 코드입니다.");
        }

        Promotion promotion = promotionOpt.get();

        // 활성화 상태 체크
        if (!promotion.getIsActive()) {
            return PromotionValidationResult.invalid("비활성화된 쿠폰입니다.");
        }

        // 유효 기간 체크
        LocalDateTime now = LocalDateTime.now();
        if (promotion.getValidFrom() != null && now.isBefore(promotion.getValidFrom())) {
            return PromotionValidationResult.invalid("아직 사용 기간이 아닌 쿠폰입니다.");
        }
        if (promotion.getValidUntil() != null && now.isAfter(promotion.getValidUntil())) {
            return PromotionValidationResult.invalid("사용 기간이 만료된 쿠폰입니다.");
        }

        // 사용 횟수 체크
        if (promotion.getUsageLimit() != null && promotion.getUsageCount() >= promotion.getUsageLimit()) {
            return PromotionValidationResult.invalid("사용 가능 횟수가 초과된 쿠폰입니다.");
        }

        // 상품 코드 체크 (null이면 전체 상품 적용)
        if (promotion.getProductCode() != null && !promotion.getProductCode().equals(productCode)) {
            return PromotionValidationResult.invalid("해당 상품에는 적용할 수 없는 쿠폰입니다.");
        }

        // 할인 금액 계산
        BigDecimal discountAmount = promotion.calculateDiscount(orderAmount);

        return PromotionValidationResult.valid(promotion, discountAmount);
    }

    /**
     * 프로모션 생성
     */
    @Transactional
    public Promotion createPromotion(Promotion promotion) {
        // 쿠폰 코드 대문자 변환
        promotion.setCode(promotion.getCode().toUpperCase());

        // 중복 체크
        if (promotionRepository.existsByCodeIgnoreCase(promotion.getCode())) {
            throw new IllegalArgumentException("이미 존재하는 쿠폰 코드입니다.");
        }

        return promotionRepository.save(promotion);
    }

    /**
     * 프로모션 수정
     */
    @Transactional
    public Promotion updatePromotion(Long id, Promotion updatedPromotion) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("프로모션을 찾을 수 없습니다."));

        // 코드 변경 시 중복 체크
        String newCode = updatedPromotion.getCode().toUpperCase();
        if (!promotion.getCode().equalsIgnoreCase(newCode) &&
                promotionRepository.existsByCodeIgnoreCase(newCode)) {
            throw new IllegalArgumentException("이미 존재하는 쿠폰 코드입니다.");
        }

        promotion.setCode(newCode);
        promotion.setName(updatedPromotion.getName());
        promotion.setDiscountType(updatedPromotion.getDiscountType());
        promotion.setDiscountValue(updatedPromotion.getDiscountValue());
        promotion.setProductCode(updatedPromotion.getProductCode());
        promotion.setUsageLimit(updatedPromotion.getUsageLimit());
        promotion.setValidFrom(updatedPromotion.getValidFrom());
        promotion.setValidUntil(updatedPromotion.getValidUntil());
        promotion.setIsActive(updatedPromotion.getIsActive());

        return promotionRepository.save(promotion);
    }

    /**
     * 프로모션 삭제
     */
    @Transactional
    public void deletePromotion(Long id) {
        if (!promotionRepository.existsById(id)) {
            throw new IllegalArgumentException("프로모션을 찾을 수 없습니다.");
        }
        promotionRepository.deleteById(id);
    }

    /**
     * 프로모션 활성화/비활성화 토글
     */
    @Transactional
    public Promotion toggleActive(Long id) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("프로모션을 찾을 수 없습니다."));

        promotion.setIsActive(!promotion.getIsActive());
        return promotionRepository.save(promotion);
    }

    /**
     * 쿠폰을 검증하고 사용 횟수를 <b>원자적으로</b> 차감한다 (MDP-748 · MDP-749).
     *
     * <p>결제 승인 경로 전용이다. {@link #validateCoupon} 만으로는 부족한 이유:
     * 검증과 차감이 분리되어 있으면 동시 결제가 남은 1회를 함께 통과해 한도를 넘는다.
     * 그래서 차감을 {@code UPDATE ... WHERE usage_count < usage_limit} 한 문장으로 하고,
     * 0행이 갱신되면 그 사이 한도가 소진된 것으로 보아 거부한다.
     *
     * <p><b>호출 위치가 중요하다 — 토스 결제 승인(캡처) 전에 불러야 한다.</b>
     * 승인 메서드 전체가 하나의 트랜잭션이므로, 이후 어떤 단계가 실패해 예외가 나면
     * 이 차감도 함께 롤백된다. 즉 "선점했지만 결제가 안 된" 상태가 남지 않는다.
     * 반대로 캡처 후에 차감하면, 캡처는 됐는데 한도가 소진돼 거부해야 하는 난처한 상태가 생긴다.
     *
     * @param code        쿠폰 코드 (대소문자 무시)
     * @param productCode 결제 대상 상품 코드. 쿠폰에 상품 제한이 걸려 있으면 대조한다
     * @param orderAmount 할인 전 정가. 할인액 산정 기준이다
     * @return 검증 통과 시 프로모션과 산정된 할인액. 실패 시 사유가 담긴 결과
     */
    @Transactional
    public PromotionValidationResult consumeCoupon(String code, String productCode, BigDecimal orderAmount) {
        PromotionValidationResult result = validateCoupon(code, productCode, orderAmount);
        if (!result.isValid()) {
            return result;
        }

        Promotion promotion = result.getPromotion();
        if (promotionRepository.consumeUsage(promotion.getId()) == 0) {
            // validateCoupon 통과 후 커밋 사이에 다른 결제가 마지막 1회를 가져간 경우
            return PromotionValidationResult.invalid("사용 가능 횟수가 초과된 쿠폰입니다.");
        }
        return result;
    }

    /**
     * 쿠폰 유효성 검증 결과 클래스
     */
    public static class PromotionValidationResult {
        private final boolean valid;
        private final String message;
        private final Promotion promotion;
        private final BigDecimal discountAmount;

        private PromotionValidationResult(boolean valid, String message, Promotion promotion, BigDecimal discountAmount) {
            this.valid = valid;
            this.message = message;
            this.promotion = promotion;
            this.discountAmount = discountAmount;
        }

        public static PromotionValidationResult valid(Promotion promotion, BigDecimal discountAmount) {
            return new PromotionValidationResult(true, null, promotion, discountAmount);
        }

        public static PromotionValidationResult invalid(String message) {
            return new PromotionValidationResult(false, message, null, null);
        }

        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
        public Promotion getPromotion() { return promotion; }
        public BigDecimal getDiscountAmount() { return discountAmount; }
    }
}
