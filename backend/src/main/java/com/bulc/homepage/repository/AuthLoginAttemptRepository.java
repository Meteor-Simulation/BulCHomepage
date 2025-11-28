package com.bulc.homepage.repository;

import com.bulc.homepage.entity.AuthLoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuthLoginAttemptRepository extends JpaRepository<AuthLoginAttempt, Long> {

    List<AuthLoginAttempt> findByEmailOrderByCreatedAtDesc(String email);

    List<AuthLoginAttempt> findByIpAddressOrderByCreatedAtDesc(String ipAddress);

    List<AuthLoginAttempt> findTop100ByOrderByCreatedAtDesc();

    List<AuthLoginAttempt> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime after);

    long countByEmailAndSuccessAndCreatedAtAfter(String email, Boolean success, LocalDateTime after);

    long countByIpAddressAndSuccessAndCreatedAtAfter(String ipAddress, Boolean success, LocalDateTime after);
}
