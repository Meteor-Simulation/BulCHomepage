package com.bulc.homepage.repository;

import com.bulc.homepage.entity.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    List<ActivityLog> findByUserEmailOrderByCreatedAtDesc(String userEmail);

    List<ActivityLog> findByActionOrderByCreatedAtDesc(String action);

    List<ActivityLog> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime after);

    void deleteByUserEmail(String userEmail);
}
