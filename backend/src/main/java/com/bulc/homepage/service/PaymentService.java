package com.bulc.homepage.service;

import com.bulc.homepage.config.TossPaymentsConfig;
import com.bulc.homepage.dto.PaymentConfirmRequest;
import com.bulc.homepage.entity.Payment;
import com.bulc.homepage.entity.PaymentDetail;
import com.bulc.homepage.entity.PricePlan;
import com.bulc.homepage.licensing.domain.OwnerType;
import com.bulc.homepage.licensing.domain.UsageCategory;
import com.bulc.homepage.licensing.dto.LicenseIssueResult;
import com.bulc.homepage.licensing.service.LicenseService;
import com.bulc.homepage.repository.PaymentRepository;
import com.bulc.homepage.repository.PricePlanRepository;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PricePlanRepository pricePlanRepository;
    private final LicenseService licenseService;
    private final TossPaymentsConfig tossPaymentsConfig;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    /**
     * 토스페이먼츠 결제 승인
     */
    @Transactional
    public Map<String, Object> confirmPayment(PaymentConfirmRequest request, String userEmail) {
        log.info("결제 승인 요청: orderId={}, amount={}, userEmail={}", request.getOrderId(), request.getAmount(), userEmail);

        if (userEmail == null || userEmail.isBlank()) {
            throw new RuntimeException("사용자 인증이 필요합니다.");
        }

        // 요금제 조회
        PricePlan pricePlan = pricePlanRepository.findById(request.getPricePlanId())
                .orElseThrow(() -> new RuntimeException("요금제를 찾을 수 없습니다: " + request.getPricePlanId()));

        if (pricePlan.getLicensePlanId() == null) {
            throw new RuntimeException("해당 요금제에 연결된 라이선스 플랜이 없습니다.");
        }

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
                Payment payment = savePaymentInfo(request, responseBody, userEmail, pricePlan);

                // 라이선스 발급
                LicenseIssueResult licenseResult = issueLicense(userEmail, pricePlan, payment.getId());

                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("orderId", request.getOrderId());
                result.put("orderName", responseBody.path("orderName").asText());
                result.put("amount", request.getAmount());
                result.put("licenseKey", licenseResult.licenseKey());
                result.put("licenseValidUntil", licenseResult.validUntil());

                log.info("결제 승인 및 라이선스 발급 성공: orderId={}, licenseKey={}",
                        request.getOrderId(), licenseResult.licenseKey());
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
     * 라이선스 발급
     */
    private LicenseIssueResult issueLicense(String userEmail, PricePlan pricePlan, Long paymentId) {
        // 사용자 이메일을 기반으로 deterministic UUID 생성
        UUID userId = UUID.nameUUIDFromBytes(userEmail.getBytes(StandardCharsets.UTF_8));

        // 결제 ID를 기반으로 sourceOrderId 생성
        UUID sourceOrderId = UUID.nameUUIDFromBytes(("payment-" + paymentId).getBytes(StandardCharsets.UTF_8));

        log.info("라이선스 발급 시작: userEmail={}, userId={}, licensePlanId={}",
                userEmail, userId, pricePlan.getLicensePlanId());

        LicenseIssueResult result = licenseService.issueLicenseWithPlanForBilling(
                OwnerType.USER,
                userId,
                pricePlan.getLicensePlanId(),
                sourceOrderId,
                UsageCategory.COMMERCIAL
        );

        log.info("라이선스 발급 완료: licenseId={}, licenseKey={}, validUntil={}",
                result.id(), result.licenseKey(), result.validUntil());

        return result;
    }

    /**
     * 결제 정보 저장
     */
    private Payment savePaymentInfo(PaymentConfirmRequest request, JsonNode responseBody,
                                     String userEmail, PricePlan pricePlan) {
        // Payment 엔티티 생성
        Payment payment = Payment.builder()
                .amount(BigDecimal.valueOf(request.getAmount()))
                .orderName(responseBody.path("orderName").asText())
                .status("C")  // C: Completed (완료)
                .userEmail(userEmail)
                .userEmailFk(userEmail)
                .pricePlan(pricePlan)
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

        Payment savedPayment = paymentRepository.save(payment);
        log.info("결제 정보 저장 완료: orderId={}, paymentId={}", request.getOrderId(), savedPayment.getId());

        return savedPayment;
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
