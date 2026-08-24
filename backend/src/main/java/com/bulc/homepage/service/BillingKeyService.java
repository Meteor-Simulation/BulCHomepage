package com.bulc.homepage.service;

import com.bulc.homepage.config.TossPaymentsConfig;
import com.bulc.homepage.dto.request.BillingKeyIssueRequest;
import com.bulc.homepage.dto.request.CardDirectRegisterRequest;
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
import org.springframework.web.client.HttpClientErrorException;
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
    /** 자체 카드 입력 폼 → 빌링키 직접 발급 (토스 별도 계약 필요) */
    private static final String BILLING_CARD_URL = "https://api.tosspayments.com/v1/billing/authorizations/card";

    /**
     * 사용자별 고유 customerKey 생성.
     *
     * 토스 빌링 인증(requestBillingAuth)과 빌링키 발급(/authorizations/issue)에서
     * 동일한 customerKey를 써야 하므로, 프론트도 이 값을 그대로 사용하도록 노출한다.
     * userId 기반 결정적(deterministic) 생성이라 항상 같은 값이 나온다.
     */
    public String getCustomerKey(UUID userId) {
        return UUID.nameUUIDFromBytes(userId.toString().getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * 빌링키 발급 (authKey를 사용하여 빌링키 발급)
     */
    @Transactional
    public BillingKeyResponse issueBillingKey(BillingKeyIssueRequest request, UUID userId) {
        log.info("빌링키 발급 요청: userId={}", userId);

        // customerKey 생성 (FE requestBillingAuth와 동일 값이어야 함)
        String customerKey = getCustomerKey(userId);

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
                boolean isFirstCard = !billingKeyRepository.existsByUserIdAndIsActiveTrue(userId);
                if (request.isSetAsDefault() || isFirstCard) {
                    billingKeyRepository.unsetDefaultByUserId(userId);
                }

                // 빌링키 저장
                BillingKey billingKey = BillingKey.builder()
                        .userId(userId)
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
     * 자체 카드 입력 폼으로 빌링키 직접 발급 (MDP-758).
     *
     * <p>토스 인증창 대신 우리 화면에서 카드 정보를 받아 토스에 그대로 전달한다.
     * 카드 정보는 이 메서드를 벗어나지 않는다 — 로그·DB 어디에도 남기지 않으며,
     * 저장하는 것은 토스가 돌려준 빌링키와 마스킹된 카드번호뿐이다.
     *
     * <p>주의: 이 API 는 토스 <b>빌링키 직접 발급 계약</b>이 승인된 가맹점만 호출할 수 있다.
     * 미승인 상태에서는 토스가 권한 오류를 반환한다.
     */
    @Transactional
    public BillingKeyResponse registerCardDirect(CardDirectRegisterRequest request, UUID userId) {
        // 카드 정보는 절대 찍지 않는다. 식별에 필요한 값만 남긴다.
        log.info("카드 직접 등록 요청: userId={}", userId);

        String customerKey = getCustomerKey(userId);

        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("customerKey", customerKey);
        body.put("cardNumber", request.getCardNumber());
        body.put("cardExpirationYear", request.getExpiryYear());
        body.put("cardExpirationMonth", request.getExpiryMonth());
        body.put("customerIdentityNumber", request.getIdentityNumber());
        body.put("cardPassword", request.getCardPassword());

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(BILLING_CARD_URL, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("카드 등록에 실패했습니다.");
            }

            JsonNode responseBody = objectMapper.readTree(response.getBody());
            String billingKeyValue = responseBody.path("billingKey").asText();
            JsonNode card = responseBody.path("card");

            boolean isFirstCard = !billingKeyRepository.existsByUserIdAndIsActiveTrue(userId);
            boolean makeDefault = request.isSetAsDefault() || isFirstCard;
            if (makeDefault) {
                billingKeyRepository.unsetDefaultByUserId(userId);
            }

            BillingKey billingKey = BillingKey.builder()
                    .userId(userId)
                    .billingKey(billingKeyValue)
                    .customerKey(customerKey)
                    .cardCompany(card.path("company").asText(null))
                    .cardNumber(card.path("number").asText(null))  // 토스가 마스킹해 돌려준 번호
                    .cardType(card.path("cardType").asText(null))
                    .ownerType(card.path("ownerType").asText(null))
                    .isDefault(makeDefault)
                    .isActive(true)
                    .build();

            billingKey = billingKeyRepository.save(billingKey);
            log.info("카드 직접 등록 성공: id={}, cardNumber={}", billingKey.getId(), billingKey.getCardNumber());

            return toBillingKeyResponse(billingKey);
        } catch (HttpClientErrorException e) {
            // 토스가 돌려준 사유만 꺼내 쓴다. 요청 본문(카드 정보)은 절대 찍지 않는다.
            String detail = extractTossErrorMessage(e.getResponseBodyAsString());
            log.error("카드 직접 등록 실패(토스 4xx): userId={}, status={}, reason={}",
                    userId, e.getStatusCode(), detail);
            throw new RuntimeException(detail);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("카드 직접 등록 오류: userId={}, type={}", userId, e.getClass().getSimpleName());
            throw new RuntimeException("카드 등록 중 오류가 발생했습니다.");
        }
    }

    /** 토스 에러 응답에서 사용자에게 보여줄 메시지만 추출 (실패해도 카드 정보는 노출하지 않는다). */
    private String extractTossErrorMessage(String errorBody) {
        try {
            JsonNode node = objectMapper.readTree(errorBody);
            String message = node.path("message").asText("");
            return message.isBlank() ? "카드 등록에 실패했습니다." : message;
        } catch (Exception e) {
            return "카드 등록에 실패했습니다.";
        }
    }

    /**
     * 빌링키로 결제 요청
     */
    @Transactional
    public Map<String, Object> requestBillingPayment(Long billingKeyId, String orderId, String orderName,
                                                      int amount, UUID userId) {
        log.info("빌링 결제 요청: billingKeyId={}, orderId={}, amount={}", billingKeyId, orderId, amount);

        BillingKey billingKey = billingKeyRepository.findByIdAndIsActiveTrue(billingKeyId)
                .orElseThrow(() -> new RuntimeException("유효하지 않은 빌링키입니다."));

        if (!billingKey.getUserId().equals(userId)) {
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
    public List<BillingKeyResponse> getUserBillingKeys(UUID userId) {
        return billingKeyRepository.findByUserIdAndIsActiveTrue(userId)
                .stream()
                .map(this::toBillingKeyResponse)
                .collect(Collectors.toList());
    }

    /**
     * 기본 결제 수단 변경
     */
    @Transactional
    public BillingKeyResponse setDefaultBillingKey(Long billingKeyId, UUID userId) {
        BillingKey billingKey = billingKeyRepository.findByIdAndIsActiveTrue(billingKeyId)
                .orElseThrow(() -> new RuntimeException("빌링키를 찾을 수 없습니다."));

        if (!billingKey.getUserId().equals(userId)) {
            throw new RuntimeException("빌링키 접근 권한이 없습니다.");
        }

        // 기존 기본 결제 수단 해제
        billingKeyRepository.unsetDefaultByUserId(userId);

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
    public void deleteBillingKey(Long billingKeyId, UUID userId) {
        BillingKey billingKey = billingKeyRepository.findByIdAndIsActiveTrue(billingKeyId)
                .orElseThrow(() -> new RuntimeException("빌링키를 찾을 수 없습니다."));

        if (!billingKey.getUserId().equals(userId)) {
            throw new RuntimeException("빌링키 접근 권한이 없습니다.");
        }

        billingKey.deactivate();
        billingKeyRepository.save(billingKey);

        // 만약 기본 결제 수단이었다면 다른 활성 카드를 기본으로 설정
        List<BillingKey> remainingKeys = billingKeyRepository.findByUserIdAndIsActiveTrue(userId);
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
    public BillingKeyResponse getDefaultBillingKey(UUID userId) {
        return billingKeyRepository.findByUserIdAndIsDefaultTrueAndIsActiveTrue(userId)
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
