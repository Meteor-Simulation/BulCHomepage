package com.bulc.homepage.repository;

import com.bulc.homepage.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    /**
     * 사용자의 구독 목록 조회
     */
    List<Subscription> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * 사용자의 활성 구독 목록 조회
     */
    List<Subscription> findByUserIdAndStatus(UUID userId, String status);

    /**
     * 사용자의 특정 상품 구독 조회
     */
    Optional<Subscription> findByUserIdAndProductCodeAndStatus(UUID userId, String productCode, String status);

    /**
     * 자동 갱신이 설정된 구독 중 결제 예정일이 도래한 구독 조회
     */
    @Query("SELECT s FROM Subscription s WHERE s.autoRenew = true AND s.status = 'A' AND s.nextBillingDate <= :now")
    List<Subscription> findDueForRenewal(@Param("now") LocalDateTime now);

    /**
     * 만료된 구독 조회 (자동갱신 OFF인 경우)
     */
    @Query("SELECT s FROM Subscription s WHERE s.status = 'A' AND s.endDate < :now AND s.autoRenew = false")
    List<Subscription> findExpiredWithoutAutoRenew(@Param("now") LocalDateTime now);

    /**
     * 특정 빌링키를 사용하는 구독 목록 조회
     */
    List<Subscription> findByBillingKeyId(Long billingKeyId);

    /**
     * 사용자에게 활성 구독이 있는지 확인
     */
    boolean existsByUserIdAndStatus(UUID userId, String status);
}
