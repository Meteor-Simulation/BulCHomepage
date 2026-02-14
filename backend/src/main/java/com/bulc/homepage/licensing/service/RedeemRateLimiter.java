package com.bulc.homepage.licensing.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RedeemRateLimiter {

    private static final int MAX_ATTEMPTS_PER_MINUTE = 5;
    private static final long WINDOW_SECONDS = 60;

    private final Map<UUID, UserAttempts> attempts = new ConcurrentHashMap<>();

    public boolean isAllowed(UUID userId) {
        Instant now = Instant.now();
        UserAttempts userAttempts = attempts.compute(userId, (key, existing) -> {
            if (existing == null || existing.isExpired(now)) {
                return new UserAttempts(now, 1);
            }
            existing.increment();
            return existing;
        });
        return userAttempts.count <= MAX_ATTEMPTS_PER_MINUTE;
    }

    private static class UserAttempts {
        private final Instant windowStart;
        private int count;

        UserAttempts(Instant windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }

        boolean isExpired(Instant now) {
            return now.isAfter(windowStart.plusSeconds(WINDOW_SECONDS));
        }

        void increment() {
            this.count++;
        }
    }
}
