package com.bulc.homepage.payment.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 환불 요청 — 고객이 신청하고 관리자가 결제관리에서 처리한다.
 *
 * <p>고객이 직접 환불을 실행하게 두지 않는다. 환불은 돈이 나가는 일이고 사유 확인·중복 검토가
 * 필요하다. 그래서 요청만 받고, 실제 승인취소는 관리자가 수행한다.
 *
 * <p>회원 엔티티로의 관계를 두지 않고 {@code userId} 만 들고 있다 — 결제 모듈이 회원 엔티티를
 * 알게 되면 따로 떼어낼 수 없다(CLAUDE.md 모듈 경계). 결제도 {@code paymentId} 로만 잇는다.
 */
@Entity
@Table(name = "refund_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundRequest {

    /** 대기 — 관리자가 아직 보지 않았다. 결제당 1건만 존재할 수 있다(DB 부분 유니크). */
    public static final String STATUS_PENDING = "PENDING";
    /** 환불 완료 — 관리자가 승인해 토스 승인취소까지 끝났다. */
    public static final String STATUS_APPROVED = "APPROVED";
    /** 거절 — 환불하지 않기로 했다. 사유는 adminNote 에 남는다. */
    public static final String STATUS_REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = STATUS_PENDING;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @Column(name = "processed_by")
    private UUID processedBy;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
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

    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }

    /** 환불 완료로 넘긴다. 관리자와 시각을 함께 남겨 누가 언제 처리했는지 추적할 수 있게 한다. */
    public void approve(UUID operator, String note) {
        this.status = STATUS_APPROVED;
        this.processedBy = operator;
        this.processedAt = LocalDateTime.now();
        this.adminNote = note;
    }

    public void reject(UUID operator, String note) {
        this.status = STATUS_REJECTED;
        this.processedBy = operator;
        this.processedAt = LocalDateTime.now();
        this.adminNote = note;
    }
}
