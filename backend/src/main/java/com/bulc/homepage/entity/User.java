package com.bulc.homepage.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @Column(nullable = false, length = 255)
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
}
