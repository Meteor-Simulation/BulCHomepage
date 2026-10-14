package com.bulc.homepage.payment.service;

import com.bulc.homepage.payment.domain.PaymentDetail;
import com.bulc.homepage.payment.repository.PaymentDetailRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 토스 웹훅 본문을 해석하고 검증해 입금 완료를 반영한다.
 *
 * <h2>왜 분리했나 — 웹훅이 100% 거부되고 있었다</h2>
 *
 * 종전 컨트롤러는 {@code Authorization: Basic base64(시크릿키:)} 헤더를 요구하고 없으면 403 을
 * 돌려줬다. 그런데 토스 웹훅은 그런 헤더를 보내지 않는다 — 보내는 헤더는
 * {@code tosspayments-webhook-transmission-{time,retried-count,id}} 뿐이고,
 * HMAC 서명({@code tosspayments-webhook-signature})은 정산 관련 이벤트에만 붙는다.
 * 그래서 정상 웹훅이 전부 403 으로 거부되고 토스가 재시도하다 포기했다
 * (2026-10-13 로그: 같은 결제가 02:46·02:47·02:51 세 번 거부됨).
 *
 * <h2>본문 구조가 이벤트마다 다르다 — 이게 두 번째 결함이었다</h2>
 *
 * 403 만 걷어내도 가상계좌는 여전히 동작하지 않았다. 두 이벤트의 본문이 아예 다르기 때문이다.
 *
 * <pre>
 * DEPOSIT_CALLBACK (가상계좌 입금)   PAYMENT_STATUS_CHANGED
 * { "createdAt": ...,                { "eventType": "PAYMENT_STATUS_CHANGED",
 *   "secret": ...,                     "createdAt": ...,
 *   "status": "DONE",                  "data": { ...Payment 객체 전체... } }
 *   "transactionKey": ...,
 *   "orderId": ... }
 * </pre>
 *
 * 입금 웹훅에는 {@code eventType} 도 {@code data} 도 <b>{@code paymentKey} 도 없다</b>.
 * 종전 코드는 {@code eventType} 을 읽어 분기하고 {@code data.paymentKey} 를 꺼냈으니,
 * 입금 웹훅이 와도 어느 분기에도 걸리지 않고 조용히 200 만 돌려줬을 것이다.
 * 그래서 평면 구조를 따로 받아 {@code orderId} 로 paymentKey 를 찾는다.
 *
 * <h2>검증은 두 겹이다</h2>
 *
 * <ol>
 *   <li><b>secret 대조</b> — 결제 승인 응답의 {@code secret} 을 저장해 두고 웹훅 본문과 비교한다.
 *       값싸게 위조를 걸러내는 1차 관문이다. 저장값이 NULL 이면(카드 결제·과거 결제) 건너뛴다.</li>
 *   <li><b>토스 재조회</b> — paymentKey 로 상태를 다시 물어 {@code DONE} 일 때만 반영한다.
 *       본문을 아예 신뢰하지 않으므로 이쪽이 실질적인 방어선이다.</li>
 * </ol>
 *
 * <h2>응답 코드가 재시도를 좌우한다</h2>
 *
 * 토스는 2xx 가 아니면 최대 7회(약 3.8일) 재시도한다. 그래서 "지금은 확인할 수 없다"
 * (토스 API 장애 등)와 "처리할 것이 없다"를 구분해야 한다. 전자에 200 을 주면 입금 통보를
 * 영구히 잃는다 — 종전 코드는 재조회 실패에도 200 을 줘서 그 손실을 자초했다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookService {

    private final PaymentDetailRepository paymentDetailRepository;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    /** 웹훅 처리 결과. 컨트롤러가 이걸 보고 HTTP 상태를 정한다. */
    public enum Result {
        /** 입금 완료를 반영했다 → 200 */
        PROCESSED,
        /** 처리할 것이 없다(관심 없는 이벤트, 모르는 주문, 아직 DONE 아님) → 200, 재시도 불필요 */
        IGNORED,
        /** secret 이 맞지 않는다 → 403 */
        REJECTED,
        /** 일시적으로 확인·반영에 실패했다 → 5xx, 토스가 재시도해야 한다 */
        RETRY
    }

    public Result handle(String payload) {
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception e) {
            // 본문이 JSON 이 아니면 재시도해도 같다 — 재시도를 유발하지 않는다.
            log.warn("[웹훅] 본문 파싱 실패 - {}", e.getMessage());
            return Result.IGNORED;
        }

        String eventType = text(root, "eventType");
        JsonNode body;
        String bodyPaymentKey;

        if (eventType == null) {
            // 평면 구조 = 가상계좌 입금 완료(DEPOSIT_CALLBACK). paymentKey 를 보내지 않는다.
            body = root;
            bodyPaymentKey = null;
        } else if ("PAYMENT_STATUS_CHANGED".equals(eventType)) {
            body = root.path("data");
            bodyPaymentKey = text(body, "paymentKey");
        } else {
            log.info("[웹훅] 처리 대상이 아닌 이벤트 - eventType={}", eventType);
            return Result.IGNORED;
        }

        String orderId = text(body, "orderId");
        if (orderId == null) {
            log.warn("[웹훅] orderId 없음 - eventType={}", eventType);
            return Result.IGNORED;
        }

        PaymentDetail detail = paymentDetailRepository.findByOrderId(orderId).orElse(null);
        if (detail == null) {
            // 우리가 만든 주문이 아니다. 재시도해도 생기지 않는다.
            log.warn("[웹훅] 모르는 주문 - orderId={}", orderId);
            return Result.IGNORED;
        }

        // 1차 관문: secret 대조
        String stored = detail.getTossSecret();
        if (stored != null && !stored.equals(text(body, "secret"))) {
            log.warn("[웹훅] secret 불일치 — 위조 요청 의심, orderId={}", orderId);
            return Result.REJECTED;
        }

        String paymentKey = bodyPaymentKey != null ? bodyPaymentKey : detail.getPaymentKey();
        if (paymentKey == null) {
            log.warn("[웹훅] paymentKey 를 찾을 수 없음 - orderId={}", orderId);
            return Result.IGNORED;
        }

        // 2차 관문: 토스에 다시 물어본다. 본문의 status 는 쓰지 않는다.
        String verifiedStatus = paymentService.verifyPaymentStatus(paymentKey);
        if (verifiedStatus == null) {
            // 토스 API 가 일시적으로 응답하지 않는 경우다. 200 을 주면 통보를 영구히 잃는다.
            log.warn("[웹훅] 토스 재조회 실패 — 재시도 요청, orderId={}", orderId);
            return Result.RETRY;
        }

        if (!"DONE".equals(verifiedStatus)) {
            // 입금 대기·취소 등. 완료 웹훅이 따로 오므로 여기서 할 일은 없다.
            log.info("[웹훅] 아직 완료 상태가 아님 - orderId={}, status={}", orderId, verifiedStatus);
            return Result.IGNORED;
        }

        try {
            // 두 이벤트를 모두 등록하면 상태 변경 때 웹훅이 두 번 온다(토스 문서).
            // handlePaymentComplete 는 이미 C 인 결제를 그냥 넘기므로 중복 반영되지 않는다.
            paymentService.handlePaymentComplete(paymentKey, orderId);
            log.info("[웹훅] 입금 완료 처리 성공 - orderId={}", orderId);
            return Result.PROCESSED;
        } catch (Exception e) {
            // 구독 생성·라이선스 발급 중 실패. 재시도로 복구될 수 있다.
            log.error("[웹훅] 완료 처리 실패 — 재시도 요청, orderId={}, error={}", orderId, e.getMessage(), e);
            return Result.RETRY;
        }
    }

    /** 빈 문자열과 누락을 모두 null 로 모은다 — {@code asText(null)} 은 빈 값을 걸러 주지 않는다. */
    private String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return (value == null || value.isBlank()) ? null : value;
    }
}
