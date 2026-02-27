package com.bulc.homepage.repository;

import com.bulc.homepage.entity.SignupTicket;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SignupTicketRepository extends JpaRepository<SignupTicket, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM SignupTicket t WHERE t.id = :id")
    Optional<SignupTicket> findByIdWithLock(@Param("id") UUID id);

    void deleteByEmailAndUsedAtIsNull(String email);
}
