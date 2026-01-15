package com.bulc.homepage.controller;

import com.bulc.homepage.dto.request.BillingKeyIssueRequest;
import com.bulc.homepage.dto.response.ApiResponse;
import com.bulc.homepage.dto.response.BillingKeyResponse;
import com.bulc.homepage.dto.response.SubscriptionResponse;
import com.bulc.homepage.entity.Subscription;
import com.bulc.homepage.entity.SubscriptionPayment;
import com.bulc.homepage.repository.SubscriptionRepository;
import com.bulc.homepage.service.BillingKeyService;
import com.bulc.homepage.service.SubscriptionBillingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionBillingService subscriptionBillingService;
    private final BillingKeyService billingKeyService;

    /**
     * 내 구독 목록 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<SubscriptionResponse>>> getMySubscriptions() {
        String userEmail = getCurrentUserEmail();

        List<SubscriptionResponse> subscriptions = subscriptionRepository.findByUserEmailOrderByCreatedAtDesc(userEmail)
                .stream()
                .map(this::toSubscriptionResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("구독 목록 조회 성공", subscriptions));
    }

    /**
     * 구독 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> getSubscription(@PathVariable Long id) {
        String userEmail = getCurrentUserEmail();

        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("구독을 찾을 수 없습니다."));

        if (!subscription.getUserEmail().equals(userEmail)) {
            return ResponseEntity.status(403).body(ApiResponse.error("접근 권한이 없습니다."));
        }

        return ResponseEntity.ok(ApiResponse.success("구독 조회 성공", toSubscriptionResponse(subscription)));
    }

    /**
     * 자동 갱신 활성화
     */
    @PostMapping("/{id}/auto-renew")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> enableAutoRenew(
            @PathVariable Long id,
            @RequestParam Long billingKeyId,
            @RequestParam(defaultValue = "YEARLY") String billingCycle) {
        String userEmail = getCurrentUserEmail();

        try {
            Subscription subscription = subscriptionBillingService.enableAutoRenew(id, billingKeyId, billingCycle, userEmail);
            return ResponseEntity.ok(ApiResponse.success("자동 갱신이 활성화되었습니다.", toSubscriptionResponse(subscription)));
        } catch (Exception e) {
            log.error("자동 갱신 활성화 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 자동 갱신 비활성화
     */
    @DeleteMapping("/{id}/auto-renew")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> disableAutoRenew(@PathVariable Long id) {
        String userEmail = getCurrentUserEmail();

        try {
            Subscription subscription = subscriptionBillingService.disableAutoRenew(id, userEmail);
            return ResponseEntity.ok(ApiResponse.success("자동 갱신이 비활성화되었습니다.", toSubscriptionResponse(subscription)));
        } catch (Exception e) {
            log.error("자동 갱신 비활성화 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 구독 결제 이력 조회
     */
    @GetMapping("/{id}/payments")
    public ResponseEntity<ApiResponse<List<SubscriptionPayment>>> getPaymentHistory(@PathVariable Long id) {
        String userEmail = getCurrentUserEmail();

        try {
            List<SubscriptionPayment> payments = subscriptionBillingService.getPaymentHistory(id, userEmail);
            return ResponseEntity.ok(ApiResponse.success("결제 이력 조회 성공", payments));
        } catch (Exception e) {
            log.error("결제 이력 조회 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ======== 빌링키 관련 API ========

    /**
     * 빌링키 발급 (카드 등록)
     */
    @PostMapping("/billing-keys")
    public ResponseEntity<ApiResponse<BillingKeyResponse>> issueBillingKey(
            @Valid @RequestBody BillingKeyIssueRequest request) {
        String userEmail = getCurrentUserEmail();

        try {
            BillingKeyResponse response = billingKeyService.issueBillingKey(request, userEmail);
            return ResponseEntity.ok(ApiResponse.success("카드가 등록되었습니다.", response));
        } catch (Exception e) {
            log.error("빌링키 발급 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 내 빌링키(등록된 카드) 목록 조회
     */
    @GetMapping("/billing-keys")
    public ResponseEntity<ApiResponse<List<BillingKeyResponse>>> getMyBillingKeys() {
        String userEmail = getCurrentUserEmail();

        List<BillingKeyResponse> billingKeys = billingKeyService.getUserBillingKeys(userEmail);
        return ResponseEntity.ok(ApiResponse.success("등록된 카드 목록 조회 성공", billingKeys));
    }

    /**
     * 기본 결제 수단 변경
     */
    @PatchMapping("/billing-keys/{id}/default")
    public ResponseEntity<ApiResponse<BillingKeyResponse>> setDefaultBillingKey(@PathVariable Long id) {
        String userEmail = getCurrentUserEmail();

        try {
            BillingKeyResponse response = billingKeyService.setDefaultBillingKey(id, userEmail);
            return ResponseEntity.ok(ApiResponse.success("기본 결제 수단이 변경되었습니다.", response));
        } catch (Exception e) {
            log.error("기본 결제 수단 변경 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 빌링키 삭제 (카드 삭제)
     */
    @DeleteMapping("/billing-keys/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBillingKey(@PathVariable Long id) {
        String userEmail = getCurrentUserEmail();

        try {
            billingKeyService.deleteBillingKey(id, userEmail);
            return ResponseEntity.ok(ApiResponse.success("카드가 삭제되었습니다.", null));
        } catch (Exception e) {
            log.error("빌링키 삭제 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() ||
                "anonymousUser".equals(authentication.getPrincipal())) {
            throw new RuntimeException("인증되지 않은 사용자입니다.");
        }
        return authentication.getName();
    }

    private SubscriptionResponse toSubscriptionResponse(Subscription subscription) {
        return SubscriptionResponse.builder()
                .id(subscription.getId())
                .productCode(subscription.getProductCode())
                .productName(subscription.getProduct() != null ? subscription.getProduct().getName() : null)
                .pricePlanId(subscription.getPricePlan() != null ? subscription.getPricePlan().getId() : null)
                .pricePlanName(subscription.getPricePlan() != null ? subscription.getPricePlan().getName() : null)
                .price(subscription.getPricePlan() != null ? subscription.getPricePlan().getPrice() : null)
                .currency(subscription.getPricePlan() != null ? subscription.getPricePlan().getCurrency() : null)
                .status(subscription.getStatus())
                .startDate(subscription.getStartDate())
                .endDate(subscription.getEndDate())
                .autoRenew(subscription.getAutoRenew())
                .billingCycle(subscription.getBillingCycle())
                .nextBillingDate(subscription.getNextBillingDate())
                .createdAt(subscription.getCreatedAt())
                .build();
    }
}
