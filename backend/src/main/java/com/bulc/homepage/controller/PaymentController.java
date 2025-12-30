package com.bulc.homepage.controller;

import com.bulc.homepage.dto.PaymentConfirmRequest;
import com.bulc.homepage.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 결제 승인 API
     * 토스페이먼츠 결제창에서 결제 완료 후 호출됨
     */
    @PostMapping("/confirm")
    public ResponseEntity<Map<String, Object>> confirmPayment(
            @Valid @RequestBody PaymentConfirmRequest request) {
        try {
            // 인증된 사용자 이메일 가져오기
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String userEmail = null;
            if (authentication != null && authentication.isAuthenticated()
                    && !"anonymousUser".equals(authentication.getPrincipal())) {
                userEmail = authentication.getName();
            }

            Map<String, Object> result = paymentService.confirmPayment(request, userEmail);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("결제 승인 실패: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 결제 정보 조회 API
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<?> getPayment(@PathVariable String orderId) {
        try {
            return ResponseEntity.ok(paymentService.getPaymentByOrderId(orderId));
        } catch (Exception e) {
            log.error("결제 조회 실패: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}
