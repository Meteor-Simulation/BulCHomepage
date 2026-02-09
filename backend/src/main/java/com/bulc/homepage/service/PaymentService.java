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
    public Map<String, Object> confirmPayment(PaymentConfirmRequest request, String userEmail) {
        log.info("결제 승인 요청: orderId={}, amount={}, userEmail={}", request.getOrderId(), request.getAmount(), userEmail);

        if (userEmail == null || userEmail.isBlank()) {
            throw new RuntimeException("사용자 인증이 필요합니다.");
        }

        // 요금제 조회
        PricePlan pricePlan = pricePlanRepository.findById(request.getPricePlanId())
                .orElseThrow(() -> new RuntimeException("요금제를 찾을 수 없습니다: " + request.getPricePlanId()));

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

                // 토스페이먼츠 결제 상태 확인
                String paymentStatus = responseBody.path("status").asText();
                log.info("토스페이먼츠 결제 상태: {}", paymentStatus);

                // 결제 정보 저장
                Payment payment = savePaymentInfo(request, responseBody, userEmail, pricePlan);

                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("orderId", request.getOrderId());
                result.put("orderName", responseBody.path("orderName").asText());
                result.put("amount", request.getAmount());
                result.put("paymentStatus", paymentStatus);

                // 가상계좌는 입금 대기 상태 - 웹훅에서 라이선스 발급
                if ("WAITING_FOR_DEPOSIT".equals(paymentStatus)) {
                    // 가상계좌 정보 반환
                    JsonNode virtualAccount = responseBody.path("virtualAccount");
                    result.put("isVirtualAccount", true);
                    result.put("bankName", getBankName(virtualAccount.path("bankCode").asText(null)));
                    result.put("accountNumber", virtualAccount.path("accountNumber").asText());
                    result.put("dueDate", virtualAccount.path("dueDate").asText());
                    result.put("message", "가상계좌가 발급되었습니다. 입금 완료 후 라이선스가 발급됩니다.");

                    log.info("가상계좌 발급 완료 (입금 대기): orderId={}, 계좌={} {}",
                            request.getOrderId(),
                            result.get("bankName"),
                            result.get("accountNumber"));
                    return result;
                }

                // 즉시 완료 상태 (카드, 계좌이체 등) - 구독 생성 및 라이선스 발급
                if ("DONE".equals(paymentStatus)) {
                    // 구독 생성 (1년 구독)
                    Subscription subscription = createSubscriptionForPayment(userEmail, pricePlan);
                    result.put("subscriptionId", subscription.getId());
                    result.put("subscriptionEndDate", subscription.getEndDate().toString());

                    log.info("결제 승인 및 구독 생성 성공: orderId={}, subscriptionId={}",
                            request.getOrderId(), subscription.getId());

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

                            log.info("라이선스 발급 성공: orderId={}, licenseKey={}",
                                    request.getOrderId(), licenseResult.licenseKey());
                        } catch (Exception e) {
                            log.error("라이선스 발급 실패 (결제는 완료됨): orderId={}, error={}",
                                    request.getOrderId(), e.getMessage(), e);
                            result.put("licenseError", "라이선스 발급 중 오류가 발생했습니다. 고객센터에 문의해주세요.");
                        }
                    } else {
                        log.warn("라이선스 플랜이 연결되지 않은 요금제: pricePlanId={}", pricePlan.getId());
                    }
                }

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
     * 웹훅을 통한 결제 완료 처리 (가상계좌 입금 완료 등)
     */
    @Transactional
    public void handlePaymentComplete(String paymentKey, String orderId) {
        log.info("결제 완료 처리 (웹훅): paymentKey={}, orderId={}", paymentKey, orderId);

        // 결제 정보 조회
        Payment payment = paymentRepository.findByPaymentKey(paymentKey)
                .orElseThrow(() -> new RuntimeException("결제 정보를 찾을 수 없습니다: paymentKey=" + paymentKey));

        // 이미 완료된 결제인지 확인
        if ("C".equals(payment.getStatus())) {
            log.info("이미 완료된 결제입니다: orderId={}", orderId);
            return;
        }

        // 결제 상태 업데이트
        payment.setStatus("C");
        payment.setPaidAt(LocalDateTime.now());
        paymentRepository.save(payment);

        // 구독 생성 및 라이선스 발급
        PricePlan pricePlan = payment.getPricePlan();
        if (pricePlan != null) {
            Subscription subscription = createSubscriptionForPayment(payment.getUserEmail(), pricePlan);
            log.info("웹훅을 통한 구독 생성 성공: orderId={}, subscriptionId={}",
                    orderId, subscription.getId());

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

                    log.info("웹훅을 통한 라이선스 발급 성공: orderId={}, licenseKey={}",
                            orderId, licenseResult.licenseKey());
                } catch (Exception e) {
                    log.error("웹훅 라이선스 발급 실패: orderId={}, error={}", orderId, e.getMessage(), e);
                }
            } else {
                log.warn("라이선스 플랜이 연결되지 않은 요금제: pricePlanId={}", pricePlan.getId());
            }
        } else {
            log.warn("요금제가 연결되지 않은 결제: orderId={}", orderId);
        }
    }

    /**
     * 결제 완료 시 구독 생성 (1년 구독)
     */
    private Subscription createSubscriptionForPayment(String userEmail, PricePlan pricePlan) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = now.plusYears(1);  // 1년 구독
        String billingCycle = "YEARLY";  // 1년 구독

        Subscription subscription = Subscription.builder()
                .userEmail(userEmail)
                .productCode(pricePlan.getProductCode())
                .pricePlan(pricePlan)
                .status("A")  // Active
                .startDate(now)
                .endDate(endDate)
                .autoRenew(false)  // 기본적으로 자동 갱신 비활성화
                .billingCycle(billingCycle)
                .build();

        subscriptionRepository.save(subscription);

        log.info("구독 생성 완료: userEmail={}, productCode={}, endDate={}, billingCycle={}",
                userEmail, pricePlan.getProductCode(), endDate, billingCycle);

        return subscription;
    }

    /**
     * 결제 정보 저장
     */
    private Payment savePaymentInfo(PaymentConfirmRequest request, JsonNode responseBody,
                                     String userEmail, PricePlan pricePlan) {
        // 사용자 이름 조회
        String userName = userRepository.findByEmail(userEmail)
                .map(user -> user.getName())
                .orElse(null);

        // 결제 상태 확인
        String paymentStatus = responseBody.path("status").asText();
        boolean isCompleted = "DONE".equals(paymentStatus);

        // Payment 엔티티 생성
        // P: Pending(대기), C: Completed(완료)
        Payment payment = Payment.builder()
                .amount(BigDecimal.valueOf(request.getAmount()))
                .orderName(responseBody.path("orderName").asText())
                .status(isCompleted ? "C" : "P")  // 가상계좌는 입금 대기 상태
                .userEmail(userEmail)
                .userEmailFk(userEmail)
                .userName(userName)
                .pricePlan(pricePlan)
                .paidAt(isCompleted ? LocalDateTime.now() : null)  // 완료 시에만 결제 시간 설정
                .build();

        // PaymentDetail 엔티티 생성
        String method = responseBody.path("method").asText();
        String paymentMethodCode = convertMethodToCode(method, responseBody);

        PaymentDetail.PaymentDetailBuilder detailBuilder = PaymentDetail.builder()
                .payment(payment)
                .orderId(request.getOrderId())
                .paymentKey(request.getPaymentKey())
                .paymentMethod(paymentMethodCode)
                .paymentProvider("TOSS");

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
                // 간편결제 내 카드 결제인 경우 카드 정보도 저장
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
                        // ISO 8601 형식 (timezone offset 포함) 파싱
                        java.time.OffsetDateTime odt = java.time.OffsetDateTime.parse(dueDate);
                        detailBuilder.dueDate(odt.toLocalDateTime());
                    } catch (Exception e) {
                        // fallback: Z 제거 후 LocalDateTime 파싱 시도
                        try {
                            detailBuilder.dueDate(LocalDateTime.parse(dueDate.replace("Z", "")));
                        } catch (Exception e2) {
                            log.warn("dueDate 파싱 실패: {}", dueDate);
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

    /**
     * 토스페이먼츠 결제 수단 판별 (응답 필드 기반으로 안정적 판단)
     * 한글 비교 대신 응답 JSON의 필드 존재 여부로 결제 수단 판단
     */
    private String convertMethodToCode(String method, JsonNode responseBody) {
        // 응답 JSON의 필드 존재 여부로 결제 수단 판단 (인코딩 문제 방지)

        // 간편결제 체크 (easyPay 필드 존재)
        JsonNode easyPay = responseBody.path("easyPay");
        if (!easyPay.isMissingNode() && easyPay.has("provider")) {
            String provider = easyPay.path("provider").asText();
            if (provider != null && !provider.isEmpty()) {
                return "EASY_PAY_" + convertProviderToCode(provider);
            }
            return "EASY_PAY";
        }

        // 가상계좌 체크 (virtualAccount 필드 존재)
        JsonNode virtualAccount = responseBody.path("virtualAccount");
        if (!virtualAccount.isMissingNode() && virtualAccount.has("accountNumber")) {
            return "VIRTUAL_ACCOUNT";
        }

        // 계좌이체 체크 (transfer 필드 존재)
        JsonNode transfer = responseBody.path("transfer");
        if (!transfer.isMissingNode() && transfer.has("bankCode")) {
            return "TRANSFER";
        }

        // 휴대폰 결제 체크 (mobilePhone 필드 존재)
        JsonNode mobilePhone = responseBody.path("mobilePhone");
        if (!mobilePhone.isMissingNode()) {
            return "MOBILE";
        }

        // 상품권 결제 체크 (giftCertificate 필드 존재)
        JsonNode giftCertificate = responseBody.path("giftCertificate");
        if (!giftCertificate.isMissingNode()) {
            return "GIFT_CARD";
        }

        // 카드 결제 체크 (card 필드 존재) - 기본값
        JsonNode card = responseBody.path("card");
        if (!card.isMissingNode() && card.has("company")) {
            return "CARD";
        }

        // fallback: method 문자열 기반 판단 (한글 포함 가능)
        if (method != null) {
            String lowerMethod = method.toLowerCase();
            if (lowerMethod.contains("카드") || lowerMethod.contains("card")) return "CARD";
            if (lowerMethod.contains("가상") || lowerMethod.contains("virtual")) return "VIRTUAL_ACCOUNT";
            if (lowerMethod.contains("계좌이체") || lowerMethod.contains("transfer")) return "TRANSFER";
            if (lowerMethod.contains("휴대폰") || lowerMethod.contains("mobile")) return "MOBILE";
            if (lowerMethod.contains("상품권") || lowerMethod.contains("gift")) return "GIFT_CARD";
            if (lowerMethod.contains("간편") || lowerMethod.contains("easy")) return "EASY_PAY";
        }

        // 최종 fallback
        log.warn("알 수 없는 결제 수단: method={}", method);
        return method != null ? method : "UNKNOWN";
    }

    /**
     * 간편결제 제공자 → 영문 코드 변환 (인코딩 안전)
     */
    private String convertProviderToCode(String provider) {
        if (provider == null || provider.isEmpty()) {
            return "UNKNOWN";
        }

        // 영문 코드가 이미 있는 경우 그대로 반환
        String upper = provider.toUpperCase();
        if (upper.equals("TOSS") || upper.equals("TOSSPAY")) return "TOSS";
        if (upper.equals("NAVER") || upper.equals("NAVERPAY")) return "NAVER";
        if (upper.equals("KAKAO") || upper.equals("KAKAOPAY")) return "KAKAO";
        if (upper.equals("SAMSUNG") || upper.equals("SAMSUNGPAY")) return "SAMSUNG";
        if (upper.equals("APPLE") || upper.equals("APPLEPAY")) return "APPLE";
        if (upper.equals("PAYCO")) return "PAYCO";

        // 한글 포함 여부로 판단 (인코딩 문제 방지)
        String lower = provider.toLowerCase();
        if (lower.contains("토스") || lower.contains("toss")) return "TOSS";
        if (lower.contains("네이버") || lower.contains("naver")) return "NAVER";
        if (lower.contains("카카오") || lower.contains("kakao")) return "KAKAO";
        if (lower.contains("삼성") || lower.contains("samsung")) return "SAMSUNG";
        if (lower.contains("애플") || lower.contains("apple")) return "APPLE";
        if (lower.contains("페이코") || lower.contains("payco")) return "PAYCO";

        // fallback
        log.warn("알 수 없는 간편결제 제공자: {}", provider);
        return upper.replace(" ", "_");
    }

    /**
     * 은행 코드 → 은행명 변환
     */
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
