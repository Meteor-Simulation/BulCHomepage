package com.bulc.homepage.repository;

import com.bulc.homepage.entity.SubscriptionPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, Long> {

    /**
     * 구독의 결제 이력 조회
     */
    List<SubscriptionPayment> findBySubscriptionIdOrderByBillingDateDesc(Long subscriptionId);

    /**
     * 특정 날짜에 결제 대상인 항목 조회
     */
    @Query("SELECT sp FROM SubscriptionPayment sp WHERE sp.billingDate = :billingDate AND sp.status = 'PENDING'")
    List<SubscriptionPayment> findPendingByBillingDate(@Param("billingDate") LocalDate billingDate);

    /**
     * 실패한 결제 중 재시도 가능한 항목 조회 (retry_count < 3)
     */
    @Query("SELECT sp FROM SubscriptionPayment sp WHERE sp.status = 'FAILED' AND sp.retryCount < 3")
    List<SubscriptionPayment> findRetryablePayments();

    /**
     * 특정 구독의 최근 결제 조회
     */
    Optional<SubscriptionPayment> findFirstBySubscriptionIdOrderByBillingDateDesc(Long subscriptionId);

    /**
     * orderId로 결제 조회
     */
    Optional<SubscriptionPayment> findByOrderId(String orderId);

    /**
     * 특정 구독의 성공한 결제 수 조회
     */
    @Query("SELECT COUNT(sp) FROM SubscriptionPayment sp WHERE sp.subscriptionId = :subscriptionId AND sp.status = 'SUCCESS'")
    long countSuccessfulPayments(@Param("subscriptionId") Long subscriptionId);

    /**
     * 특정 기간의 결제 이력 조회
     */
    @Query("SELECT sp FROM SubscriptionPayment sp WHERE sp.billingDate BETWEEN :startDate AND :endDate ORDER BY sp.billingDate DESC")
    List<SubscriptionPayment> findByBillingDateBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
