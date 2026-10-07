package com.bulc.homepage.payment.repository;

import com.bulc.homepage.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<Payment> findByStatus(String status);

    @Query("SELECT p FROM Payment p JOIN p.paymentDetail pd WHERE pd.orderId = :orderId")
    Optional<Payment> findByOrderId(@Param("orderId") String orderId);

    @Query("SELECT p FROM Payment p JOIN p.paymentDetail pd WHERE pd.paymentKey = :paymentKey")
    Optional<Payment> findByPaymentKey(@Param("paymentKey") String paymentKey);

    @Query("SELECT CASE WHEN COUNT(pd) > 0 THEN true ELSE false END FROM PaymentDetail pd WHERE pd.orderId = :orderId")
    boolean existsByOrderId(@Param("orderId") String orderId);

    /**
     * 같은 사용자·요금제로 방금 승인된 결제가 있는지 — 빌링키 즉시결제의 중복 승인 방지용.
     *
     * <p>{@code existsByOrderId} 로는 막을 수 없다. 빌링키 경로는 호출마다 orderId 를
     * {@code "BILL-" + planId + "-" + currentTimeMillis()} 로 새로 만들기 때문에, 두 번 클릭하면
     * 서로 다른 주문번호가 생겨 그 중복 검사를 구조적으로 통과한다.
     * (2026-10-07: 2.4초 간격의 두 요청이 모두 승인돼 100원이 2건 청구됐다)
     *
     * <p>취소된 건(status {@code R})은 세지 않는다 — 환불 뒤 다시 결제하는 것은 막을 이유가 없다.
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Payment p "
            + "WHERE p.userId = :userId AND p.pricePlan.id = :pricePlanId "
            + "AND p.status = 'C' AND p.createdAt > :since")
    boolean existsRecentCompleted(@Param("userId") UUID userId,
                                  @Param("pricePlanId") Long pricePlanId,
                                  @Param("since") LocalDateTime since);
}
