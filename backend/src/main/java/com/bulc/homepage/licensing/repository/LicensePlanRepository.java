package com.bulc.homepage.licensing.repository;

import com.bulc.homepage.licensing.domain.LicensePlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 라이선스 플랜 Repository.
 */
@Repository
public interface LicensePlanRepository extends JpaRepository<LicensePlan, UUID> {

    /**
     * 코드로 플랜 조회 (삭제되지 않은 것만).
     */
    Optional<LicensePlan> findByCodeAndDeletedFalse(String code);

    /**
     * 삭제되지 않은 플랜 목록 (페이지네이션).
     */
    Page<LicensePlan> findAllByDeletedFalse(Pageable pageable);

    /**
     * 삭제되지 않고 활성화된 플랜 목록 (페이지네이션).
     */
    Page<LicensePlan> findAllByDeletedFalseAndActiveTrue(Pageable pageable);

    /**
     * 특정 제품의 플랜 목록 (삭제되지 않은 것만).
     */
    Page<LicensePlan> findAllByDeletedFalseAndProductId(UUID productId, Pageable pageable);

    /**
     * 특정 제품의 활성화된 플랜 목록 (삭제되지 않은 것만).
     */
    Page<LicensePlan> findAllByDeletedFalseAndActiveTrueAndProductId(UUID productId, Pageable pageable);

    /**
     * ID로 삭제되지 않은 플랜 조회.
     */
    Optional<LicensePlan> findByIdAndDeletedFalse(UUID id);

    /**
     * ID로 활성화되고 삭제되지 않은 플랜 조회.
     * 라이선스 발급 시 사용.
     */
    @Query("SELECT p FROM LicensePlan p LEFT JOIN FETCH p.entitlements WHERE p.id = :id AND p.deleted = false AND p.active = true")
    Optional<LicensePlan> findAvailableById(@Param("id") UUID id);

    /**
     * 코드로 활성화되고 삭제되지 않은 플랜 조회.
     * 라이선스 발급 시 사용.
     */
    @Query("SELECT p FROM LicensePlan p LEFT JOIN FETCH p.entitlements WHERE p.code = :code AND p.deleted = false AND p.active = true")
    Optional<LicensePlan> findAvailableByCode(@Param("code") String code);

    /**
     * 플랜 코드 중복 여부 확인.
     */
    boolean existsByCodeAndDeletedFalse(String code);

    /**
     * 특정 제품에 속한 플랜 수.
     */
    long countByProductIdAndDeletedFalse(UUID productId);
}
