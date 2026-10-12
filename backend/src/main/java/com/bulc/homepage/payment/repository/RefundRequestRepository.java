package com.bulc.homepage.payment.repository;

import com.bulc.homepage.payment.domain.RefundRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {

    /** 내 환불 요청 (마이페이지) */
    List<RefundRequest> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** 관리자 목록 — 대기 건을 먼저 보도록 상태로 걸러 쓴다 */
    List<RefundRequest> findByStatusOrderByCreatedAtDesc(String status);

    List<RefundRequest> findAllByOrderByCreatedAtDesc();

    /** 이 결제에 처리 대기 중인 요청 — 중복 신청을 막고, 관리자 처리 시 묶을 요청을 찾는다 */
    Optional<RefundRequest> findByPaymentIdAndStatus(Long paymentId, String status);

    /** 결제 목록 화면에서 "요청 있음" 을 표시하기 위한 조회 */
    List<RefundRequest> findByPaymentIdInAndStatus(List<Long> paymentIds, String status);
}
