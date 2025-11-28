package com.bulc.homepage.repository;

import com.bulc.homepage.entity.UserActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserActivityLogRepository extends JpaRepository<UserActivityLog, Long> {

    List<UserActivityLog> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<UserActivityLog> findByAction(String action);

    List<UserActivityLog> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    List<UserActivityLog> findByUserIdAndCreatedAtBetween(Long userId, LocalDateTime start, LocalDateTime end);
}
