package com.bulc.homepage.service;

import com.bulc.homepage.entity.PasswordResetToken;
import com.bulc.homepage.entity.User;
import com.bulc.homepage.repository.PasswordResetTokenRepository;
import com.bulc.homepage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    private static final int CODE_LENGTH = 6;
    private static final int EXPIRATION_MINUTES = 5;

    /**
     * 비밀번호 재설정 요청 (인증 코드 발송)
     */
    @Transactional
    public void requestPasswordReset(String email) {
        // 사용자 존재 여부 확인 (미가입/비활성화 이메일은 조용히 무시 - 계정 존재 여부 미노출)
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !user.getIsActive()) {
            log.info("비밀번호 재설정 요청 - 미가입 또는 비활성화 이메일: {}", email);
            return;
        }

        // 6자리 인증 코드 생성
        String code = generateResetCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES);

        // 기존 토큰이 있으면 업데이트, 없으면 새로 생성
        PasswordResetToken token = passwordResetTokenRepository.findByEmail(email)
                .map(existing -> {
                    existing.setResetCode(code);
                    existing.setExpiresAt(expiresAt);
                    return existing;
                })
                .orElseGet(() -> PasswordResetToken.builder()
                        .email(email)
                        .resetCode(code)
                        .expiresAt(expiresAt)
                        .build());

        passwordResetTokenRepository.save(token);

        log.info("비밀번호 재설정 코드 발송 - 이메일: {}", email);

        // 비동기 이메일 발송 (DB에 코드 저장 후 즉시 응답)
        CompletableFuture.runAsync(() -> {
            try {
                emailService.sendPasswordResetEmail(email, code);
            } catch (Exception e) {
                log.error("비밀번호 재설정 이메일 발송 실패 (비동기) - 이메일: {}, 오류: {}", email, e.getMessage());
            }
        });
    }

    /**
     * 인증 코드 검증
     */
    @Transactional(readOnly = true)
    public boolean verifyResetCode(String email, String code) {
        PasswordResetToken token = passwordResetTokenRepository
                .findByEmailAndResetCode(email, code.toUpperCase())
                .orElseThrow(() -> new RuntimeException("인증 코드가 올바르지 않습니다."));

        if (token.isExpired()) {
            throw new RuntimeException("인증 코드가 만료되었습니다. 다시 요청해주세요.");
        }

        return true;
    }

    /**
     * 비밀번호 재설정 완료
     */
    @Transactional
    public void resetPassword(String email, String code, String newPassword) {
        // 코드 검증
        PasswordResetToken token = passwordResetTokenRepository
                .findByEmailAndResetCode(email, code.toUpperCase())
                .orElseThrow(() -> new RuntimeException("인증 코드가 올바르지 않습니다."));

        if (token.isExpired()) {
            throw new RuntimeException("인증 코드가 만료되었습니다. 다시 요청해주세요.");
        }

        // 사용자 조회 및 비밀번호 업데이트
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // 토큰 삭제
        passwordResetTokenRepository.delete(token);

        log.info("비밀번호 재설정 완료 - 이메일: {}", email);
    }

    /**
     * 6자리 영숫자 인증 코드 생성
     */
    private String generateResetCode() {
        String chars = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        return code.toString();
    }
}
