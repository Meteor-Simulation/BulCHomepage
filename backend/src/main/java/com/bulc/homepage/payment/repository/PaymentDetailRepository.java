package com.bulc.homepage.payment.repository;

import com.bulc.homepage.payment.domain.PaymentDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentDetailRepository extends JpaRepository<PaymentDetail, Long> {

    Optional<PaymentDetail> findByOrderId(String orderId);

    Optional<PaymentDetail> findByPaymentKey(String paymentKey);

    boolean existsByOrderId(String orderId);
}
