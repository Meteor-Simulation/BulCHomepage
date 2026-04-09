package com.bulc.homepage.service;

import com.bulc.homepage.config.TossPaymentsConfig;
import com.bulc.homepage.dto.PaymentConfirmRequest;
import com.bulc.homepage.entity.Payment;
import com.bulc.homepage.entity.PaymentDetail;
import com.bulc.homepage.entity.PricePlan;
import com.bulc.homepage.entity.Subscription;
import com.bulc.homepage.licensing.domain.OwnerType;
import com.bulc.homepage.licensing.domain.UsageCategory;
import com.bulc.homepage.licensing.dto.LicenseIssueResult;
import com.bulc.homepage.licensing.service.LicenseService;
import com.bulc.homepage.repository.PaymentRepository;
import com.bulc.homepage.repository.PricePlanRepository;
import com.bulc.homepage.repository.SubscriptionRepository;
import com.bulc.homepage.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
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
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final LicenseService licenseService;
    private final TossPaymentsConfig tossPaymentsConfig;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    /**
     * 토스페이먼츠 결제 승인
     *
     * 카드/계좌이체: 즉시 완료 → 라이선스 발급
     * 가상계좌: 입금 대기 상태 → 웹훅으로 입금 확인 후 라이선스 발급
     */
    @Transactional
    public Map<String, Object> confirmPayment(PaymentConfirmRequest request, String userEmail, String clientIp) {
        log.info("[결제] STEP 1/5 검증 시작 - orderId={}, amount={}, pricePlanId={}, userEmail={}, IP={}",
                request.getOrderId(), request.getAmount(), request.getPricePlanId(), userEmail, clientIp);

        if (userEmail == null || userEmail.isBlank()) {
            log.warn("[결제] 인증 실패 - orderId={}, IP={}", request.getOrderId(), clientIp);
            throw new RuntimeException("사용자 인증이 필요합니다.");
        }

        // 중복 결제 방지
        if (paymentRepository.existsByOrderId(request.getOrderId())) {
            log.warn("[결제] 중복 주문 감지 - orderId={}, userEmail={}", request.getOrderId(), userEmail);
            throw new RuntimeException("이미 처리된 주문입니다: " + request.getOrderId());
        }

        // 요금제 조회
        PricePlan pricePlan = pricePlanRepository.findById(request.getPricePlanId())
                .orElseThrow(() -> {
                    log.error("[결제] 요금제 없음 - pricePlanId={}, orderId={}", request.getPricePlanId(), request.getOrderId());
                    return new RuntimeException("요금제를 찾을 수 없습니다: " + request.getPricePlanId());
                });

        log.info("[결제] STEP 2/5 토스 API 호출 - orderId={}, paymentKey={}", request.getOrderId(), request.getPaymentKey());

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

            log.info("[결제] 토스 API 응답 - orderId={}, httpStatus={}", request.getOrderId(), response.getStatusCode());

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode responseBody = objectMapper.readTree(response.getBody());

                // 토스페이먼츠 결제 상태 확인
                String paymentStatus = responseBody.path("status").asText();
                String method = responseBody.path("method").asText();
                String tossOrderName = responseBody.path("orderName").asText();

                log.info("[결제] STEP 3/5 토스 결제 확인 - orderId={}, tossStatus={}, method={}, orderName={}",
                        request.getOrderId(), paymentStatus, method, tossOrderName);

                // 토스 응답 요약 (민감정보 제외)
                String responseSummary = buildTossResponseSummary(responseBody);

                // 결제 정보 저장
                Payment payment = savePaymentInfo(request, responseBody, userEmail, pricePlan, clientIp, paymentStatus, responseSummary);

                log.info("[결제] STEP 4/5 DB 저장 완료 - orderId={}, paymentId={}, dbStatus={}",
                        request.getOrderId(), payment.getId(), payment.getStatus());

                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("orderId", request.getOrderId());
                result.put("orderName", tossOrderName);
                result.put("amount", request.getAmount());
                result.put("currency", pricePlan.getCurrency());
                result.put("paymentStatus", paymentStatus);

                // 가상계좌는 입금 대기 상태 - 웹훅에서 라이선스 발급
                if ("WAITING_FOR_DEPOSIT".equals(paymentStatus)) {
                    JsonNode virtualAccount = responseBody.path("virtualAccount");
                    result.put("isVirtualAccount", true);
                    result.put("bankName", getBankName(virtualAccount.path("bankCode").asText(null)));
                    result.put("accountNumber", virtualAccount.path("accountNumber").asText());
                    result.put("dueDate", virtualAccount.path("dueDate").asText());
                    result.put("message", "가상계좌가 발급되었습니다. 입금 완료 후 라이선스가 발급됩니다.");

                    log.info("[결제] STEP 5/5 가상계좌 발급 (입금 대기) - orderId={}, 은행={}, 계좌={}",
                            request.getOrderId(), result.get("bankName"), result.get("accountNumber"));
                    return result;
                }

                // 즉시 완료 상태 (카드, 계좌이체 등) - 구독 생성 및 라이선스 발급
                if ("DONE".equals(paymentStatus)) {
                    Subscription subscription = createSubscriptionForPayment(userEmail, pricePlan);
                    result.put("subscriptionId", subscription.getId());
                    result.put("subscriptionEndDate", subscription.getEndDate().toString());

                    log.info("[결제] 구독 생성 완료 - orderId={}, subscriptionId={}, endDate={}",
                            request.getOrderId(), subscription.getId(), subscription.getEndDate());

                    // 라이선스 발급
                    if (pricePlan.getLicensePlanId() != null) {
                        try {
                            UUID userId = UUID.nameUUIDFromBytes(userEmail.getBytes(StandardCharsets.UTF_8));
                            UUID orderId = UUID.nameUUIDFromBytes(request.getOrderId().getBytes(StandardCharsets.UTF_8));

                            LicenseIssueResult licenseResult = licenseService.issueLicenseWithPlanForBilling(
                                    OwnerType.USER,
                                    userId,
                                    pricePlan.getLicensePlanId(),
                                    orderId,
                                    UsageCategory.COMMERCIAL
                            );

                            result.put("licenseKey", licenseResult.licenseKey());
                            result.put("licenseId", licenseResult.id().toString());
                            if (licenseResult.validUntil() != null) {
                                result.put("licenseValidUntil", licenseResult.validUntil().toString());
                            }

                            log.info("[결제] STEP 5/5 라이선스 발급 성공 - orderId={}, licenseKey={}, validUntil={}",
                                    request.getOrderId(), licenseResult.licenseKey(), licenseResult.validUntil());
                        } catch (Exception e) {
                            log.error("[결제] 라이선스 발급 실패 (결제는 완료) - orderId={}, error={}",
                                    request.getOrderId(), e.getMessage(), e);
                            // 실패 사유 DB 기록
                            payment.setFailReason("라이선스 발급 실패: " + e.getMessage());
                            paymentRepository.save(payment);
                            result.put("licenseError", "라이선스 발급 중 오류가 발생했습니다. 고객센터에 문의해주세요.");
                        }
                    } else {
                        log.warn("[결제] 라이선스 플랜 미연결 - orderId={}, pricePlanId={}", request.getOrderId(), pricePlan.getId());
                    }
                } else {
                    log.warn("[결제] 예상 외 토스 상태 - orderId={}, tossStatus={}", request.getOrderId(), paymentStatus);
                }

                return result;
            } else {
                log.error("[결제] 토스 API 비정상 응답 - orderId={}, httpStatus={}, body={}",
                        request.getOrderId(), response.getStatusCode(), response.getBody());
                throw new RuntimeException("결제 승인 실패: 토스 API 응답 " + response.getStatusCode());
            }
        } catch (HttpClientErrorException e) {
            // 토스 API 4xx 에러 (잘못된 요청, 이미 처리된 결제 등)
            String errorBody = e.getResponseBodyAsString();
            String errorDetail = extractTossErrorMessage(errorBody);
            log.error("[결제] 토스 API 클라이언트 에러 - orderId={}, httpStatus={}, tossError={}, body={}",
                    request.getOrderId(), e.getStatusCode(), errorDetail, errorBody);
            saveFailedPayment(request, userEmail, clientIp, "TOSS_CLIENT_ERROR: " + e.getStatusCode() + " - " + errorDetail);
            throw new RuntimeException("결제 승인 실패: " + errorDetail);
        } catch (HttpServerErrorException e) {
            // 토스 API 5xx 에러 (서버 장애)
            log.error("[결제] 토스 API 서버 에러 - orderId={}, httpStatus={}, body={}",
                    request.getOrderId(), e.getStatusCode(), e.getResponseBodyAsString());
            saveFailedPayment(request, userEmail, clientIp, "TOSS_SERVER_ERROR: " + e.getStatusCode());
            throw new RuntimeException("결제 승인 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        } catch (RuntimeException e) {
            // 이미 처리된 RuntimeException은 그대로 전파
            throw e;
        } catch (Exception e) {
            log.error("[결제] 예상 외 오류 - orderId={}, type={}, error={}",
                    request.getOrderId(), e.getClass().getSimpleName(), e.getMessage(), e);
            saveFailedPayment(request, userEmail, clientIp, e.getClass().getSimpleName() + ": " + e.getMessage());
            throw new RuntimeException("결제 승인 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 실패한 결제 정보 DB 기록 (나중에 조회 가능하도록)
     */
    private void saveFailedPayment(PaymentConfirmRequest request, String userEmail, String clientIp, String failReason) {
        try {
            var userOpt = userRepository.findByEmail(userEmail);
            UUID userId = userOpt.map(user -> user.getId()).orElse(null);
            String userName = userOpt.map(user -> user.getName()).orElse(null);

            Payment payment = Payment.builder()
                    .amount(BigDecimal.valueOf(request.getAmount()))
                    .currency("KRW")
                    .status("F")  // Failed
                    .userId(userId)
                    .userEmail(userEmail)
                    .userName(userName)
                    .clientIp(clientIp)
                    .failReason(failReason)
                    .build();

            PaymentDetail detail = PaymentDetail.builder()
                    .payment(payment)
                    .orderId(request.getOrderId())
                    .paymentKey(request.getPaymentKey())
                    .paymentProvider("TOSS")
                    .tossStatus("FAILED")
                    .tossResponseSummary(failReason)
                    .build();

            payment.setPaymentDetail(detail);
            paymentRepository.save(payment);

            log.info("[결제] 실패 기록 DB 저장 - orderId={}, failReason={}", request.getOrderId(), failReason);
        } catch (Exception e) {
            log.error("[결제] 실패 기록 DB 저장 오류 - orderId={}, error={}", request.getOrderId(), e.getMessage());
        }
    }

    /**
     * 토스 API 응답에서 요약 정보 추출 (민감정보 제외)
     */
    private String buildTossResponseSummary(JsonNode responseBody) {
        try {
            Map<String, Object> summary = new HashMap<>();
            summary.put("status", responseBody.path("status").asText());
            summary.put("method", responseBody.path("method").asText());
            summary.put("requestedAt", responseBody.path("requestedAt").asText());
            summary.put("approvedAt", responseBody.path("approvedAt").asText());
            summary.put("totalAmount", responseBody.path("totalAmount").asInt());

            // 카드 정보 (마스킹된 번호만)
            JsonNode card = responseBody.path("card");
            if (!card.isMissingNode()) {
                summary.put("cardCompany", card.path("company").asText());
                summary.put("cardNumber", card.path("number").asText());
            }

            // 간편결제 제공자
            JsonNode easyPay = responseBody.path("easyPay");
            if (!easyPay.isMissingNode() && easyPay.has("provider")) {
                summary.put("easyPayProvider", easyPay.path("provider").asText());
            }

            return objectMapper.writeValueAsString(summary);
        } catch (Exception e) {
            log.warn("[결제] 토스 응답 요약 생성 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 토스 에러 응답에서 메시지 추출
     */
    private String extractTossErrorMessage(String errorBody) {
        try {
            JsonNode errorJson = objectMapper.readTree(errorBody);
            String code = errorJson.path("code").asText("");
            String message = errorJson.path("message").asText("");
            return code + ": " + message;
        } catch (Exception e) {
            return errorBody;
        }
    }

    /**
     * 웹훅을 통한 결제 완료 처리 (가상계좌 입금 완료 등)
     */
    @Transactional
    public void handlePaymentComplete(String paymentKey, String orderId) {
        log.info("[웹훅] 결제 완료 처리 시작 - paymentKey={}, orderId={}", paymentKey, orderId);

        // 결제 정보 조회
        Payment payment = paymentRepository.findByPaymentKey(paymentKey)
                .orElseThrow(() -> {
                    log.error("[웹훅] 결제 정보 없음 - paymentKey={}", paymentKey);
                    return new RuntimeException("결제 정보를 찾을 수 없습니다: paymentKey=" + paymentKey);
                });

        // 이미 완료된 결제인지 확인
        if ("C".equals(payment.getStatus())) {
            log.info("[웹훅] 이미 완료된 결제 - orderId={}, paymentId={}", orderId, payment.getId());
            return;
        }

        // 결제 상태 업데이트
        payment.setStatus("C");
        payment.setPaidAt(LocalDateTime.now());
        paymentRepository.save(payment);

        log.info("[웹훅] 결제 상태 업데이트 - orderId={}, paymentId={}, P→C", orderId, payment.getId());

        // 구독 생성 및 라이선스 발급
        PricePlan pricePlan = payment.getPricePlan();
        if (pricePlan != null) {
            Subscription subscription = createSubscriptionForPayment(payment.getUserEmail(), pricePlan);
            log.info("[웹훅] 구독 생성 성공 - orderId={}, subscriptionId={}", orderId, subscription.getId());

            // 라이선스 발급
            if (pricePlan.getLicensePlanId() != null) {
                try {
                    UUID userId = UUID.nameUUIDFromBytes(payment.getUserEmail().getBytes(StandardCharsets.UTF_8));
                    UUID orderUuid = UUID.nameUUIDFromBytes(orderId.getBytes(StandardCharsets.UTF_8));

                    LicenseIssueResult licenseResult = licenseService.issueLicenseWithPlanForBilling(
                            OwnerType.USER,
                            userId,
                            pricePlan.getLicensePlanId(),
                            orderUuid,
                            UsageCategory.COMMERCIAL
                    );

                    log.info("[웹훅] 라이선스 발급 성공 - orderId={}, licenseKey={}", orderId, licenseResult.licenseKey());
                } catch (Exception e) {
                    log.error("[웹훅] 라이선스 발급 실패 - orderId={}, error={}", orderId, e.getMessage(), e);
                    payment.setFailReason("웹훅 라이선스 발급 실패: " + e.getMessage());
                    paymentRepository.save(payment);
                }
            } else {
                log.warn("[웹훅] 라이선스 플랜 미연결 - orderId={}, pricePlanId={}", orderId, pricePlan.getId());
            }
        } else {
            log.warn("[웹훅] 요금제 미연결 - orderId={}, paymentId={}", orderId, payment.getId());
        }
    }

    /**
     * 결제 완료 시 구독 생성 (1년 구독)
     */
    private Subscription createSubscriptionForPayment(String userEmail, PricePlan pricePlan) {
        UUID userId = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + userEmail))
                .getId();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = now.plusYears(1);
        String billingCycle = "YEARLY";

        Subscription subscription = Subscription.builder()
                .userId(userId)
                .productCode(pricePlan.getProductCode())
                .pricePlan(pricePlan)
                .status("A")
                .startDate(now)
                .endDate(endDate)
                .autoRenew(false)
                .billingCycle(billingCycle)
                .build();

        subscriptionRepository.save(subscription);

        log.info("[구독] 생성 완료 - userId={}, productCode={}, endDate={}", userId, pricePlan.getProductCode(), endDate);
        return subscription;
    }

    /**
     * 결제 정보 저장
     */
    private Payment savePaymentInfo(PaymentConfirmRequest request, JsonNode responseBody,
                                     String userEmail, PricePlan pricePlan, String clientIp,
                                     String tossStatus, String responseSummary) {
        var userOpt = userRepository.findByEmail(userEmail);
        UUID userId = userOpt.map(user -> user.getId()).orElse(null);
        String userName = userOpt.map(user -> user.getName()).orElse(null);

        boolean isCompleted = "DONE".equals(tossStatus);

        Payment payment = Payment.builder()
                .amount(BigDecimal.valueOf(request.getAmount()))
                .currency(pricePlan.getCurrency())
                .orderName(responseBody.path("orderName").asText())
                .status(isCompleted ? "C" : "P")
                .userId(userId)
                .userEmail(userEmail)
                .userName(userName)
                .pricePlan(pricePlan)
                .clientIp(clientIp)
                .paidAt(isCompleted ? LocalDateTime.now() : null)
                .build();

        // PaymentDetail 생성
        String method = responseBody.path("method").asText();
        String paymentMethodCode = convertMethodToCode(method, responseBody);

        PaymentDetail.PaymentDetailBuilder detailBuilder = PaymentDetail.builder()
                .payment(payment)
                .orderId(request.getOrderId())
                .paymentKey(request.getPaymentKey())
                .paymentMethod(paymentMethodCode)
                .paymentProvider("TOSS")
                .tossStatus(tossStatus)
                .tossResponseSummary(responseSummary);

        // 카드 결제 정보 파싱
        if ("CARD".equals(paymentMethodCode)) {
            JsonNode card = responseBody.path("card");
            if (!card.isMissingNode()) {
                detailBuilder.cardCompany(card.path("company").asText(null));
                detailBuilder.cardNumber(card.path("number").asText(null));
                detailBuilder.installmentMonths(card.path("installmentPlanMonths").asInt(0));
                detailBuilder.approveNo(card.path("approveNo").asText(null));
            }
        }

        // 간편결제 정보 파싱
        if (paymentMethodCode != null && paymentMethodCode.startsWith("EASY_PAY")) {
            JsonNode easyPay = responseBody.path("easyPay");
            if (!easyPay.isMissingNode()) {
                detailBuilder.easyPayProvider(easyPay.path("provider").asText(null));
                JsonNode card = responseBody.path("card");
                if (!card.isMissingNode()) {
                    detailBuilder.cardCompany(card.path("company").asText(null));
                    detailBuilder.cardNumber(card.path("number").asText(null));
                    detailBuilder.installmentMonths(card.path("installmentPlanMonths").asInt(0));
                    detailBuilder.approveNo(card.path("approveNo").asText(null));
                }
            }
        }

        // 가상계좌 정보 파싱
        if ("VIRTUAL_ACCOUNT".equals(paymentMethodCode)) {
            JsonNode virtualAccount = responseBody.path("virtualAccount");
            if (!virtualAccount.isMissingNode()) {
                detailBuilder.bankCode(virtualAccount.path("bankCode").asText(null));
                detailBuilder.bankName(getBankName(virtualAccount.path("bankCode").asText(null)));
                detailBuilder.accountNumber(virtualAccount.path("accountNumber").asText(null));
                detailBuilder.depositorName(virtualAccount.path("customerName").asText(null));
                String dueDate = virtualAccount.path("dueDate").asText(null);
                if (dueDate != null && !dueDate.isEmpty()) {
                    try {
                        java.time.OffsetDateTime odt = java.time.OffsetDateTime.parse(dueDate);
                        detailBuilder.dueDate(odt.toLocalDateTime());
                    } catch (Exception e) {
                        try {
                            detailBuilder.dueDate(LocalDateTime.parse(dueDate.replace("Z", "")));
                        } catch (Exception e2) {
                            log.warn("[결제] dueDate 파싱 실패: {}", dueDate);
                        }
                    }
                }
            }
        }

        // 계좌이체 정보 파싱
        if ("TRANSFER".equals(paymentMethodCode)) {
            JsonNode transfer = responseBody.path("transfer");
            if (!transfer.isMissingNode()) {
                detailBuilder.bankCode(transfer.path("bankCode").asText(null));
                detailBuilder.bankName(getBankName(transfer.path("bankCode").asText(null)));
                detailBuilder.settlementStatus(transfer.path("settlementStatus").asText(null));
            }
        }

        PaymentDetail paymentDetail = detailBuilder.build();
        payment.setPaymentDetail(paymentDetail);

        Payment savedPayment = paymentRepository.save(payment);
        log.info("[결제] DB 저장 - paymentId={}, orderId={}, method={}, tossStatus={}, amount={} {}",
                savedPayment.getId(), request.getOrderId(), paymentMethodCode, tossStatus,
                request.getAmount(), pricePlan.getCurrency());

        return savedPayment;
    }

    /**
     * 토스페이먼츠 API로 결제 상태 직접 조회 (웹훅 위변조 방지)
     */
    public String verifyPaymentStatus(String paymentKey) {
        try {
            HttpHeaders headers = createAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    TossPaymentsConfig.TOSS_API_URL + "/" + paymentKey,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            JsonNode body = objectMapper.readTree(response.getBody());
            String status = body.path("status").asText();
            log.info("[토스API] 결제 상태 조회 - paymentKey={}, status={}", paymentKey, status);
            return status;
        } catch (Exception e) {
            log.error("[토스API] 결제 상태 조회 실패 - paymentKey={}, error={}", paymentKey, e.getMessage());
            return null;
        }
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

    /**
     * 토스페이먼츠 결제 수단 판별 (응답 필드 기반으로 안정적 판단)
     */
    private String convertMethodToCode(String method, JsonNode responseBody) {
        JsonNode easyPay = responseBody.path("easyPay");
        if (!easyPay.isMissingNode() && easyPay.has("provider")) {
            String provider = easyPay.path("provider").asText();
            if (provider != null && !provider.isEmpty()) {
                return "EASY_PAY_" + convertProviderToCode(provider);
            }
            return "EASY_PAY";
        }

        JsonNode virtualAccount = responseBody.path("virtualAccount");
        if (!virtualAccount.isMissingNode() && virtualAccount.has("accountNumber")) {
            return "VIRTUAL_ACCOUNT";
        }

        JsonNode transfer = responseBody.path("transfer");
        if (!transfer.isMissingNode() && transfer.has("bankCode")) {
            return "TRANSFER";
        }

        JsonNode mobilePhone = responseBody.path("mobilePhone");
        if (!mobilePhone.isMissingNode()) {
            return "MOBILE";
        }

        JsonNode giftCertificate = responseBody.path("giftCertificate");
        if (!giftCertificate.isMissingNode()) {
            return "GIFT_CARD";
        }

        JsonNode card = responseBody.path("card");
        if (!card.isMissingNode() && card.has("company")) {
            return "CARD";
        }

        if (method != null) {
            String lowerMethod = method.toLowerCase();
            if (lowerMethod.contains("카드") || lowerMethod.contains("card")) return "CARD";
            if (lowerMethod.contains("가상") || lowerMethod.contains("virtual")) return "VIRTUAL_ACCOUNT";
            if (lowerMethod.contains("계좌이체") || lowerMethod.contains("transfer")) return "TRANSFER";
            if (lowerMethod.contains("휴대폰") || lowerMethod.contains("mobile")) return "MOBILE";
            if (lowerMethod.contains("상품권") || lowerMethod.contains("gift")) return "GIFT_CARD";
            if (lowerMethod.contains("간편") || lowerMethod.contains("easy")) return "EASY_PAY";
        }

        log.warn("[결제] 알 수 없는 결제 수단: method={}", method);
        return method != null ? method : "UNKNOWN";
    }

    private String convertProviderToCode(String provider) {
        if (provider == null || provider.isEmpty()) return "UNKNOWN";

        String upper = provider.toUpperCase();
        if (upper.equals("TOSS") || upper.equals("TOSSPAY")) return "TOSS";
        if (upper.equals("NAVER") || upper.equals("NAVERPAY")) return "NAVER";
        if (upper.equals("KAKAO") || upper.equals("KAKAOPAY")) return "KAKAO";
        if (upper.equals("SAMSUNG") || upper.equals("SAMSUNGPAY")) return "SAMSUNG";
        if (upper.equals("APPLE") || upper.equals("APPLEPAY")) return "APPLE";
        if (upper.equals("PAYCO")) return "PAYCO";

        String lower = provider.toLowerCase();
        if (lower.contains("토스") || lower.contains("toss")) return "TOSS";
        if (lower.contains("네이버") || lower.contains("naver")) return "NAVER";
        if (lower.contains("카카오") || lower.contains("kakao")) return "KAKAO";
        if (lower.contains("삼성") || lower.contains("samsung")) return "SAMSUNG";
        if (lower.contains("애플") || lower.contains("apple")) return "APPLE";
        if (lower.contains("페이코") || lower.contains("payco")) return "PAYCO";

        log.warn("[결제] 알 수 없는 간편결제 제공자: {}", provider);
        return upper.replace(" ", "_");
    }

    private String getBankName(String bankCode) {
        if (bankCode == null) return null;
        return switch (bankCode) {
            case "39" -> "경남은행";
            case "34" -> "광주은행";
            case "12" -> "단위농협";
            case "32" -> "부산은행";
            case "45" -> "새마을금고";
            case "64" -> "산림조합";
            case "88" -> "신한은행";
            case "48" -> "신협";
            case "27" -> "씨티은행";
            case "20" -> "우리은행";
            case "71" -> "우체국";
            case "50" -> "저축은행";
            case "37" -> "전북은행";
            case "35" -> "제주은행";
            case "90" -> "카카오뱅크";
            case "89" -> "케이뱅크";
            case "92" -> "토스뱅크";
            case "81" -> "하나은행";
            case "54" -> "HSBC";
            case "03" -> "IBK기업은행";
            case "06" -> "KB국민은행";
            case "31" -> "DGB대구은행";
            case "02" -> "KDB산업은행";
            case "11" -> "NH농협은행";
            case "23" -> "SC제일은행";
            case "07" -> "Sh수협은행";
            default -> bankCode;
        };
    }
}
