package com.bulc.homepage.licensing.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 라이선스 플랜/정책 템플릿.
 * Admin UI에서 관리하며, 라이선스 발급 시 이 플랜 정보를 기반으로 PolicySnapshot을 생성.
 */
@Entity
@Table(name = "license_plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LicensePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "license_type", nullable = false, length = 32)
    private LicenseType licenseType;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Column(name = "grace_days", nullable = false)
    private int graceDays;

    // v1.2.0 (MDP-791): 유예기간(EXPIRED_GRACE) 중 제공되는 기능 범위.
    // 종전 스펙 문서(licensing_domain_v1.md)에만 있고 서버 컬럼이 없어 클라이언트 가정으로만 살아 있었다.
    // v1 기본값 "full" (유예기간 중 전 기능 제공). 다른 값("limited" 등)은 후속 정의.
    @Column(name = "grace_period_features", nullable = false, length = 32)
    private String gracePeriodFeatures = "full";

    @Column(name = "max_activations", nullable = false)
    private int maxActivations;

    @Column(name = "max_concurrent_sessions", nullable = false)
    private int maxConcurrentSessions;

    @Column(name = "allow_offline_days", nullable = false)
    private int allowOfflineDays;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LicensePlanEntitlement> entitlements = new ArrayList<>();

    @Builder
    private LicensePlan(UUID productId, String code, String name, String description,
                        LicenseType licenseType, int durationDays, int graceDays,
                        int maxActivations, int maxConcurrentSessions, int allowOfflineDays,
                        String gracePeriodFeatures) {
        this.productId = productId;
        this.code = code;
        this.name = name;
        this.description = description;
        this.licenseType = licenseType;
        this.durationDays = durationDays;
        this.graceDays = graceDays;
        this.maxActivations = maxActivations;
        this.maxConcurrentSessions = maxConcurrentSessions;
        this.allowOfflineDays = allowOfflineDays;
        // v1.2.0 (MDP-791): null 이면 v1 기본값 "full"
        this.gracePeriodFeatures = gracePeriodFeatures != null ? gracePeriodFeatures : "full";
        this.active = true;
        this.deleted = false;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // === 도메인 메서드 ===

    /**
     * 플랜 정보 업데이트.
     */
    public void update(String code, String name, String description,
                       LicenseType licenseType, int durationDays, int graceDays,
                       int maxActivations, int maxConcurrentSessions, int allowOfflineDays,
                       String gracePeriodFeatures) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.licenseType = licenseType;
        this.durationDays = durationDays;
        this.graceDays = graceDays;
        this.maxActivations = maxActivations;
        this.maxConcurrentSessions = maxConcurrentSessions;
        this.allowOfflineDays = allowOfflineDays;
        // v1.2.0 (MDP-791): null 이면 기존 값 유지
        if (gracePeriodFeatures != null) {
            this.gracePeriodFeatures = gracePeriodFeatures;
        }
        this.updatedAt = Instant.now();
    }

    /**
     * 플랜 활성화.
     */
    public void activate() {
        this.active = true;
        this.updatedAt = Instant.now();
    }

    /**
     * 플랜 비활성화.
     */
    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    /**
     * 플랜 삭제 (soft delete).
     */
    public void delete() {
        this.deleted = true;
        this.active = false;
        this.updatedAt = Instant.now();
    }

    /**
     * Entitlements 설정 (변경분만 반영).
     * 기존에 있는 키는 유지, 새 키만 추가, 없어진 키만 삭제.
     */
    public void setEntitlements(Collection<String> keys) {
        Set<String> newKeys = keys != null ? new HashSet<>(keys) : Set.of();

        // 기존 키 중 새 목록에 없는 것 삭제
        this.entitlements.removeIf(e -> !newKeys.contains(e.getEntitlementKey()));

        // 기존에 이미 있는 키
        Set<String> existingKeys = getEntitlementKeys();

        // 새 목록 중 기존에 없는 것만 추가
        for (String key : newKeys) {
            if (!existingKeys.contains(key)) {
                this.entitlements.add(new LicensePlanEntitlement(this, key));
            }
        }

        this.updatedAt = Instant.now();
    }

    /**
     * Entitlement 키 목록 조회.
     */
    public Set<String> getEntitlementKeys() {
        return this.entitlements.stream()
                .map(LicensePlanEntitlement::getEntitlementKey)
                .collect(Collectors.toSet());
    }

    /**
     * 라이선스 발급 시 사용할 PolicySnapshot 생성.
     * 이 시점의 플랜 정보를 JSON 형태로 변환하여 License에 저장.
     */
    public Map<String, Object> toPolicySnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("maxActivations", this.maxActivations);
        snapshot.put("maxConcurrentSessions", this.maxConcurrentSessions);
        snapshot.put("gracePeriodDays", this.graceDays);
        snapshot.put("allowOfflineDays", this.allowOfflineDays);
        snapshot.put("entitlements", new ArrayList<>(getEntitlementKeys()));
        return snapshot;
    }

    /**
     * 유효한 플랜인지 확인 (삭제되지 않고 활성화 상태).
     */
    public boolean isAvailable() {
        return !this.deleted && this.active;
    }
}
