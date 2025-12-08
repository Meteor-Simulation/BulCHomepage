package com.bulc.homepage.service;

import com.bulc.homepage.entity.EmailVerification;
import com.bulc.homepage.repository.EmailVerificationRepository;
import com.bulc.homepage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailVerificationRepository emailVerificationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    private static final int CODE_LENGTH = 6;
    private static final int EXPIRATION_MINUTES = 10;

    /**
     * 이메일 중복 체크
     */
    public boolean isEmailExists(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * 인증 코드 생성 및 저장
     */
    @Transactional
    public String sendVerificationCode(String email) {
        // 이메일 중복 체크
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("이미 가입된 이메일입니다");
        }

        // 6자리 인증 코드 생성
        String code = generateVerificationCode();

        // 기존 미인증 코드 삭제
        emailVerificationRepository.findTopByEmailAndVerifiedFalseOrderByCreatedAtDesc(email)
                .ifPresent(v -> emailVerificationRepository.delete(v));

        // 새 인증 코드 저장
        EmailVerification verification = EmailVerification.builder()
                .email(email)
                .verificationCode(code)
                .expiresAt(LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES))
                .verified(false)
                .build();

        emailVerificationRepository.save(verification);

        log.info("인증 코드 발송 - 이메일: {}, 코드: {}", email, code);

        // 실제 이메일 발송 (mail.enabled=true 시 발송)
        emailService.sendVerificationEmail(email, code);

        return code; // 개발 단계에서는 코드 반환, 운영에서는 제거
    }

    /**
     * 인증 코드 검증
     */
    @Transactional
    public boolean verifyCode(String email, String code) {
        EmailVerification verification = emailVerificationRepository
                .findByEmailAndVerificationCodeAndVerifiedFalse(email, code)
                .orElseThrow(() -> new RuntimeException("인증 코드가 올바르지 않습니다"));

        if (verification.isExpired()) {
            throw new RuntimeException("인증 코드가 만료되었습니다. 다시 요청해주세요.");
        }

        verification.setVerified(true);
        emailVerificationRepository.save(verification);

        log.info("이메일 인증 완료 - 이메일: {}", email);

        return true;
    }

    /**
     * 이메일이 인증되었는지 확인
     */
    public boolean isEmailVerified(String email) {
        return emailVerificationRepository
                .findTopByEmailAndVerifiedFalseOrderByCreatedAtDesc(email)
                .map(v -> v.getVerified())
                .orElse(false);
    }

    /**
     * 6자리 숫자 인증 코드 생성
     */
    private String generateVerificationCode() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }
}
