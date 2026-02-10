package com.bulc.homepage.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Refresh Token Entity for RTR (Refresh Token Rotation).
 *
 * 서버가 현재 유효한 리프레시 토큰을 추적하여:
 * 1. 토큰 탈취 감지 (Token Theft Detection)
 * 2. 단일 사용 보장 (One-Time Use)
 * 3. 강제 로그아웃 지원
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
    @Index(name = "idx_refresh_tokens_user_id", columnList = "user_id"),
    @Index(name = "idx_refresh_tokens_token", columnList = "token"),
    @Index(name = "idx_refresh_tokens_device_id", columnList = "device_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * 실제 Refresh Token 값.
     * RTR의 핵심 - 요청된 토큰과 DB의 토큰이 일치해야만 유효.
     */
    @Column(nullable = false, length = 500)
    private String token;

    /**
     * 디바이스 식별자 (멀티 디바이스 지원용).
     * 같은 유저가 여러 기기에서 로그인 가능하도록 함.
     * null이면 단일 기기 정책.
     */
    @Column(name = "device_id", length = 255)
    private String deviceId;

    /**
     * 디바이스 정보 (User-Agent 등).
     */
    @Column(name = "device_info", length = 500)
    private String deviceInfo;

    /**
     * 토큰 만료 시간.
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * 토큰 생성 시간.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * 마지막 사용 시간 (갱신 시 업데이트).
     */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        lastUsedAt = LocalDateTime.now();
    }

    /**
     * 토큰 값 교체 (RTR 핵심).
     * 새 토큰으로 교체하고 만료 시간 갱신.
     */
    public void rotateToken(String newToken, LocalDateTime newExpiresAt) {
        this.token = newToken;
        this.expiresAt = newExpiresAt;
        this.lastUsedAt = LocalDateTime.now();
    }

    /**
     * 토큰 만료 여부 확인.
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
