package com.bulc.homepage.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MDP-707: 인증 없는 공개 폼의 IP 기반 레이트리밋.
 *
 * <p>전시회 현장은 부스 와이파이/모바일 NAT 로 다수 방문자가 같은 IP 를 공유할 수 있으므로
 * 한도를 넉넉히 잡는다. 목적은 사람 차단이 아니라 자동 스크립트의 대량 등록 억제이다.
 *
 * <p>{@link com.bulc.homepage.licensing.service.RedeemRateLimiter} 와 동일하게 인메모리 구현이라
 * 백엔드를 다중 인스턴스로 확장하면 인스턴스마다 한도가 따로 적용된다(MDP-659 무상태화 대상).
 */
@Component
public class PublicFormRateLimiter {

    private static final int MAX_ATTEMPTS_PER_WINDOW = 30;
    private static final long WINDOW_SECONDS = 60;

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key) {
        if (key == null || key.isBlank()) {
            return true;
        }
        Instant now = Instant.now();
        Attempts current = attempts.compute(key, (k, existing) -> {
            if (existing == null || existing.isExpired(now)) {
                return new Attempts(now);
            }
            existing.increment();
            return existing;
        });
        return current.count <= MAX_ATTEMPTS_PER_WINDOW;
    }

    /** 만료된 윈도우 정리 (메모리 누수 방지). */
    @Scheduled(fixedRate = 600_000)
    public void evictExpired() {
        Instant now = Instant.now();
        attempts.entrySet().removeIf(e -> e.getValue().isExpired(now));
    }

    private static class Attempts {
        private final Instant windowStart;
        private int count;

        Attempts(Instant windowStart) {
            this.windowStart = windowStart;
            this.count = 1;
        }

        boolean isExpired(Instant now) {
            return now.isAfter(windowStart.plusSeconds(WINDOW_SECONDS));
        }

        void increment() {
            this.count++;
        }
    }
}
