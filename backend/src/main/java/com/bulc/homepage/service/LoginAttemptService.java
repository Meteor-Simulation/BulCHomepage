package com.bulc.homepage.service;

import com.bulc.homepage.entity.LoginAttempt;
import com.bulc.homepage.repository.LoginAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final LoginAttemptRepository loginAttemptRepository;

    private static final int MAX_ATTEMPTS = 5;           // 최대 시도 횟수
    private static final int WINDOW_MINUTES = 15;         // 시도 윈도우 (분)
    private static final int LOCKOUT_MINUTES = 30;        // 잠금 시간 (분)

    /**
     * 로그인 시도 전 잠금 상태 확인.
     * 잠금 중이면 남은 시간과 함께 예외 발생.
     */
    @Transactional(readOnly = true)
    public void checkLocked(String email) {
        LoginAttempt attempt = loginAttemptRepository.findByEmail(email).orElse(null);
        if (attempt != null && attempt.isLocked()) {
            long minutesLeft = LocalDateTime.now().until(attempt.getLockedUntil(), ChronoUnit.MINUTES) + 1;
            log.warn("로그인 잠금 상태 - 이메일: {}, 남은 시간: {}분", email, minutesLeft);
            throw new RuntimeException("로그인 시도가 너무 많습니다. " + minutesLeft + "분 후에 다시 시도해주세요.");
        }
    }

    /**
     * 로그인 실패 기록.
     * MAX_ATTEMPTS 초과 시 LOCKOUT_MINUTES 동안 잠금.
     */
    @Transactional
    public void recordFailure(String email) {
        LoginAttempt attempt = loginAttemptRepository.findByEmail(email).orElse(null);

        if (attempt == null) {
            attempt = LoginAttempt.builder()
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
            attempt.setAttemptCount(attempt.getAttemptCount() + 1);
        }

        if (attempt.getAttemptCount() >= MAX_ATTEMPTS) {
            attempt.setLockedUntil(LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES));
            log.warn("로그인 잠금 설정 - 이메일: {}, {}회 실패, {}분간 잠금", email, attempt.getAttemptCount(), LOCKOUT_MINUTES);
        }

        loginAttemptRepository.save(attempt);
    }

    /**
     * 로그인 성공 시 실패 기록 초기화.
     */
    @Transactional
    public void resetAttempts(String email) {
        loginAttemptRepository.deleteByEmail(email);
    }
}
