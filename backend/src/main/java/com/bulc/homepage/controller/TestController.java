package com.bulc.homepage.controller;

import com.bulc.homepage.entity.Subscription;
import com.bulc.homepage.entity.SubscriptionPayment;
import com.bulc.homepage.repository.SubscriptionPaymentRepository;
import com.bulc.homepage.repository.SubscriptionRepository;
import com.bulc.homepage.service.SubscriptionBillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 테스트용 컨트롤러 (개발 환경에서만 활성화)
 * 구독 갱신 테스트를 위한 시간 시뮬레이션 기능 제공
 */
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@Slf4j
@Profile("dev")  // 개발 환경에서만 활성화
public class TestController {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPaymentRepository subscriptionPaymentRepository;
    private final SubscriptionBillingService subscriptionBillingService;

    /**
     * 구독 종료일을 현재 시간 근처로 변경하여 갱신 테스트 가능하게 함
     *
     * @param subscriptionId 구독 ID
     * @param daysUntilExpiry 만료까지 남은 일수 (기본값: 3일)
     */
    @PostMapping("/subscriptions/{subscriptionId}/simulate-near-expiry")
    public ResponseEntity<Map<String, Object>> simulateNearExpiry(
            @PathVariable Long subscriptionId,
            @RequestParam(defaultValue = "3") int daysUntilExpiry) {

        UUID userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "인증이 필요합니다"));
        }

        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("구독을 찾을 수 없습니다"));

        // 권한 확인
        if (!subscription.getUserId().equals(userId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "권한이 없습니다"));
        }

        // 시간 시뮬레이션: 종료일을 daysUntilExpiry일 후로 설정
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime newEndDate = now.plusDays(daysUntilExpiry);
        LocalDateTime newStartDate = newEndDate.minusYears(1);  // 시작일도 조정

        subscription.setStartDate(newStartDate);
        subscription.setEndDate(newEndDate);

        // 자동 갱신이 활성화되어 있으면 다음 결제일도 재계산
        if (subscription.getAutoRenew()) {
            subscription.calculateNextBillingDate();
        }

        subscriptionRepository.save(subscription);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "구독 종료일이 " + daysUntilExpiry + "일 후로 설정되었습니다");
        result.put("subscriptionId", subscriptionId);
        result.put("newStartDate", newStartDate);
        result.put("newEndDate", newEndDate);
        result.put("nextBillingDate", subscription.getNextBillingDate());
        result.put("isDueForRenewal", subscription.isDueForRenewal());

        log.info("[테스트] 구독 만료일 시뮬레이션: subscriptionId={}, newEndDate={}", subscriptionId, newEndDate);

        return ResponseEntity.ok(result);
    }

    /**
     * 구독을 즉시 갱신 대상으로 만듦 (다음 결제일을 현재로 설정)
     */
    @PostMapping("/subscriptions/{subscriptionId}/make-due-now")
    public ResponseEntity<Map<String, Object>> makeDueNow(@PathVariable Long subscriptionId) {

        UUID userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "인증이 필요합니다"));
        }

        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("구독을 찾을 수 없습니다"));

        // 권한 확인
        if (!subscription.getUserId().equals(userId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "권한이 없습니다"));
        }

        if (!subscription.getAutoRenew()) {
            return ResponseEntity.badRequest().body(Map.of("error", "자동 갱신이 비활성화되어 있습니다"));
        }

        // 다음 결제일을 현재 시간으로 설정
        subscription.setNextBillingDate(LocalDateTime.now().minusMinutes(1));
        subscriptionRepository.save(subscription);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "구독이 즉시 갱신 대상이 되었습니다");
        result.put("subscriptionId", subscriptionId);
        result.put("nextBillingDate", subscription.getNextBillingDate());
        result.put("isDueForRenewal", subscription.isDueForRenewal());

        log.info("[테스트] 구독 즉시 갱신 대상으로 설정: subscriptionId={}", subscriptionId);

        return ResponseEntity.ok(result);
    }

    /**
     * 수동으로 구독 갱신 프로세스 실행
     */
    @PostMapping("/subscriptions/process-renewals")
    public ResponseEntity<Map<String, Object>> processRenewals() {
        log.info("[테스트] 수동 구독 갱신 프로세스 실행");

        subscriptionBillingService.processDueSubscriptions();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "갱신 프로세스가 실행되었습니다");
        result.put("executedAt", LocalDateTime.now());

        return ResponseEntity.ok(result);
    }

    /**
     * 수동으로 실패한 결제 재시도
     */
    @PostMapping("/subscriptions/retry-failed")
    public ResponseEntity<Map<String, Object>> retryFailedPayments() {
        log.info("[테스트] 수동 결제 재시도 실행");

        subscriptionBillingService.retryFailedPayments();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "결제 재시도가 실행되었습니다");
        result.put("executedAt", LocalDateTime.now());

        return ResponseEntity.ok(result);
    }

    /**
     * 수동으로 만료 구독 처리
     */
    @PostMapping("/subscriptions/process-expired")
    public ResponseEntity<Map<String, Object>> processExpired() {
        log.info("[테스트] 수동 만료 구독 처리 실행");

        subscriptionBillingService.processExpiredSubscriptions();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "만료 처리가 실행되었습니다");
        result.put("executedAt", LocalDateTime.now());

        return ResponseEntity.ok(result);
    }

    /**
     * 내 구독 결제 이력 조회
     */
    @GetMapping("/subscriptions/{subscriptionId}/payment-history")
    public ResponseEntity<?> getPaymentHistory(@PathVariable Long subscriptionId) {
        UUID userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "인증이 필요합니다"));
        }

        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("구독을 찾을 수 없습니다"));

        // 권한 확인
        if (!subscription.getUserId().equals(userId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "권한이 없습니다"));
        }

        List<SubscriptionPayment> payments = subscriptionPaymentRepository
                .findBySubscriptionIdOrderByBillingDateDesc(subscriptionId);

        return ResponseEntity.ok(payments);
    }

    /**
     * 현재 로그인한 사용자 ID 조회
     */
    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return UUID.fromString(authentication.getName());
        }
        return null;
    }
}
