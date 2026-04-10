package com.bulc.homepage.controller;

import com.bulc.homepage.dto.PaymentConfirmRequest;
import com.bulc.homepage.service.ActivityLogService;
import com.bulc.homepage.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final ActivityLogService activityLogService;

    /**
     * 결제 승인 API
     * 토스페이먼츠 결제창에서 결제 완료 후 호출됨
     */
    @PostMapping("/confirm")
    public ResponseEntity<Map<String, Object>> confirmPayment(
            @Valid @RequestBody PaymentConfirmRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        log.info("[결제승인 요청] orderId={}, amount={}, pricePlanId={}, IP={}, UA={}",
                request.getOrderId(), request.getAmount(), request.getPricePlanId(),
                clientIp, userAgent);

        try {
            // 인증된 사용자 ID 가져오기 (getName()은 userId UUID를 반환)
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String userId = null;
            if (authentication != null && authentication.isAuthenticated()
                    && !"anonymousUser".equals(authentication.getPrincipal())) {
                userId = authentication.getName();
            }

            log.info("[결제승인 인증] orderId={}, userId={}, authenticated={}",
                    request.getOrderId(), userId, userId != null);

            Map<String, Object> result = paymentService.confirmPayment(request, userId, clientIp);

            String paymentStatus = (String) result.get("paymentStatus");
            log.info("[결제승인 성공] orderId={}, paymentStatus={}",
                    request.getOrderId(), paymentStatus);

            // DB 활동 로그 기록 (결제 성공)
            UUID userUuid = userId != null ? UUID.fromString(userId) : null;
            activityLogService.logPaymentActivity(
                    userUuid, request.getOrderId(), paymentStatus,
                    String.format("결제 승인 성공: %s, %d원, pricePlanId=%d",
                            request.getOrderId(), request.getAmount(), request.getPricePlanId()),
                    clientIp, userAgent);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[결제승인 실패] orderId={}, IP={}, error={}",
                    request.getOrderId(), clientIp, e.getMessage(), e);

            // DB 활동 로그 기록 (결제 실패)
            try {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                UUID userUuid = null;
                if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                    userUuid = UUID.fromString(auth.getName());
                }
                activityLogService.logPaymentActivity(
                        userUuid, request.getOrderId(), "FAILED",
                        String.format("결제 승인 실패: %s, %d원, error=%s",
                                request.getOrderId(), request.getAmount(), e.getMessage()),
                        clientIp, userAgent);
            } catch (Exception logError) {
                log.warn("결제 실패 로그 기록 오류: {}", logError.getMessage());
            }

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
            log.error("[결제조회 실패] orderId={}, error={}", orderId, e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 클라이언트 IP 추출 (프록시/CDN 대응)
     */
    private String extractClientIp(HttpServletRequest request) {
        String[] headerNames = {
                "CF-Connecting-IP",     // Cloudflare
                "X-Real-IP",           // Nginx
                "X-Forwarded-For",     // 표준 프록시
        };
        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.contains(",") ? ip.split(",")[0].trim() : ip;
            }
        }
        return request.getRemoteAddr();
    }
}
