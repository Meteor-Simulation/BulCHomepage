package com.bulc.homepage.controller;

import com.bulc.homepage.service.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 토스페이먼츠 웹훅 컨트롤러
 *
 * 가상계좌 입금 완료 등의 이벤트를 처리합니다.
 * 토스페이먼츠에서 결제 상태 변경 시 이 엔드포인트로 POST 요청을 보냅니다.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookController {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    /**
     * 토스페이먼츠 웹훅 수신
     *
     * 웹훅 이벤트 타입:
     * - PAYMENT_STATUS_CHANGED: 결제 상태 변경 (가상계좌 입금 완료 등)
     * - DEPOSIT_CALLBACK: 가상계좌 입금 콜백 (구버전)
     */
    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(@RequestBody String payload) {
        log.info("웹훅 수신: {}", payload);

        try {
            JsonNode webhookData = objectMapper.readTree(payload);

            String eventType = webhookData.path("eventType").asText();
            log.info("웹훅 이벤트 타입: {}", eventType);

            // 결제 상태 변경 이벤트 처리
            if ("PAYMENT_STATUS_CHANGED".equals(eventType) || "DEPOSIT_CALLBACK".equals(eventType)) {
                JsonNode data = webhookData.path("data");
                String paymentKey = data.path("paymentKey").asText();
                String orderId = data.path("orderId").asText();
                String status = data.path("status").asText();

                log.info("결제 상태 변경: paymentKey={}, orderId={}, status={}", paymentKey, orderId, status);

                // 입금 완료(DONE) 상태인 경우 처리
                if ("DONE".equals(status)) {
                    paymentService.handlePaymentComplete(paymentKey, orderId);
                    log.info("입금 완료 처리 성공: orderId={}", orderId);
                }
            }

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("웹훅 처리 오류: {}", e.getMessage(), e);
            // 웹훅은 200 응답을 반환해야 재시도하지 않음
            return ResponseEntity.ok().build();
        }
    }

    /**
     * 웹훅 테스트용 엔드포인트
     */
    @GetMapping("/webhook/test")
    public ResponseEntity<?> testWebhook() {
        return ResponseEntity.ok().body("Webhook endpoint is working");
    }
}
