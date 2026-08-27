package com.bulc.homepage.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "roles_code", nullable = false, length = 10)
    @Builder.Default
    private String rolesCode = "002";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "roles_code", referencedColumnName = "code", insertable = false, updatable = false)
    private UserRole role;

    @Column(length = 100)
    private String name;

    @Column(length = 20)
    private String phone;

    @Column(name = "country_code", length = 10)
    @Builder.Default
    private String countryCode = "KR";

    @Column(name = "language_code", length = 5)
    private String languageCode;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Column(name = "marketing_agreed", nullable = false)
    @Builder.Default
    private Boolean marketingAgreed = false;

    /**
     * 광고성 수신 상태 — Y:동의, N:거절, P:미선택 (MDP-772).
     *
     * <p>boolean 하나로는 "거절함"과 "아직 안 물어봄"이 구분되지 않아
     * 거절한 사용자에게 동의 팝업이 계속 노출되던 문제가 있었다.
     *
     * <p>{@link #marketingAgreed} 는 롤백 대비로 남겨두고 함께 갱신하지만,
     * 판단 기준은 이 컬럼이다.
     */
    @Column(name = "marketing_consent", nullable = false, length = 1)
    @Builder.Default
    private String marketingConsent = MarketingConsent.PENDING;

    /** 마지막으로 수신 상태가 바뀐 시각 (동의·철회 시점 기록). */
    @Column(name = "marketing_agreed_at")
    private LocalDateTime marketingAgreedAt;

    @Column(name = "unsubscribe_token", unique = true, length = 36)
    private String unsubscribeToken;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
