package com.bulc.homepage.licensing.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 라이선스 Aggregate Root.
 * 특정 Owner(개인/조직)가 특정 Product/Plan을 사용할 수 있는 권리.
 */
@Entity
@Table(name = "licenses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class License {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // === 소유자 정보 ===
    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 20)
    private OwnerType ownerType;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    // === 제품/플랜 정보 ===
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "plan_id")
    private UUID planId;

    // === 라이선스 타입, 용도, 상태 ===
    @Enumerated(EnumType.STRING)
    @Column(name = "license_type", nullable = false, length = 20)
    private LicenseType licenseType;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_category", nullable = false, length = 30)
    private UsageCategory usageCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LicenseStatus status;

    // === 기간 ===
    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;  // Perpetual인 경우 null

    // === 정책 스냅샷 (JSON) ===
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_snapshot")
    private Map<String, Object> policySnapshot;

    // === 외부 참조 ===
    @Column(name = "license_key", unique = true, length = 50)
    private String licenseKey;

    @Column(name = "source_order_id")
    private UUID sourceOrderId;

    // === Audit ===
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // === 연관관계 ===
    @OneToMany(mappedBy = "license", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Activation> activations = new ArrayList<>();

    // === 생성자 (Builder) ===
    @Builder
    private License(OwnerType ownerType, UUID ownerId, UUID productId, UUID planId,
                    LicenseType licenseType, UsageCategory usageCategory,
                    Instant validFrom, Instant validUntil,
                    Map<String, Object> policySnapshot, String licenseKey, UUID sourceOrderId) {
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.productId = productId;
        this.planId = planId;
        this.licenseType = licenseType;
        this.usageCategory = usageCategory != null ? usageCategory : UsageCategory.COMMERCIAL;
        this.status = LicenseStatus.PENDING;
        this.issuedAt = Instant.now();
        this.validFrom = validFrom != null ? validFrom : Instant.now();
        this.validUntil = validUntil;
        this.policySnapshot = policySnapshot;
        this.licenseKey = licenseKey;
        this.sourceOrderId = sourceOrderId;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // === 도메인 메서드 ===

    /**
     * 라이선스 활성화.
     */
    public void activate() {
        if (this.status != LicenseStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 활성화할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = LicenseStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    /**
     * 라이선스 정지.
     */
    public void suspend(String reason) {
        if (this.status == LicenseStatus.REVOKED) {
            throw new IllegalStateException("이미 회수된 라이선스는 정지할 수 없습니다.");
        }
        this.status = LicenseStatus.SUSPENDED;
        this.updatedAt = Instant.now();
    }

    /**
     * 라이선스 회수 (환불 등).
     */
    public void revoke(String reason) {
        this.status = LicenseStatus.REVOKED;
        this.updatedAt = Instant.now();
        // 모든 활성화도 비활성화
        this.activations.forEach(Activation::deactivate);
    }

    /**
     * 구독 갱신.
     */
    public void renew(Instant newValidUntil) {
        if (this.licenseType != LicenseType.SUBSCRIPTION) {
            throw new IllegalStateException("구독형 라이선스만 갱신할 수 있습니다.");
        }
        this.validUntil = newValidUntil;
        this.status = LicenseStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    /**
     * 라이선스가 특정 사용자 소유인지 확인.
     */
    public boolean isOwnedBy(UUID userId) {
        return this.ownerType == OwnerType.USER && this.ownerId.equals(userId);
    }

    /**
     * 현재 시점 기준 라이선스 유효 상태 계산.
     */
    public LicenseStatus calculateEffectiveStatus(Instant now) {
        if (this.status == LicenseStatus.REVOKED || this.status == LicenseStatus.SUSPENDED) {
            return this.status;
        }

        if (this.validUntil == null) {
            // Perpetual 라이선스
            return this.status;
        }

        int gracePeriodDays = getGracePeriodDays();
        Instant graceEnd = this.validUntil.plusSeconds(gracePeriodDays * 24L * 60 * 60);

        if (now.isBefore(this.validUntil)) {
            return LicenseStatus.ACTIVE;
        } else if (now.isBefore(graceEnd)) {
            return LicenseStatus.EXPIRED_GRACE;
        } else {
            return LicenseStatus.EXPIRED_HARD;
        }
    }

    /**
     * PolicySnapshot에서 gracePeriodDays 추출.
     */
    public int getGracePeriodDays() {
        if (policySnapshot == null || !policySnapshot.containsKey("gracePeriodDays")) {
            return 7; // 기본값
        }
        return ((Number) policySnapshot.get("gracePeriodDays")).intValue();
    }

    /**
     * PolicySnapshot에서 maxActivations 추출.
     */
    public int getMaxActivations() {
        if (policySnapshot == null || !policySnapshot.containsKey("maxActivations")) {
            return 3; // 기본값
        }
        return ((Number) policySnapshot.get("maxActivations")).intValue();
    }

    /**
     * PolicySnapshot에서 maxConcurrentSessions 추출.
     */
    public int getMaxConcurrentSessions() {
        if (policySnapshot == null || !policySnapshot.containsKey("maxConcurrentSessions")) {
            return 2; // 기본값
        }
        return ((Number) policySnapshot.get("maxConcurrentSessions")).intValue();
    }

    /**
     * 새 기기 활성화 가능 여부 확인.
     */
    public boolean canActivate(String deviceFingerprint, Instant now) {
        LicenseStatus effectiveStatus = calculateEffectiveStatus(now);
        if (effectiveStatus != LicenseStatus.ACTIVE && effectiveStatus != LicenseStatus.EXPIRED_GRACE) {
            return false;
        }

        // 이미 해당 기기가 활성화되어 있는지 확인
        boolean deviceAlreadyActive = activations.stream()
                .anyMatch(a -> a.getDeviceFingerprint().equals(deviceFingerprint)
                        && a.getStatus() == ActivationStatus.ACTIVE);
        if (deviceAlreadyActive) {
            return true; // 기존 활성화 갱신
        }

        // 활성 기기 수 확인
        long activeCount = activations.stream()
                .filter(a -> a.getStatus() == ActivationStatus.ACTIVE || a.getStatus() == ActivationStatus.STALE)
                .count();

        return activeCount < getMaxActivations();
    }

    /**
     * 기기 활성화 추가.
     */
    public Activation addActivation(String deviceFingerprint, String clientVersion,
                                    String clientOs, String lastIp) {
        // 기존 활성화가 있으면 갱신
        Activation existing = activations.stream()
                .filter(a -> a.getDeviceFingerprint().equals(deviceFingerprint))
                .findFirst()
                .orElse(null);

        if (existing != null) {
            existing.reactivate(clientVersion, clientOs, lastIp);
            return existing;
        }

        // 새 활성화 생성
        Activation activation = Activation.builder()
                .license(this)
                .deviceFingerprint(deviceFingerprint)
                .clientVersion(clientVersion)
                .clientOs(clientOs)
                .lastIp(lastIp)
                .build();

        this.activations.add(activation);
        this.updatedAt = Instant.now();
        return activation;
    }
}
