package com.bulc.homepage.repository;

import com.bulc.homepage.entity.EmailLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {

    /**
     * MDP-505: 중복 발송 가드용 조회.
     * 동일 수신자 + 동일 templateKey + 동일 status + 지정 기간 내 발송 이력 존재 여부.
     */
    boolean existsByRecipientEmailAndTemplateKeyAndStatusAndSentAtBetween(
            String recipientEmail,
            String templateKey,
            EmailLog.Status status,
            LocalDateTime start,
            LocalDateTime end
    );
}
