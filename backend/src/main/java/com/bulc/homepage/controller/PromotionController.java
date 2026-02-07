package com.bulc.homepage.controller;

import com.bulc.homepage.entity.Promotion;
import com.bulc.homepage.repository.UserRepository;
import com.bulc.homepage.service.PromotionService;
import com.bulc.homepage.service.PromotionService.PromotionValidationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/promotions")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionService promotionService;
    private final UserRepository userRepository;

    /**
     * 관리자 권한 체크
     */
    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return false;
        }
        UUID userId = UUID.fromString(auth.getName());
        return userRepository.findById(userId)
                .map(user -> "000".equals(user.getRolesCode()) || "001".equals(user.getRolesCode()))
                .orElse(false);
    }

    // ========================================
    // 공개 API (쿠폰 검증)
    // ========================================

    /**
     * 쿠폰 코드 유효성 검증 및 할인 금액 계산
     */
    @PostMapping("/validate")
    public ResponseEntity<?> validateCoupon(@RequestBody CouponValidateRequest request) {
        if (request.code == null || request.code.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "쿠폰 코드를 입력해주세요."));
        }

        if (request.orderAmount == null || request.orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "주문 금액이 유효하지 않습니다."));
        }

        PromotionValidationResult result = promotionService.validateCoupon(
                request.code.trim().toUpperCase(),
                request.productCode,
                request.orderAmount
        );

        if (!result.isValid()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", result.getMessage()));
        }

        Promotion promotion = result.getPromotion();
        return ResponseEntity.ok(new CouponValidateResponse(
                promotion.getCode(),
                promotion.getName(),
                promotion.getDiscountType(),
                promotion.getDiscountValue(),
                result.getDiscountAmount()
        ));
    }

    // ========================================
    // 관리자 API (CRUD)
    // ========================================

    /**
     * 프로모션 목록 조회
     */
    @GetMapping
    public ResponseEntity<List<PromotionResponse>> getPromotions() {
        if (!isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        List<PromotionResponse> promotions = promotionService.getAllPromotions().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(promotions);
    }

    /**
     * 프로모션 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<PromotionResponse> getPromotion(@PathVariable Long id) {
        if (!isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        return promotionService.getPromotionById(id)
                .map(promotion -> ResponseEntity.ok(toResponse(promotion)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 프로모션 생성
     */
    @PostMapping
    public ResponseEntity<?> createPromotion(@RequestBody PromotionRequest request) {
        if (!isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        try {
            Promotion promotion = Promotion.builder()
                    .code(request.code.toUpperCase())
                    .name(request.name)
                    .discountType(request.discountType)
                    .discountValue(request.discountValue)
                    .productCode(request.productCode)
                    .usageLimit(request.usageLimit)
                    .validFrom(request.validFrom != null ? request.validFrom : LocalDateTime.now())
                    .validUntil(request.validUntil)
                    .isActive(request.isActive != null ? request.isActive : true)
                    .build();

            Promotion created = promotionService.createPromotion(promotion);
            return ResponseEntity.ok(toResponse(created));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 프로모션 수정
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updatePromotion(@PathVariable Long id, @RequestBody PromotionRequest request) {
        if (!isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        try {
            Promotion updated = Promotion.builder()
                    .code(request.code.toUpperCase())
                    .name(request.name)
                    .discountType(request.discountType)
                    .discountValue(request.discountValue)
                    .productCode(request.productCode)
                    .usageLimit(request.usageLimit)
                    .validFrom(request.validFrom)
                    .validUntil(request.validUntil)
                    .isActive(request.isActive != null ? request.isActive : true)
                    .build();

            Promotion result = promotionService.updatePromotion(id, updated);
            return ResponseEntity.ok(toResponse(result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 프로모션 삭제
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePromotion(@PathVariable Long id) {
        if (!isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        try {
            promotionService.deletePromotion(id);
            return ResponseEntity.ok(Map.of("message", "프로모션이 삭제되었습니다."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 프로모션 활성화/비활성화 토글
     */
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<?> togglePromotion(@PathVariable Long id) {
        if (!isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        try {
            Promotion result = promotionService.toggleActive(id);
            return ResponseEntity.ok(toResponse(result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ========================================
    // Helper Methods
    // ========================================

    private PromotionResponse toResponse(Promotion promotion) {
        return new PromotionResponse(
                promotion.getId(),
                promotion.getCode(),
                promotion.getName(),
                promotion.getDiscountType(),
                promotion.getDiscountValue(),
                promotion.getProductCode(),
                promotion.getUsageLimit(),
                promotion.getUsageCount(),
                promotion.getValidFrom() != null ? promotion.getValidFrom().toString() : null,
                promotion.getValidUntil() != null ? promotion.getValidUntil().toString() : null,
                promotion.getIsActive(),
                promotion.getCreatedAt() != null ? promotion.getCreatedAt().toString() : null
        );
    }

    // ========================================
    // DTOs
    // ========================================

    public record CouponValidateRequest(
            String code,
            String productCode,
            BigDecimal orderAmount
    ) {}

    public record CouponValidateResponse(
            String code,
            String name,
            Integer discountType,
            BigDecimal discountValue,
            BigDecimal discountAmount
    ) {}

    public record PromotionRequest(
            String code,
            String name,
            Integer discountType,
            BigDecimal discountValue,
            String productCode,
            Integer usageLimit,
            LocalDateTime validFrom,
            LocalDateTime validUntil,
            Boolean isActive
    ) {}

    public record PromotionResponse(
            Long id,
            String code,
            String name,
            Integer discountType,
            BigDecimal discountValue,
            String productCode,
            Integer usageLimit,
            Integer usageCount,
            String validFrom,
            String validUntil,
            Boolean isActive,
            String createdAt
    ) {}
}
