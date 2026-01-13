package com.bulc.homepage.repository;

import com.bulc.homepage.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Refresh Token Repository for RTR (Refresh Token Rotation).
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * 사용자 이메일로 Refresh Token 조회 (단일 디바이스 정책).
     */
    Optional<RefreshToken> findByUserEmail(String userEmail);

    /**
     * 사용자 이메일과 디바이스 ID로 Refresh Token 조회 (멀티 디바이스 정책).
     */
    Optional<RefreshToken> findByUserEmailAndDeviceId(String userEmail, String deviceId);

    /**
     * 토큰 값으로 Refresh Token 조회.
     */
    Optional<RefreshToken> findByToken(String token);

    /**
     * 사용자의 모든 Refresh Token 조회 (멀티 디바이스).
     */
    List<RefreshToken> findAllByUserEmail(String userEmail);

    /**
     * 사용자의 모든 Refresh Token 삭제 (강제 로그아웃).
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.userEmail = :userEmail")
    void deleteAllByUserEmail(@Param("userEmail") String userEmail);

    /**
     * 사용자의 특정 디바이스 Refresh Token 삭제.
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.userEmail = :userEmail AND rt.deviceId = :deviceId")
    void deleteByUserEmailAndDeviceId(@Param("userEmail") String userEmail, @Param("deviceId") String deviceId);

    /**
     * 토큰 값으로 삭제.
     */
    @Modifying
    void deleteByToken(String token);

    /**
     * 만료된 토큰 일괄 삭제 (스케줄러에서 호출).
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    int deleteExpiredTokens(@Param("now") LocalDateTime now);

    /**
     * 토큰 존재 여부 확인.
     */
    boolean existsByToken(String token);

    /**
     * 사용자의 활성 세션 수 조회.
     */
    @Query("SELECT COUNT(rt) FROM RefreshToken rt WHERE rt.userEmail = :userEmail AND rt.expiresAt > :now")
    long countActiveSessionsByUserEmail(@Param("userEmail") String userEmail, @Param("now") LocalDateTime now);
}
