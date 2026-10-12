package com.bulc.homepage.payment.controller;

import com.bulc.homepage.payment.dto.BillingPaymentRequest;
import com.bulc.homepage.payment.dto.PaymentConfirmRequest;
import com.bulc.homepage.audit.api.ActivityLogPort;
import com.bulc.homepage.payment.service.PaymentService;
import com.bulc.homepage.payment.service.RefundRequestService;
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
    private final ActivityLogPort activityLogPort;
    private final RefundRequestService refundRequestService;

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
            activityLogPort.logPaymentActivity(
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
                activityLogPort.logPaymentActivity(
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
     * 등록 카드(빌링키)로 즉시 결제 API.
     * 결제 페이지에서 등록 카드를 선택해 결제할 때 호출.
     */
    @PostMapping("/billing")
    public ResponseEntity<Map<String, Object>> payWithBillingKey(
            @Valid @RequestBody BillingPaymentRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String userId = null;
            if (authentication != null && authentication.isAuthenticated()
                    && !"anonymousUser".equals(authentication.getPrincipal())) {
                userId = authentication.getName();
            }
            if (userId == null) {
                throw new RuntimeException("로그인이 필요합니다.");
            }

            log.info("[빌링결제 요청] pricePlanId={}, billingKeyId={}, autoRenew={}, userId={}",
                    request.getPricePlanId(), request.getBillingKeyId(), request.isEnableAutoRenew(), userId);

            Map<String, Object> result = paymentService.payWithBillingKey(request, userId, clientIp);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[빌링결제 실패] pricePlanId={}, error={}", request.getPricePlanId(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 본인 결제 내역 조회 (MDP-576). 인증 필수 — SecurityContext의 userId 기준.
     */
    @GetMapping("/me")
    public ResponseEntity<?> getMyPayments() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(paymentService.getMyPaymentHistory(userId));
    }

    /**
     * 환불 신청 (고객). 신청만 접수되고 실제 승인취소는 관리자가 결제관리에서 수행한다.
     *
     * <p>고객이 직접 환불을 실행하게 두지 않는 이유: 환불은 돈이 나가는 일이고 사유 확인·중복
     * 검토가 필요하다. 본인 결제인지는 서버가 다시 확인한다 — 결제 id 는 추측 가능한 숫자다.
     */
    @PostMapping("/me/{paymentId}/refund-request")
    public ResponseEntity<?> requestRefund(@PathVariable Long paymentId,
                                           @RequestBody(required = false) RefundRequestBody body) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }
        UUID userId = UUID.fromString(authentication.getName());
        try {
            var request = refundRequestService.request(paymentId, userId, body != null ? body.reason() : null);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "requestId", request.getId(),
                    "status", request.getStatus()
            ));
        } catch (RefundRequestService.RefundRequestException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    public record RefundRequestBody(String reason) {}

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
