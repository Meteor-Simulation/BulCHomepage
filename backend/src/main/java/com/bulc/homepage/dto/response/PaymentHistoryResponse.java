package com.bulc.homepage.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 사용자 본인 결제 내역 응답 (MDP-576).
 * 내부 식별자/IP/PG 원문 등 민감 필드는 제외하고 노출 가능한 항목만 담는다.
 */
public record PaymentHistoryResponse(
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
        BigDecimal refundAmount
) {}
