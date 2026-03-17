package com.bulc.homepage.service;

import com.bulc.homepage.entity.SignupTicket;
import com.bulc.homepage.repository.SignupTicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignupTicketService {

    private final SignupTicketRepository signupTicketRepository;

    private static final int TICKET_EXPIRATION_MINUTES = 10;

    @Transactional
    public UUID createTicket(String email) {
        // 기존 미사용 티켓 정리
        signupTicketRepository.deleteByEmailAndUsedAtIsNull(email);

        SignupTicket ticket = SignupTicket.builder()
                .id(UUID.randomUUID())
                .email(email)
                .purpose("SIGNUP")
                .expiresAt(LocalDateTime.now().plusMinutes(TICKET_EXPIRATION_MINUTES))
                .build();
        signupTicketRepository.save(ticket);

        log.info("가입 티켓 생성 - 이메일: {}, 티켓ID: {}", email, ticket.getId());
        return ticket.getId();
    }

    @Transactional
    public SignupTicket consumeTicket(UUID ticketId) {
        // 비관적 락으로 조회
        SignupTicket ticket = signupTicketRepository.findByIdWithLock(ticketId)
                .orElseThrow(() -> new RuntimeException("유효하지 않은 가입 티켓입니다"));

        if (ticket.getUsedAt() != null) {
            throw new RuntimeException("이미 사용된 티켓입니다");
        }

        if (ticket.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("만료된 티켓입니다");
        }

        ticket.setUsedAt(LocalDateTime.now());
        signupTicketRepository.save(ticket);

        log.info("가입 티켓 소비 - 이메일: {}, 티켓ID: {}", ticket.getEmail(), ticket.getId());
        return ticket;
    }
}
