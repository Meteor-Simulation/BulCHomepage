package com.bulc.homepage.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 사용자 본인 결제 내역 응답 (MDP-576).
 * 내부 식별자/IP/PG 원문 등 민감 필드는 제외하고 노출 가능한 항목만 담는다.
 */
public record PaymentHistoryResponse(
        /**
         * 결제 id — 환불 신청에 쓴다. 본인 결제만 내려오고 신청 시 서버가 소유자를 다시
         * 확인하므로(RefundRequestService.request) 남의 결제에 손댈 수는 없다.
         */
        Long id,
        String orderId,
        String orderName,
        BigDecimal amount,
        String currency,
        String status,        // PENDING / COMPLETED / FAILED / REFUNDED
        String paymentMethod, // CARD / VIRTUAL_ACCOUNT / EASY_PAY / ...
        String cardCompany,
        String cardNumber,    // 마스킹된 카드번호
        LocalDateTime paidAt,
        LocalDateTime createdAt,
        LocalDateTime refundedAt,
        BigDecimal refundAmount,
        /** 이 결제의 환불 요청 상태 — PENDING / APPROVED / REJECTED. 신청 이력이 없으면 null */
        String refundRequestStatus,
        /** 관리자 메모 (거절 사유 등) — 고객이 왜 거절됐는지 알 수 있어야 한다 */
        String refundRequestNote
) {}
