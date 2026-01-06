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
     * 쿠폰 사용 (사용 횟수 증가)
     */
    @Transactional
    public void useCoupon(String code) {
        Promotion promotion = promotionRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new IllegalArgumentException("프로모션을 찾을 수 없습니다."));

        promotion.incrementUsageCount();
        promotionRepository.save(promotion);
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
