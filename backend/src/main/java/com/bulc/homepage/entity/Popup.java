package com.bulc.homepage.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "popups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Popup {

    public enum Type {
        IMAGE_TEXT, TEXT_ONLY
    }

    public enum Trigger {
        HOME_ENTRY, POST_LOGIN
    }

    public enum CloseOption {
        HIDE_TODAY, HIDE_FOREVER, CLOSE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(nullable = false, length = 100)
    private String triggers;

    @Column(name = "close_options", nullable = false, length = 100)
    private String closeOptions;

    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

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

    public List<Trigger> getTriggerList() {
        if (triggers == null || triggers.isBlank()) return List.of();
        return Arrays.stream(triggers.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Trigger::valueOf)
                .toList();
    }

    public List<CloseOption> getCloseOptionList() {
        if (closeOptions == null || closeOptions.isBlank()) return List.of();
        return Arrays.stream(closeOptions.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(CloseOption::valueOf)
                .toList();
    }

    public boolean isVisibleAt(LocalDateTime now) {
        if (!Boolean.TRUE.equals(isActive)) return false;
        if (startAt != null && now.isBefore(startAt)) return false;
        if (endAt != null && now.isAfter(endAt)) return false;
        return true;
    }
}
