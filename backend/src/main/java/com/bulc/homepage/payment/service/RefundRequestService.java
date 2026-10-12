package com.bulc.homepage.payment.service;

import com.bulc.homepage.payment.domain.Payment;
import com.bulc.homepage.payment.domain.PaymentDetail;
import com.bulc.homepage.payment.domain.RefundRequest;
import com.bulc.homepage.payment.repository.PaymentRepository;
import com.bulc.homepage.payment.repository.RefundRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 환불 요청 — 고객 신청과 관리자 처리.
 *
 * <p>고객은 요청만 낸다. 실제 승인취소는 관리자가 승인할 때 {@link PaymentCancelService} 가
 * 수행한다. 환불은 돈이 나가는 일이라 사유 확인·중복 검토를 사람이 거쳐야 한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefundRequestService {

    private final RefundRequestRepository refundRequestRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentCancelService paymentCancelService;

    /** 완료된 결제만 환불 대상이다 (init.sql 규약: P 대기 · C 완료 · F 실패 · R 환불) */
    private static final String PAYMENT_COMPLETED = "C";

    // ── 고객 ──────────────────────────────────────────────────────────────────

    /**
     * 환불을 신청한다.
     *
     * @throws RefundRequestException 남의 결제·완료되지 않은 결제·이미 신청한 결제
     */
    @Transactional
    public RefundRequest request(Long paymentId, UUID userId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new RefundRequestException("환불 사유를 입력해 주세요.");
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RefundRequestException("결제 내역을 찾을 수 없습니다."));

        // 남의 결제에 환불을 걸 수 없다. 결제 id 는 연속 숫자라 추측 가능하므로 반드시 본다.
        if (payment.getUserId() == null || !payment.getUserId().equals(userId)) {
            log.warn("[환불요청] 타인 결제 접근 차단 - paymentId={}, userId={}", paymentId, userId);
            // 남의 결제가 존재한다는 사실조차 알려주지 않는다
            throw new RefundRequestException("결제 내역을 찾을 수 없습니다.");
        }
        if (!PAYMENT_COMPLETED.equals(payment.getStatus())) {
            throw new RefundRequestException("완료된 결제만 환불을 신청할 수 있습니다.");
        }

        RefundRequest request = RefundRequest.builder()
                .paymentId(paymentId)
                .userId(userId)
                .reason(reason.trim())
                .status(RefundRequest.STATUS_PENDING)
                .build();
        try {
            // 결제당 대기 요청은 1건만 (DB 부분 유니크). 연속 클릭·중복 신청이 여기서 걸린다.
            refundRequestRepository.saveAndFlush(request);
        } catch (DataIntegrityViolationException e) {
            throw new RefundRequestException("이미 환불 요청이 접수되어 처리를 기다리고 있습니다.");
        }

        log.info("[환불요청] 접수 - requestId={}, paymentId={}, userId={}", request.getId(), paymentId, userId);
        return request;
    }

    @Transactional(readOnly = true)
    public List<RefundRequest> myRequests(UUID userId) {
        return refundRequestRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    // ── 관리자 ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RefundRequest> list(String status) {
        return (status == null || status.isBlank())
                ? refundRequestRepository.findAllByOrderByCreatedAtDesc()
                : refundRequestRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    /** 결제 목록에 "환불 요청 있음" 을 표시하기 위한 paymentId → 요청 맵 */
    @Transactional(readOnly = true)
    public Map<Long, RefundRequest> pendingByPaymentId(List<Long> paymentIds) {
        if (paymentIds == null || paymentIds.isEmpty()) {
            return Map.of();
        }
        return refundRequestRepository
                .findByPaymentIdInAndStatus(paymentIds, RefundRequest.STATUS_PENDING)
                .stream()
                .collect(Collectors.toMap(RefundRequest::getPaymentId, Function.identity(), (a, b) -> a));
    }

    /**
     * 요청을 승인한다 — 토스 승인취소까지 수행한다.
     *
     * <p>취소가 실패하면 요청 상태를 바꾸지 않는다. 환불이 안 됐는데 "환불 완료" 로 적히면
     * 고객은 받은 줄 알고 기다리게 된다.
     */
    @Transactional
    public RefundRequest approve(Long requestId, UUID operator, String note) {
        RefundRequest request = findPending(requestId);

        // 취소 실패 시 PaymentCancelException 이 그대로 올라간다 — 요청은 PENDING 으로 남는다.
        paymentCancelService.cancel(request.getPaymentId(),
                note != null && !note.isBlank() ? note : "환불 요청 승인: " + request.getReason(),
                operator);

        request.approve(operator, note);
        refundRequestRepository.save(request);

        log.info("[환불요청] 승인 - requestId={}, paymentId={}, operator={}",
                requestId, request.getPaymentId(), operator);
        return request;
    }

    /** 요청을 거절한다. 돈은 움직이지 않는다. */
    @Transactional
    public RefundRequest reject(Long requestId, UUID operator, String note) {
        RefundRequest request = findPending(requestId);
        request.reject(operator, note);
        refundRequestRepository.save(request);

        log.info("[환불요청] 거절 - requestId={}, paymentId={}, operator={}",
                requestId, request.getPaymentId(), operator);
        return request;
    }

    private RefundRequest findPending(Long requestId) {
        RefundRequest request = refundRequestRepository.findById(requestId)
                .orElseThrow(() -> new RefundRequestException("환불 요청을 찾을 수 없습니다."));
        if (!request.isPending()) {
            throw new RefundRequestException("이미 처리된 요청입니다.");
        }
        return request;
    }

    /** 화면에 필요한 값만 담아 내보낸다 — 엔티티를 그대로 넘기지 않는다. */
    public record View(Long id, Long paymentId, String orderId, String userEmail, String userName,
                       BigDecimal amount, String currency, String reason, String status,
                       String adminNote, LocalDateTime createdAt, LocalDateTime processedAt) {}

    @Transactional(readOnly = true)
    public View toView(RefundRequest request) {
        Payment payment = paymentRepository.findById(request.getPaymentId()).orElse(null);
        PaymentDetail detail = payment != null ? payment.getPaymentDetail() : null;
        return new View(
                request.getId(),
                request.getPaymentId(),
                detail != null ? detail.getOrderId() : null,
                payment != null ? payment.getUserEmail() : null,
                payment != null ? payment.getUserName() : null,
                payment != null ? payment.getAmount() : null,
                payment != null ? payment.getCurrency() : null,
                request.getReason(),
                request.getStatus(),
                request.getAdminNote(),
                request.getCreatedAt(),
                request.getProcessedAt());
    }

    /** 요청 처리 실패 사유를 사용자에게 그대로 보여줄 수 있는 예외. */
    public static class RefundRequestException extends RuntimeException {
        public RefundRequestException(String message) {
            super(message);
        }
    }
}
