package com.bulc.homepage.audit.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "activity_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private UUID userId;

    // 회원 엔티티로의 @ManyToOne 관계를 제거했다 (MDP-924).
    //
    // 읽기 전용(insertable=false, updatable=false) 매핑이었고 getUser() 를 쓰는 곳이 한 곳도
    // 없었다. 유지하면 감사 모듈이 회원 엔티티를 알아야 해서 따로 떼어낼 수 없다.
    // 조회는 모두 userId 기반이므로 동작 변화가 없고, user_id 컬럼은 위 userId 가 그대로 매핑한다.

    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "target_type", length = 50)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
