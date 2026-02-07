package com.bulc.homepage.service;

import com.bulc.homepage.entity.TokenBlacklist;
import com.bulc.homepage.repository.TokenBlacklistRepository;
import com.bulc.homepage.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 토큰을 블랙리스트에 추가
     */
    @Transactional
    public void blacklistToken(String token, UUID userId) {
        // 토큰 만료 시간 추출
        Date expiration = jwtTokenProvider.getExpirationFromToken(token);
        LocalDateTime expiresAt = expiration.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        TokenBlacklist blacklist = TokenBlacklist.builder()
                .token(token)
                .userId(userId)
                .expiresAt(expiresAt)
                .build();

        tokenBlacklistRepository.save(blacklist);
        log.info("토큰 블랙리스트 추가 - userId: {}", userId);
    }

    /**
     * 토큰이 블랙리스트에 있는지 확인
     */
    public boolean isBlacklisted(String token) {
        return tokenBlacklistRepository.existsByToken(token);
    }

    /**
     * 만료된 블랙리스트 토큰 정리 (매시간 실행)
     */
    @Scheduled(fixedRate = 3600000) // 1시간마다
    @Transactional
    public void cleanupExpiredTokens() {
        tokenBlacklistRepository.deleteExpiredTokens(LocalDateTime.now());
        log.debug("만료된 블랙리스트 토큰 정리 완료");
    }
}
