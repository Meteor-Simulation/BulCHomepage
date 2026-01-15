package com.bulc.homepage.service;

import com.bulc.homepage.config.TossPaymentsConfig;
import com.bulc.homepage.dto.request.BillingKeyIssueRequest;
import com.bulc.homepage.dto.response.BillingKeyResponse;
import com.bulc.homepage.entity.BillingKey;
import com.bulc.homepage.repository.BillingKeyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingKeyService {

    private final BillingKeyRepository billingKeyRepository;
    private final TossPaymentsConfig tossPaymentsConfig;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    private static final String BILLING_AUTH_URL = "https://api.tosspayments.com/v1/billing/authorizations/issue";
    private static final String BILLING_PAYMENT_URL = "https://api.tosspayments.com/v1/billing";

    /**
     * 빌링키 발급 (authKey를 사용하여 빌링키 발급)
     */
    @Transactional
    public BillingKeyResponse issueBillingKey(BillingKeyIssueRequest request, String userEmail) {
        log.info("빌링키 발급 요청: userEmail={}, authKey={}", userEmail, request.getAuthKey());

        // customerKey 생성 (사용자별 고유 키)
        String customerKey = UUID.nameUUIDFromBytes(userEmail.getBytes(StandardCharsets.UTF_8)).toString();

        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("authKey", request.getAuthKey());
        body.put("customerKey", customerKey);

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(BILLING_AUTH_URL, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode responseBody = objectMapper.readTree(response.getBody());

                String billingKeyValue = responseBody.path("billingKey").asText();
                JsonNode card = responseBody.path("card");

                // 기존 기본 결제 수단 해제 (첫 번째 카드면 기본으로 설정)
                boolean isFirstCard = !billingKeyRepository.existsByUserEmailAndIsActiveTrue(userEmail);
                if (request.isSetAsDefault() || isFirstCard) {
                    billingKeyRepository.unsetDefaultByUserEmail(userEmail);
                }

                // 빌링키 저장
                BillingKey billingKey = BillingKey.builder()
                        .userEmail(userEmail)
                        .billingKey(billingKeyValue)
                        .customerKey(customerKey)
                        .cardCompany(card.path("company").asText(null))
                        .cardNumber(card.path("number").asText(null))
                        .cardType(card.path("cardType").asText(null))
                        .ownerType(card.path("ownerType").asText(null))
                        .isDefault(request.isSetAsDefault() || isFirstCard)
                        .isActive(true)
                        .build();

                billingKey = billingKeyRepository.save(billingKey);
                log.info("빌링키 발급 성공: id={}, cardNumber={}", billingKey.getId(), billingKey.getCardNumber());

                return toBillingKeyResponse(billingKey);
            } else {
                throw new RuntimeException("빌링키 발급 실패");
            }
        } catch (Exception e) {
            log.error("빌링키 발급 오류: {}", e.getMessage(), e);
            throw new RuntimeException("빌링키 발급 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 빌링키로 결제 요청
     */
    @Transactional
    public Map<String, Object> requestBillingPayment(Long billingKeyId, String orderId, String orderName,
                                                      int amount, String userEmail) {
        log.info("빌링 결제 요청: billingKeyId={}, orderId={}, amount={}", billingKeyId, orderId, amount);

        BillingKey billingKey = billingKeyRepository.findByIdAndIsActiveTrue(billingKeyId)
                .orElseThrow(() -> new RuntimeException("유효하지 않은 빌링키입니다."));

        if (!billingKey.getUserEmail().equals(userEmail)) {
            throw new RuntimeException("빌링키 접근 권한이 없습니다.");
        }

        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("customerKey", billingKey.getCustomerKey());
        body.put("amount", amount);
        body.put("orderId", orderId);
        body.put("orderName", orderName);

        try {
            String url = BILLING_PAYMENT_URL + "/" + billingKey.getBillingKey();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode responseBody = objectMapper.readTree(response.getBody());

                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("paymentKey", responseBody.path("paymentKey").asText());
                result.put("orderId", orderId);
                result.put("amount", amount);
                result.put("status", responseBody.path("status").asText());
                result.put("approvedAt", responseBody.path("approvedAt").asText());

                log.info("빌링 결제 성공: orderId={}, paymentKey={}", orderId, result.get("paymentKey"));
                return result;
            } else {
                throw new RuntimeException("빌링 결제 실패");
            }
        } catch (Exception e) {
            log.error("빌링 결제 오류: {}", e.getMessage(), e);
            throw new RuntimeException("빌링 결제 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 사용자의 빌링키 목록 조회
     */
    @Transactional(readOnly = true)
    public List<BillingKeyResponse> getUserBillingKeys(String userEmail) {
        return billingKeyRepository.findByUserEmailAndIsActiveTrue(userEmail)
                .stream()
                .map(this::toBillingKeyResponse)
                .collect(Collectors.toList());
    }

    /**
     * 기본 결제 수단 변경
     */
    @Transactional
    public BillingKeyResponse setDefaultBillingKey(Long billingKeyId, String userEmail) {
        BillingKey billingKey = billingKeyRepository.findByIdAndIsActiveTrue(billingKeyId)
                .orElseThrow(() -> new RuntimeException("빌링키를 찾을 수 없습니다."));

        if (!billingKey.getUserEmail().equals(userEmail)) {
            throw new RuntimeException("빌링키 접근 권한이 없습니다.");
        }

        // 기존 기본 결제 수단 해제
        billingKeyRepository.unsetDefaultByUserEmail(userEmail);

        // 새 기본 결제 수단 설정
        billingKey.setAsDefault();
        billingKeyRepository.save(billingKey);

        log.info("기본 결제 수단 변경: billingKeyId={}", billingKeyId);
        return toBillingKeyResponse(billingKey);
    }

    /**
     * 빌링키 삭제 (비활성화)
     */
    @Transactional
    public void deleteBillingKey(Long billingKeyId, String userEmail) {
        BillingKey billingKey = billingKeyRepository.findByIdAndIsActiveTrue(billingKeyId)
                .orElseThrow(() -> new RuntimeException("빌링키를 찾을 수 없습니다."));

        if (!billingKey.getUserEmail().equals(userEmail)) {
            throw new RuntimeException("빌링키 접근 권한이 없습니다.");
        }

        billingKey.deactivate();
        billingKeyRepository.save(billingKey);

        // 만약 기본 결제 수단이었다면 다른 활성 카드를 기본으로 설정
        List<BillingKey> remainingKeys = billingKeyRepository.findByUserEmailAndIsActiveTrue(userEmail);
        if (!remainingKeys.isEmpty() && remainingKeys.stream().noneMatch(BillingKey::getIsDefault)) {
            remainingKeys.get(0).setAsDefault();
            billingKeyRepository.save(remainingKeys.get(0));
        }

        log.info("빌링키 삭제: billingKeyId={}", billingKeyId);
    }

    /**
     * 기본 결제 수단 조회
     */
    @Transactional(readOnly = true)
    public BillingKeyResponse getDefaultBillingKey(String userEmail) {
        return billingKeyRepository.findByUserEmailAndIsDefaultTrueAndIsActiveTrue(userEmail)
                .map(this::toBillingKeyResponse)
                .orElse(null);
    }

    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String credentials = tossPaymentsConfig.getSecretKey() + ":";
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + encodedCredentials);
        return headers;
    }

    private BillingKeyResponse toBillingKeyResponse(BillingKey billingKey) {
        return BillingKeyResponse.builder()
                .id(billingKey.getId())
                .cardCompany(billingKey.getCardCompany())
                .cardNumber(billingKey.getCardNumber())
                .cardType(billingKey.getCardType())
                .ownerType(billingKey.getOwnerType())
                .isDefault(billingKey.getIsDefault())
                .createdAt(billingKey.getCreatedAt())
                .build();
    }
}
