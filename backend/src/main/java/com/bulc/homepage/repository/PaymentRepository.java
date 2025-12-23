package com.bulc.homepage.repository;

import com.bulc.homepage.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByUserEmailOrderByCreatedAtDesc(String userEmail);

    List<Payment> findByStatus(String status);

    @Query("SELECT p FROM Payment p JOIN p.paymentDetail pd WHERE pd.orderId = :orderId")
    Optional<Payment> findByOrderId(@Param("orderId") String orderId);

    @Query("SELECT p FROM Payment p JOIN p.paymentDetail pd WHERE pd.paymentKey = :paymentKey")
    Optional<Payment> findByPaymentKey(@Param("paymentKey") String paymentKey);

    @Query("SELECT CASE WHEN COUNT(pd) > 0 THEN true ELSE false END FROM PaymentDetail pd WHERE pd.orderId = :orderId")
    boolean existsByOrderId(@Param("orderId") String orderId);
}
