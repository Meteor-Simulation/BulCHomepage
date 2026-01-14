package com.bulc.homepage.repository;

import com.bulc.homepage.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByEmail(String email);
    Optional<PasswordResetToken> findByEmailAndResetCode(String email, String resetCode);
    boolean existsByEmail(String email);
    void deleteByEmail(String email);
}
