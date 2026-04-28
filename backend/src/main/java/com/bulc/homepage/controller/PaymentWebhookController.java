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
 *
 * 보안:
 * 1. 웹훅 서명 검증 (시크릿 키 기반)
 * 2. 토스 API에 직접 결제 상태 재확인
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
     */
    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody String payload) {
        log.info("웹훅 수신: {}", payload);

        // 1. 웹훅 서명 검증
        if (!verifyWebhookSignature(authorization)) {
            log.warn("웹훅 서명 검증 실패 — 위조 요청 의심");
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

                log.info("웹훅 결제 이벤트: paymentKey={}, orderId={}", paymentKey, orderId);

                // 2. 토스 API에 직접 결제 상태 재확인 (이중 검증)
                String verifiedStatus = paymentService.verifyPaymentStatus(paymentKey);
                if (verifiedStatus == null) {
                    log.warn("토스 API 결제 상태 확인 실패 - 처리 보류: paymentKey={}", paymentKey);
                    return ResponseEntity.ok().build();
                }

                log.info("토스 API 확인 결과: paymentKey={}, status={}", paymentKey, verifiedStatus);

                // 토스 API에서 확인된 상태가 DONE일 때만 처리
                if ("DONE".equals(verifiedStatus)) {
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
     * 웹훅 서명 검증
     *
     * 토스페이먼츠 웹훅은 시크릿 키를 Base64 인코딩하여
     * Authorization: Basic {base64(secretKey:)} 형태로 전송합니다.
     */
    private boolean verifyWebhookSignature(String authorization) {
        if (authorization == null || !authorization.startsWith("Basic ")) {
            log.warn("웹훅 Authorization 헤더 없음 또는 형식 불일치");
            return false;
        }

        try {
            String expectedCredentials = Base64.getEncoder()
                    .encodeToString((tossPaymentsConfig.getSecretKey() + ":").getBytes(StandardCharsets.UTF_8));
            String expectedHeader = "Basic " + expectedCredentials;

            return authorization.equals(expectedHeader);
        } catch (Exception e) {
            log.error("웹훅 서명 검증 오류: {}", e.getMessage());
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
