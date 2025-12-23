package com.bulc.homepage.service;

import com.bulc.homepage.config.TossPaymentsConfig;
import com.bulc.homepage.dto.PaymentConfirmRequest;
import com.bulc.homepage.entity.Payment;
import com.bulc.homepage.entity.PaymentDetail;
import com.bulc.homepage.repository.PaymentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final TossPaymentsConfig tossPaymentsConfig;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    /**
     * 토스페이먼츠 결제 승인
     */
    @Transactional
    public Map<String, Object> confirmPayment(PaymentConfirmRequest request) {
        log.info("결제 승인 요청: orderId={}, amount={}", request.getOrderId(), request.getAmount());

        // 토스페이먼츠 API 호출
        String url = TossPaymentsConfig.TOSS_API_URL + "/confirm";

        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("paymentKey", request.getPaymentKey());
        body.put("orderId", request.getOrderId());
        body.put("amount", request.getAmount());

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode responseBody = objectMapper.readTree(response.getBody());

                // 결제 정보 저장
                savePaymentInfo(request, responseBody);

                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("orderId", request.getOrderId());
                result.put("orderName", responseBody.path("orderName").asText());
                result.put("amount", request.getAmount());

                log.info("결제 승인 성공: orderId={}", request.getOrderId());
                return result;
            } else {
                throw new RuntimeException("결제 승인 실패");
            }
        } catch (Exception e) {
            log.error("결제 승인 오류: {}", e.getMessage(), e);
            throw new RuntimeException("결제 승인 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 결제 정보 저장
     */
    private void savePaymentInfo(PaymentConfirmRequest request, JsonNode responseBody) {
        // Payment 엔티티 생성
        Payment payment = Payment.builder()
                .amount(BigDecimal.valueOf(request.getAmount()))
                .orderName(responseBody.path("orderName").asText())
                .status("C")  // C: Completed (완료)
                .userEmail(responseBody.path("receipt").path("url").asText()) // 임시 - 실제 구현 시 세션에서 가져옴
                .paidAt(LocalDateTime.now())
                .build();

        // PaymentDetail 엔티티 생성
        PaymentDetail paymentDetail = PaymentDetail.builder()
                .payment(payment)
                .orderId(request.getOrderId())
                .paymentKey(request.getPaymentKey())
                .paymentMethod(responseBody.path("method").asText())
                .paymentProvider("TOSS")
                .build();

        // 양방향 관계 설정
        payment.setPaymentDetail(paymentDetail);

        paymentRepository.save(payment);
        log.info("결제 정보 저장 완료: orderId={}", request.getOrderId());
    }

    /**
     * 토스페이먼츠 인증 헤더 생성
     */
    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String credentials = tossPaymentsConfig.getSecretKey() + ":";
        String encodedCredentials = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + encodedCredentials);
        return headers;
    }

    /**
     * 주문 ID로 결제 정보 조회
     */
    public Payment getPaymentByOrderId(String orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("결제 정보를 찾을 수 없습니다: " + orderId));
    }
}
