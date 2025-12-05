package com.bulc.homepage.licensing.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 기기 활성화 엔티티.
 * 특정 라이선스를 특정 기기 환경에서 사용 중인 "활성화 인스턴스".
 */
@Entity
@Table(name = "license_activations", indexes = {
        @Index(name = "idx_activation_license_id", columnList = "license_id"),
        @Index(name = "idx_activation_device", columnList = "license_id, device_fingerprint")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Activation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "license_id", nullable = false)
    private License license;

    @Column(name = "device_fingerprint", nullable = false, length = 255)
    private String deviceFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ActivationStatus status;

    @Column(name = "activated_at", nullable = false)
    private Instant activatedAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    // === 클라이언트 정보 ===
    @Column(name = "client_version", length = 50)
    private String clientVersion;

    @Column(name = "client_os", length = 100)
    private String clientOs;

    @Column(name = "last_ip", length = 45)  // IPv6 지원
    private String lastIp;

    // === 오프라인 토큰 ===
    @Column(name = "offline_token", length = 2000)
    private String offlineToken;

    @Column(name = "offline_token_expires_at")
    private Instant offlineTokenExpiresAt;

    // === Audit ===
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // === 생성자 (Builder) ===
    @Builder
    private Activation(License license, String deviceFingerprint,
                       String clientVersion, String clientOs, String lastIp) {
        this.license = license;
        this.deviceFingerprint = deviceFingerprint;
        this.status = ActivationStatus.ACTIVE;
        this.activatedAt = Instant.now();
        this.lastSeenAt = Instant.now();
        this.clientVersion = clientVersion;
        this.clientOs = clientOs;
        this.lastIp = lastIp;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // === 도메인 메서드 ===

    /**
     * Heartbeat 갱신 (마지막 접속 시간 업데이트).
     */
    public void updateHeartbeat(String clientVersion, String clientOs, String lastIp) {
        this.lastSeenAt = Instant.now();
        this.clientVersion = clientVersion;
        this.clientOs = clientOs;
        this.lastIp = lastIp;
        this.updatedAt = Instant.now();

        // STALE 상태였다면 ACTIVE로 복귀
        if (this.status == ActivationStatus.STALE) {
            this.status = ActivationStatus.ACTIVE;
        }
    }

    /**
     * 재활성화 (기존 비활성화된 기기를 다시 활성화).
     */
    public void reactivate(String clientVersion, String clientOs, String lastIp) {
        if (this.status == ActivationStatus.EXPIRED) {
            throw new IllegalStateException("만료된 활성화는 재활성화할 수 없습니다.");
        }
        this.status = ActivationStatus.ACTIVE;
        this.lastSeenAt = Instant.now();
        this.clientVersion = clientVersion;
        this.clientOs = clientOs;
        this.lastIp = lastIp;
        this.updatedAt = Instant.now();
    }

    /**
     * 명시적 비활성화 (사용자/관리자에 의해).
     */
    public void deactivate() {
        this.status = ActivationStatus.DEACTIVATED;
        this.updatedAt = Instant.now();
        // 오프라인 토큰도 무효화
        this.offlineToken = null;
        this.offlineTokenExpiresAt = null;
    }

    /**
     * STALE 상태로 전환 (장기 미접속).
     */
    public void markAsStale() {
        if (this.status == ActivationStatus.ACTIVE) {
            this.status = ActivationStatus.STALE;
            this.updatedAt = Instant.now();
        }
    }

    /**
     * 라이선스 만료로 인한 비활성화.
     */
    public void expire() {
        this.status = ActivationStatus.EXPIRED;
        this.updatedAt = Instant.now();
        this.offlineToken = null;
        this.offlineTokenExpiresAt = null;
    }

    /**
     * 오프라인 토큰 발급.
     */
    public void issueOfflineToken(String token, Instant expiresAt) {
        this.offlineToken = token;
        this.offlineTokenExpiresAt = expiresAt;
        this.updatedAt = Instant.now();
    }

    /**
     * 오프라인 토큰 무효화.
     */
    public void revokeOfflineToken() {
        this.offlineToken = null;
        this.offlineTokenExpiresAt = null;
        this.updatedAt = Instant.now();
    }

    /**
     * 오프라인 토큰 유효 여부 확인.
     */
    public boolean hasValidOfflineToken(Instant now) {
        return offlineToken != null
                && offlineTokenExpiresAt != null
                && now.isBefore(offlineTokenExpiresAt);
    }

    /**
     * STALE 상태 판단 (stalePeriodDays 이상 미접속).
     */
    public boolean shouldBeStale(int stalePeriodDays, Instant now) {
        if (this.status != ActivationStatus.ACTIVE) {
            return false;
        }
        Instant staleThreshold = now.minusSeconds(stalePeriodDays * 24L * 60 * 60);
        return this.lastSeenAt.isBefore(staleThreshold);
    }
}
