package com.bulc.homepage.licensing.repository;

import com.bulc.homepage.licensing.domain.Activation;
import com.bulc.homepage.licensing.domain.ActivationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivationRepository extends JpaRepository<Activation, UUID> {

    /**
     * 라이선스의 모든 활성화 조회.
     */
    List<Activation> findByLicenseId(UUID licenseId);

    /**
     * 라이선스의 특정 상태 활성화 조회.
     */
    List<Activation> findByLicenseIdAndStatus(UUID licenseId, ActivationStatus status);

    /**
     * 라이선스 + 기기 조합으로 조회.
     */
    Optional<Activation> findByLicenseIdAndDeviceFingerprint(UUID licenseId, String deviceFingerprint);

    /**
     * 라이선스의 활성 상태(ACTIVE, STALE) 활성화 수 조회.
     */
    @Query("SELECT COUNT(a) FROM Activation a WHERE a.license.id = :licenseId AND a.status IN ('ACTIVE', 'STALE')")
    long countActiveByLicenseId(@Param("licenseId") UUID licenseId);

    /**
     * 라이선스의 ACTIVE 상태 활성화 수 조회 (동시 세션 체크용).
     */
    long countByLicenseIdAndStatus(UUID licenseId, ActivationStatus status);

    /**
     * 장기 미접속 활성화 조회 (STALE 전환 배치용).
     */
    @Query("SELECT a FROM Activation a WHERE a.status = 'ACTIVE' AND a.lastSeenAt < :threshold")
    List<Activation> findStaleActivations(@Param("threshold") Instant threshold);

    /**
     * 장기 미접속 활성화 일괄 STALE 처리.
     */
    @Modifying
    @Query("UPDATE Activation a SET a.status = 'STALE', a.updatedAt = :now WHERE a.status = 'ACTIVE' AND a.lastSeenAt < :threshold")
    int markStaleActivations(@Param("threshold") Instant threshold, @Param("now") Instant now);

    /**
     * 라이선스 만료 시 관련 활성화 일괄 EXPIRED 처리.
     */
    @Modifying
    @Query("UPDATE Activation a SET a.status = 'EXPIRED', a.updatedAt = :now, a.offlineToken = null, a.offlineTokenExpiresAt = null WHERE a.license.id = :licenseId AND a.status IN ('ACTIVE', 'STALE')")
    int expireActivationsByLicenseId(@Param("licenseId") UUID licenseId, @Param("now") Instant now);

    /**
     * 특정 기기의 오프라인 토큰 무효화.
     */
    @Modifying
    @Query("UPDATE Activation a SET a.offlineToken = null, a.offlineTokenExpiresAt = null, a.updatedAt = :now WHERE a.id = :activationId")
    int revokeOfflineToken(@Param("activationId") UUID activationId, @Param("now") Instant now);

    // ==========================================
    // v1.1.1 추가 메서드 - 동시 세션 관리
    // ==========================================

    /**
     * 활성 세션 조회 (Session TTL 기반).
     * 활성 세션 조건: status = ACTIVE AND lastSeenAt >= threshold
     * @param licenseId 라이선스 ID
     * @param threshold 세션 만료 기준 시간 (now - sessionTtlMinutes)
     * @return 활성 세션 목록
     */
    @Query("SELECT a FROM Activation a WHERE a.license.id = :licenseId " +
            "AND a.status = 'ACTIVE' AND a.lastSeenAt >= :threshold " +
            "ORDER BY a.lastSeenAt DESC")
    List<Activation> findActiveSessions(@Param("licenseId") UUID licenseId, @Param("threshold") Instant threshold);

    /**
     * 활성 세션 수 조회 (Session TTL 기반).
     * @param licenseId 라이선스 ID
     * @param threshold 세션 만료 기준 시간 (now - sessionTtlMinutes)
     * @return 활성 세션 수
     */
    @Query("SELECT COUNT(a) FROM Activation a WHERE a.license.id = :licenseId " +
            "AND a.status = 'ACTIVE' AND a.lastSeenAt >= :threshold")
    long countActiveSessions(@Param("licenseId") UUID licenseId, @Param("threshold") Instant threshold);

    /**
     * 여러 활성화 ID로 조회 (force deactivate에서 사용).
     */
    List<Activation> findByIdIn(List<UUID> ids);

    // ==========================================
    // v0.3.0 추가 메서드 - Auto-Resolve
    // ==========================================

    /**
     * Stale 세션 조회 (Auto-Resolve용).
     * Stale 조건: status = ACTIVE AND lastSeenAt < staleThreshold
     * 가장 오래된 세션부터 반환 (자동 종료 우선순위).
     *
     * @param licenseId 라이선스 ID
     * @param staleThreshold stale 판정 기준 시간 (now - staleThresholdMinutes)
     * @return stale 세션 목록 (오래된 순)
     */
    @Query("SELECT a FROM Activation a WHERE a.license.id = :licenseId " +
            "AND a.status = 'ACTIVE' AND a.lastSeenAt < :staleThreshold " +
            "ORDER BY a.lastSeenAt ASC")
    List<Activation> findStaleSessions(@Param("licenseId") UUID licenseId, @Param("staleThreshold") Instant staleThreshold);

    /**
     * 여러 라이선스의 활성 세션 일괄 조회 (Global Session Kick용).
     * 모든 후보 라이선스의 세션을 한 번에 조회.
     *
     * @param licenseIds 라이선스 ID 목록
     * @param sessionThreshold 세션 만료 기준 시간 (now - sessionTtlMinutes)
     * @return 모든 라이선스의 활성 세션 목록
     */
    @Query("SELECT a FROM Activation a WHERE a.license.id IN :licenseIds " +
            "AND a.status = 'ACTIVE' AND a.lastSeenAt >= :sessionThreshold " +
            "ORDER BY a.lastSeenAt DESC")
    List<Activation> findActiveSessionsByLicenseIds(@Param("licenseIds") List<UUID> licenseIds,
                                                     @Param("sessionThreshold") Instant sessionThreshold);
}
