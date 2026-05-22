package com.bulc.homepage.entity;

import com.bulc.homepage.email.EmailCategory;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_log", indexes = {
        @Index(name = "idx_email_log_recipient", columnList = "recipient_email"),
        @Index(name = "idx_email_log_sent_at", columnList = "sent_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_email", nullable = false, length = 255)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private EmailCategory category;

    @Column(name = "template_key", nullable = false, length = 100)
    private String templateKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "sent_at", nullable = false)
    @Builder.Default
    private LocalDateTime sentAt = LocalDateTime.now();

    @Column(name = "skip_reason", length = 255)
    private String skipReason;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public enum Status {
        SUCCESS,
        SKIPPED,
        FAILED
    }
}
