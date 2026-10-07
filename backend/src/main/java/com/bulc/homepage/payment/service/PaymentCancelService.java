package com.bulc.homepage.payment.service;

import com.bulc.homepage.config.TossPaymentsConfig;
import com.bulc.homepage.payment.domain.Payment;
import com.bulc.homepage.payment.domain.PaymentDetail;
import com.bulc.homepage.payment.repository.PaymentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 결제 취소·환불 (관리자 작업).
 *
 * <p>토스에 승인취소를 요청하고, 성공한 뒤에만 우리 기록을 바꾼다. 순서를 뒤집으면 토스는
 * 살아 있는데 우리만 환불로 적힌 상태가 생겨 대조가 불가능해진다.
 *
 * <p><b>현금 환급으로 처리하면 안 된다.</b> 카드 결제를 승인취소가 아니라 현금으로 돌려주면
 * 가맹점이 수수료만큼 손실을 본다. 반드시 이 경로(원거래 취소)를 쓸 것.
 *
 * <p>가상계좌는 실시간 취소가 불가능하고 환불받을 계좌 정보가 필요하다. 그 경로는 아직
 * 구현하지 않았고, 시도하면 토스가 거부하므로 그 사유를 그대로 올려 보낸다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCancelService {

    private final PaymentRepository paymentRepository;
    private final TossPaymentsConfig tossPaymentsConfig;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    private static final String CANCEL_URL = "https://api.tosspayments.com/v1/payments/%s/cancel";

    /** 결제 상태 — init.sql 규약: P 대기 · C 완료 · F 실패 · R 환불 */
    private static final String STATUS_COMPLETED = "C";
    private static final String STATUS_REFUNDED = "R";

    /**
     * 결제를 취소한다. 전액 취소만 지원한다.
     *
     * <p>부분 취소를 넣지 않은 이유: 부분 취소는 수수료 처리 기준이 PG 계약별로 달라
     * 정산 영향을 먼저 확인해야 하고, 구독 기간을 얼마나 되돌릴지도 정책 결정이 필요하다.
     * 전액 취소만으로 중복 결제·오결제 대응은 충분하다.
     *
     * @param paymentId 취소할 결제 id
     * @param reason    취소 사유 (토스에 그대로 전달되고 우리 기록에도 남는다)
     * @param operator  작업한 관리자 — 로그 추적용
     * @throws PaymentCancelException 이미 환불됨·완료 상태 아님·토스 거부
     */
    @Transactional
    public CancelResult cancel(Long paymentId, String reason, UUID operator) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentCancelException("결제 내역을 찾을 수 없습니다."));

        if (STATUS_REFUNDED.equals(payment.getStatus())) {
            throw new PaymentCancelException("이미 환불된 결제입니다.");
        }
        if (!STATUS_COMPLETED.equals(payment.getStatus())) {
            // 대기(P)·실패(F) 는 취소할 승인이 없다. 토스를 부르기 전에 막는다.
            throw new PaymentCancelException("완료된 결제만 취소할 수 있습니다.");
        }

        PaymentDetail detail = payment.getPaymentDetail();
        if (detail == null || detail.getPaymentKey() == null || detail.getPaymentKey().isBlank()) {
            throw new PaymentCancelException("결제 키가 없어 취소할 수 없습니다. 토스 대시보드에서 직접 처리해 주세요.");
        }

        String cancelReason = (reason == null || reason.isBlank()) ? "관리자 취소" : reason.trim();

        log.info("[결제취소] 요청 - paymentId={}, orderId={}, amount={}, operator={}",
                paymentId, detail.getOrderId(), payment.getAmount(), operator);

        JsonNode tossResponse = requestCancelToToss(detail.getPaymentKey(), cancelReason, detail.getOrderId());

        String tossStatus = tossResponse.path("status").asText(null);
        BigDecimal balance = new BigDecimal(tossResponse.path("balanceAmount").asText("0"));

        // 전액 취소를 요청했으므로 잔액이 0 이어야 한다. 아니면 우리 기록을 환불로 적지 않는다 —
        // 부분 취소 상태를 전액 환불로 적으면 회계가 어긋난다.
        if (balance.compareTo(BigDecimal.ZERO) != 0) {
            log.error("[결제취소] 전액 취소가 아님 - paymentId={}, balanceAmount={}, tossStatus={}",
                    paymentId, balance, tossStatus);
            throw new PaymentCancelException(
                    "토스에서 전액 취소되지 않았습니다(잔액 " + balance + "원). 대시보드를 확인해 주세요.");
        }

        payment.setStatus(STATUS_REFUNDED);
        payment.setRefundedAt(LocalDateTime.now());
        payment.setRefundAmount(payment.getAmount());
        payment.setRefundReason(cancelReason);
        detail.setTossStatus(tossStatus != null ? tossStatus : "CANCELED");
        paymentRepository.save(payment);

        log.info("[결제취소] 완료 - paymentId={}, orderId={}, tossStatus={}, operator={}",
                paymentId, detail.getOrderId(), tossStatus, operator);

        return new CancelResult(paymentId, detail.getOrderId(), payment.getAmount(), tossStatus);
    }

    private JsonNode requestCancelToToss(String paymentKey, String cancelReason, String orderId) {
        HttpHeaders headers = new HttpHeaders();
        String credentials = tossPaymentsConfig.getSecretKey() + ":";
        headers.set("Authorization", "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 같은 키로 다시 요청해도 토스가 한 번만 취소한다 — 버튼 연속 클릭으로 이중 취소가
        // 생기지 않게 한다. paymentKey 기준이므로 재시도에도 안전하다.
        headers.set("Idempotency-Key", "cancel-" + paymentKey);

        Map<String, Object> body = new HashMap<>();
        body.put("cancelReason", cancelReason);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    String.format(CANCEL_URL, paymentKey), new HttpEntity<>(body, headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new PaymentCancelException("토스 취소 요청이 실패했습니다.");
            }
            return objectMapper.readTree(response.getBody());
        } catch (HttpClientErrorException e) {
            String detail = extractTossErrorMessage(e.getResponseBodyAsString());
            log.error("[결제취소] 토스 거부 - orderId={}, status={}, reason={}", orderId, e.getStatusCode(), detail);
            throw new PaymentCancelException(detail);
        } catch (PaymentCancelException e) {
            throw e;
        } catch (Exception e) {
            log.error("[결제취소] 오류 - orderId={}, type={}", orderId, e.getClass().getSimpleName(), e);
            throw new PaymentCancelException("취소 처리 중 오류가 발생했습니다.");
        }
    }

    /** 토스 에러에서 사용자에게 보여줄 사유만 꺼낸다. */
    private String extractTossErrorMessage(String errorBody) {
        try {
            String message = objectMapper.readTree(errorBody).path("message").asText("");
            return message.isBlank() ? "취소에 실패했습니다." : message;
        } catch (Exception e) {
            return "취소에 실패했습니다.";
        }
    }

    public record CancelResult(Long paymentId, String orderId, BigDecimal amount, String tossStatus) {}

    /** 취소 실패 사유를 사용자에게 그대로 보여줄 수 있는 예외. */
    public static class PaymentCancelException extends RuntimeException {
        public PaymentCancelException(String message) {
            super(message);
        }
    }
}
