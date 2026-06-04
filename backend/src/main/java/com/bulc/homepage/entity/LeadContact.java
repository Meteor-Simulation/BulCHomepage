package com.bulc.homepage.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * MDP-549: 회원가입 없이 외부(전시회·세미나 등)에서 수집한 메일링 컨택.
 *
 * 일반 사용자(User)와 별개로 관리되며 관리자가 직접 등록·해지한다.
 */
@Entity
@Table(name = "lead_contacts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeadContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "contact_name", length = 100)
    private String contactName;

    @Column(name = "company_name", length = 100)
    private String companyName;

    @Column(length = 100)
    private String role;

    @Column(name = "source_event", length = 200)
    private String sourceEvent;

    @Column(name = "source_date")
    private LocalDate sourceDate;

    @Column(name = "collected_by", length = 100)
    private String collectedBy;

    @Column(name = "consent_method", length = 50)
    private String consentMethod;

    @Column(name = "consent_date")
    private LocalDate consentDate;

    @Column(name = "consent_evidence", columnDefinition = "TEXT")
    private String consentEvidence;

    @Column(name = "opt_in_marketing", nullable = false)
    @Builder.Default
    private Boolean optInMarketing = false;

    @Column(name = "opt_in_transactional", nullable = false)
    @Builder.Default
    private Boolean optInTransactional = true;

    @Column(length = 500)
    private String tags;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "unsubscribe_token", nullable = false, unique = true)
    @Builder.Default
    private UUID unsubscribeToken = UUID.randomUUID();

    @Column(name = "unsubscribed_at")
    private LocalDateTime unsubscribedAt;

    @Column(name = "unsubscribe_reason", length = 500)
    private String unsubscribeReason;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
        if (unsubscribeToken == null) unsubscribeToken = UUID.randomUUID();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return unsubscribedAt == null;
    }

    public void markUnsubscribed(String reason) {
        this.unsubscribedAt = LocalDateTime.now();
        this.unsubscribeReason = reason;
    }
}
