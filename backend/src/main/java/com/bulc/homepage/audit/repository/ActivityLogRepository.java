package com.bulc.homepage.audit.repository;

import com.bulc.homepage.audit.domain.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    List<ActivityLog> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<ActivityLog> findByActionOrderByCreatedAtDesc(String action);

    List<ActivityLog> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime after);

    void deleteByUserId(UUID userId);
}
