package com.bulc.homepage.licensing.repository;

import com.bulc.homepage.licensing.domain.License;
import com.bulc.homepage.licensing.domain.LicenseStatus;
import com.bulc.homepage.licensing.domain.OwnerType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LicenseRepository extends JpaRepository<License, UUID> {

    /**
     * 라이선스 키로 조회.
     */
    Optional<License> findByLicenseKey(String licenseKey);

    /**
     * 소유자 기준 조회.
     */
    List<License> findByOwnerTypeAndOwnerId(OwnerType ownerType, UUID ownerId);

    /**
     * 소유자의 특정 상태 라이선스 조회.
     */
    List<License> findByOwnerTypeAndOwnerIdAndStatus(OwnerType ownerType, UUID ownerId, LicenseStatus status);

    /**
     * 주문 ID로 조회.
     */
    Optional<License> findBySourceOrderId(UUID sourceOrderId);

    /**
     * 제품별 라이선스 조회.
     */
    List<License> findByProductId(UUID productId);

    /**
     * 소유자의 특정 제품 라이선스 조회.
     */
    Optional<License> findByOwnerTypeAndOwnerIdAndProductId(OwnerType ownerType, UUID ownerId, UUID productId);

    /**
     * 동시성 제어를 위한 비관적 락 조회.
     * MaxConcurrentSessions 검증 시 race condition 방지용.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM License l WHERE l.id = :id")
    Optional<License> findByIdWithLock(@Param("id") UUID id);

    /**
     * 라이선스 키로 비관적 락 조회.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM License l WHERE l.licenseKey = :licenseKey")
    Optional<License> findByLicenseKeyWithLock(@Param("licenseKey") String licenseKey);

    /**
     * 특정 상태의 라이선스 수 조회.
     */
    long countByStatus(LicenseStatus status);

    /**
     * 만료 예정 라이선스 조회 (배치 처리용).
     */
    @Query("SELECT l FROM License l WHERE l.status = 'ACTIVE' AND l.validUntil IS NOT NULL AND l.validUntil < :threshold")
    List<License> findExpiredLicenses(@Param("threshold") java.time.Instant threshold);

    // ==========================================
    // v1.1 추가 메서드
    // ==========================================

    /**
     * 사용자의 특정 제품 라이선스 조회 (여러 상태).
     * 계정 기반 validate에서 사용.
     */
    @Query("SELECT l FROM License l WHERE l.ownerType = :ownerType AND l.ownerId = :ownerId AND l.productId = :productId AND l.status IN :statuses ORDER BY l.status ASC, l.createdAt DESC")
    List<License> findByOwnerAndProductAndStatusIn(
            @Param("ownerType") OwnerType ownerType,
            @Param("ownerId") UUID ownerId,
            @Param("productId") UUID productId,
            @Param("statuses") List<LicenseStatus> statuses
    );

    /**
     * 사용자의 특정 제품 라이선스 조회 (비관적 락).
     * 계정 기반 validate에서 동시성 제어에 사용.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM License l WHERE l.ownerType = :ownerType AND l.ownerId = :ownerId AND l.productId = :productId AND l.status IN :statuses ORDER BY l.status ASC, l.createdAt DESC")
    List<License> findByOwnerAndProductAndStatusInWithLock(
            @Param("ownerType") OwnerType ownerType,
            @Param("ownerId") UUID ownerId,
            @Param("productId") UUID productId,
            @Param("statuses") List<LicenseStatus> statuses
    );

    /**
     * 사용자의 라이선스 목록 조회 (특정 제품 필터 가능).
     */
    @Query("SELECT l FROM License l WHERE l.ownerType = 'USER' AND l.ownerId = :userId " +
            "AND (:productId IS NULL OR l.productId = :productId) " +
            "AND (:status IS NULL OR l.status = :status) " +
            "ORDER BY l.createdAt DESC")
    List<License> findByUserIdWithFilters(
            @Param("userId") UUID userId,
            @Param("productId") UUID productId,
            @Param("status") LicenseStatus status
    );

    /**
     * 사용자의 모든 라이선스 조회 (특정 상태, 제품 필터 없음).
     * 복수 라이선스 선택 로직에서 사용.
     */
    @Query("SELECT l FROM License l WHERE l.ownerType = :ownerType AND l.ownerId = :ownerId " +
            "AND l.status IN :statuses ORDER BY l.status ASC, l.createdAt DESC")
    List<License> findByOwnerAndStatusIn(
            @Param("ownerType") OwnerType ownerType,
            @Param("ownerId") UUID ownerId,
            @Param("statuses") List<LicenseStatus> statuses
    );

    /**
     * 사용자의 모든 라이선스 조회 (비관적 락, 제품 필터 없음).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM License l WHERE l.ownerType = :ownerType AND l.ownerId = :ownerId " +
            "AND l.status IN :statuses ORDER BY l.status ASC, l.createdAt DESC")
    List<License> findByOwnerAndStatusInWithLock(
            @Param("ownerType") OwnerType ownerType,
            @Param("ownerId") UUID ownerId,
            @Param("statuses") List<LicenseStatus> statuses
    );
}
