package com.bulc.homepage.repository;

import com.bulc.homepage.entity.BillingKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingKeyRepository extends JpaRepository<BillingKey, Long> {

    /**
     * 사용자의 활성화된 빌링키 목록 조회
     */
    List<BillingKey> findByUserIdAndIsActiveTrue(UUID userId);

    /**
     * 사용자의 기본 빌링키 조회
     */
    Optional<BillingKey> findByUserIdAndIsDefaultTrueAndIsActiveTrue(UUID userId);

    /**
     * 사용자의 모든 빌링키 목록 조회
     */
    List<BillingKey> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * customerKey로 빌링키 조회
     */
    Optional<BillingKey> findByCustomerKey(String customerKey);

    /**
     * 특정 빌링키 조회 (활성화된 것만)
     */
    Optional<BillingKey> findByIdAndIsActiveTrue(Long id);

    /**
     * 사용자의 기본 빌링키 해제
     */
    @Modifying
    @Query("UPDATE BillingKey b SET b.isDefault = false WHERE b.userId = :userId AND b.isDefault = true")
    void unsetDefaultByUserId(@Param("userId") UUID userId);

    /**
     * 사용자에게 활성화된 빌링키가 있는지 확인
     */
    boolean existsByUserIdAndIsActiveTrue(UUID userId);
}
