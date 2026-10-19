package com.bulc.homepage.payment.service;

import com.bulc.homepage.catalog.domain.PricePlan;
import com.bulc.homepage.payment.domain.Payment;
import com.bulc.homepage.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 결제 실패를 기록한다 — <b>호출한 트랜잭션이 롤백돼도 남아야 한다.</b>
 *
 * <p>왜 별도 컴포넌트인가 — {@code payWithBillingKey} 는 {@code @Transactional} 이다. 그 안에서
 * 실패 행을 저장하고 예외를 다시 던지면 Spring 이 트랜잭션 전체를 롤백해 그 기록까지 지운다.
 * 실제로 2026-10-13 카드 거절(잔액부족) 때 코드는 실패를 기록하는 모양이었는데
 * {@code payments} 에 status='F' 행이 한 건도 남지 않았다. 기록하는 척하면서 안 하는 쪽이
 * 아예 안 하는 것보다 나쁘다 — 고객이 "결제가 안 됐다" 고 문의할 때 찾을 근거가 없다.
 *
 * <p>{@code REQUIRES_NEW} 로 독립 트랜잭션에서 커밋하므로 바깥이 롤백돼도 살아남는다.
 * 같은 클래스 안의 메서드를 부르면 프록시를 거치지 않아 전파 속성이 무시되므로,
 * 반드시 별도 빈으로 두고 주입해 써야 한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentFailureRecorder {

    private final PaymentRepository paymentRepository;

    /** 결제 상태 — init.sql 규약: P 대기 · C 완료 · F 실패 · R 환불 */
    private static final String STATUS_FAILED = "F";

    /**
     * 빌링키 즉시결제 실패를 남긴다.
     *
     * <p><b>멱등키는 넣지 않는다.</b> 실패 행이 키를 들고 있으면 유니크 제약에 걸려 같은 키로
     * 재시도할 수 없게 된다. 실패한 결제는 다시 시도할 수 있어야 한다.
     *
     * <p>기록 자체가 실패해도 삼킨다 — 결제가 이미 실패한 상황에서 기록 실패로 예외를 덮으면
     * 원래 사유(카드 거절 등)가 사용자에게 전달되지 않는다. 그쪽이 더 큰 손해다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordBillingFailure(UUID userId, String userEmail, String userName,
                                     PricePlan pricePlan, int amount, String orderName,
                                     String clientIp, String reason) {
        try {
            Payment failed = Payment.builder()
                    .amount(BigDecimal.valueOf(amount))
                    .currency(pricePlan != null ? pricePlan.getCurrency() : "KRW")
                    .orderName(orderName)
                    .status(STATUS_FAILED)
                    .userId(userId)
                    .userEmail(userEmail)
                    .userName(userName)
                    .pricePlan(pricePlan)
                    .clientIp(clientIp)
                    .failReason(truncate(reason))
                    .tossKeySet(Payment.KEY_SET_BILLING)
                    .build();
            paymentRepository.save(failed);

            log.info("[결제실패 기록] userId={}, pricePlanId={}, amount={}, reason={}",
                    userId, pricePlan != null ? pricePlan.getId() : null, amount, reason);
        } catch (Exception e) {
            log.error("[결제실패 기록] 기록 자체가 실패 - userId={}, type={}",
                    userId, e.getClass().getSimpleName(), e);
        }
    }

    /** fail_reason 은 TEXT 지만 토스 응답 전문이 들어올 수 있어 적당히 끊는다. */
    private String truncate(String reason) {
        if (reason == null) {
            return "알 수 없는 오류";
        }
        return reason.length() > 1000 ? reason.substring(0, 1000) : reason;
    }
}
