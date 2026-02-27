package com.bulc.homepage.service;

import com.bulc.homepage.entity.EmailVerification;
import com.bulc.homepage.entity.EmailVerificationAttempt;
import com.bulc.homepage.repository.EmailVerificationAttemptRepository;
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
    private final EmailVerificationAttemptRepository attemptRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    private static final int CODE_LENGTH = 6;
    private static final int EXPIRATION_MINUTES = 10;
    private static final int MAX_ATTEMPTS = 5;
    private static final int WINDOW_MINUTES = 60;
    private static final int LOCKOUT_HOURS = 24;

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
     * 인증 코드 검증 (시도 횟수 제한 적용)
     *
     * - 최대 5회 실패 허용 (1시간 윈도우)
     * - 5회 초과 시 24시간 락
     * - 인증 성공 시 시도 기록 삭제
     */
    @Transactional
    public boolean verifyCode(String email, String code) {
        // 1. 락 여부 확인 (코드 검증 전에 체크)
        checkAttemptLock(email);

        // 2. 인증 코드 조회
        EmailVerification verification = emailVerificationRepository
                .findByEmailAndVerificationCode(email, code)
                .orElse(null);

        if (verification == null) {
            // 실패: 시도 횟수 기록
            recordFailedAttempt(email);
            throw new RuntimeException("인증 코드가 올바르지 않습니다");
        }

        if (verification.isExpired()) {
            recordFailedAttempt(email);
            throw new RuntimeException("인증 코드가 만료되었습니다. 다시 요청해주세요.");
        }

        // 3. 인증 성공 → 시도 기록 삭제 + 인증 레코드 삭제
        attemptRepository.deleteByEmail(email);
        emailVerificationRepository.delete(verification);

        log.info("이메일 인증 완료 - 이메일: {}", email);

        return true;
    }

    /**
     * 락 여부 확인. 잠금 상태이면 예외 발생.
     */
    private void checkAttemptLock(String email) {
        attemptRepository.findByEmail(email).ifPresent(attempt -> {
            if (attempt.isLocked()) {
                log.warn("이메일 인증 잠금 상태 - 이메일: {}, 해제 시각: {}", email, attempt.getLockedUntil());
                throw new RuntimeException("인증 시도 횟수를 초과했습니다. 잠시 후 다시 시도해주세요.");
            }
        });
    }

    /**
     * 실패 시도 기록. 최대 횟수 도달 시 24시간 락.
     */
    private void recordFailedAttempt(String email) {
        EmailVerificationAttempt attempt = attemptRepository.findByEmail(email)
                .orElse(null);

        if (attempt == null) {
            // 첫 실패
            attempt = EmailVerificationAttempt.builder()
                    .email(email)
                    .attemptCount(1)
                    .firstAttemptAt(LocalDateTime.now())
                    .build();
        } else if (attempt.isWindowExpired(WINDOW_MINUTES)) {
            // 윈도우 만료 → 카운트 리셋
            attempt.setAttemptCount(1);
            attempt.setFirstAttemptAt(LocalDateTime.now());
            attempt.setLockedUntil(null);
        } else {
            // 윈도우 내 추가 실패
            attempt.setAttemptCount(attempt.getAttemptCount() + 1);
        }

        // 최대 횟수 도달 시 락
        if (attempt.getAttemptCount() >= MAX_ATTEMPTS) {
            attempt.setLockedUntil(LocalDateTime.now().plusHours(LOCKOUT_HOURS));
            log.warn("이메일 인증 횟수 초과 → 24시간 잠금 - 이메일: {}", email);
        }

        attemptRepository.save(attempt);
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
