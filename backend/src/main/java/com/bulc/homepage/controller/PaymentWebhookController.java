package com.bulc.homepage.controller;

import com.bulc.homepage.config.TossPaymentsConfig;
import com.bulc.homepage.service.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

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
    private final TossPaymentsConfig tossPaymentsConfig;

    /**
     * 토스페이먼츠 웹훅 수신
     *
     * 웹훅 이벤트 타입:
     * - PAYMENT_STATUS_CHANGED: 결제 상태 변경 (가상계좌 입금 완료 등)
     * - DEPOSIT_CALLBACK: 가상계좌 입금 콜백 (구버전)
     */
    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Toss-Signature", required = false) String tossSignature) {
        log.info("웹훅 수신: {}", payload);

        // 웹훅 시크릿키가 설정된 경우 서명 검증
        if (!verifyWebhookSignature(payload, tossSignature)) {
            log.warn("웹훅 서명 검증 실패 - 위변조 요청 차단");
            return ResponseEntity.status(403).build();
        }

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
     * 토스페이먼츠 웹훅 서명 검증
     *
     * 토스가 웹훅 시크릿키로 payload를 HMAC-SHA256 서명하여
     * Toss-Signature 헤더에 Base64로 인코딩하여 전송합니다.
     * 서버에서 동일한 방식으로 서명을 생성하여 일치 여부를 확인합니다.
     *
     * 웹훅 시크릿키가 미설정(빈 값)이면 검증을 건너뜁니다.
     */
    private boolean verifyWebhookSignature(String payload, String tossSignature) {
        String webhookSecretKey = tossPaymentsConfig.getWebhookSecretKey();

        // 웹훅 시크릿키가 미설정이면 검증 건너뜀 (개발 환경 등)
        if (webhookSecretKey == null || webhookSecretKey.isBlank()) {
            log.warn("웹훅 시크릿키 미설정 - 서명 검증 건너뜀 (프로덕션에서는 반드시 설정 필요)");
            return true;
        }

        // 서명 헤더가 없으면 거부
        if (tossSignature == null || tossSignature.isBlank()) {
            log.warn("Toss-Signature 헤더 누락");
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    webhookSecretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);

            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = Base64.getEncoder().encodeToString(hmacBytes);

            boolean valid = expectedSignature.equals(tossSignature);
            if (!valid) {
                log.warn("웹훅 서명 불일치 - expected: {}, received: {}", expectedSignature, tossSignature);
            }
            return valid;
        } catch (Exception e) {
            log.error("웹훅 서명 검증 중 오류: {}", e.getMessage(), e);
            return false;
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
