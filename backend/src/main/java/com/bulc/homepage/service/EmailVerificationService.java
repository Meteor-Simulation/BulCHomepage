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
     * 이메일 상태 체크 (활성화/비활성화 구분)
     * @return null: 미가입, true: 활성 계정, false: 비활성 계정
     */
    public Boolean checkEmailStatus(String email) {
        return userRepository.findByEmail(email)
                .map(user -> user.getIsActive())
                .orElse(null);
    }

    /**
     * 인증 코드 생성 및 저장
     */
    @Transactional
    public String sendVerificationCode(String email) {
        // 이메일 중복 체크 (비활성화된 계정은 재가입 허용)
        Boolean isActive = checkEmailStatus(email);
        if (isActive != null && isActive) {
            throw new RuntimeException("이미 가입된 이메일입니다");
        }

        // 6자리 인증 코드 생성
        String code = generateVerificationCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES);

        // 기존 인증 코드가 있으면 업데이트, 없으면 새로 생성
        EmailVerification verification = emailVerificationRepository.findByEmail(email)
                .map(existing -> {
                    existing.setVerificationCode(code);
                    existing.setExpiresAt(expiresAt);
                    return existing;
                })
                .orElseGet(() -> EmailVerification.builder()
                        .email(email)
                        .verificationCode(code)
                        .expiresAt(expiresAt)
                        .build());

        emailVerificationRepository.save(verification);

        log.info("인증 코드 발송 - 이메일: {}", email);

        // 실제 이메일 발송
        emailService.sendVerificationEmail(email, code);

        return code;
    }

    /**
     * 인증 코드 검증
     */
    @Transactional
    public boolean verifyCode(String email, String code) {
        EmailVerification verification = emailVerificationRepository
                .findByEmailAndVerificationCode(email, code)
                .orElseThrow(() -> new RuntimeException("인증 코드가 올바르지 않습니다"));

        if (verification.isExpired()) {
            throw new RuntimeException("인증 코드가 만료되었습니다. 다시 요청해주세요.");
        }

        // 인증 완료 시 레코드 삭제
        emailVerificationRepository.delete(verification);

        log.info("이메일 인증 완료 - 이메일: {}", email);

        return true;
    }

    /**
     * 이메일 인증 대기 중인지 확인
     * (인증 코드가 발송되었고 아직 인증되지 않은 상태)
     */
    public boolean hasPendingVerification(String email) {
        return emailVerificationRepository.existsByEmail(email);
    }

    /**
     * 6자리 영숫자 인증 코드 생성 (대소문자 구별)
     */
    private String generateVerificationCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        return code.toString();
    }
}
