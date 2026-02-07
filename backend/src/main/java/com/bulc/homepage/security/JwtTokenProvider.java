package com.bulc.homepage.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    private SecretKey secretKey;

    @PostConstruct
    protected void init() {
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        // username이 userId.toString()으로 설정되어 있다고 가정
        return generateToken(userDetails.getUsername(), null, accessTokenExpiration);
    }

    /**
     * 새로운 액세스 토큰 생성 (UUID userId 기반)
     */
    public String generateAccessToken(UUID userId, String email) {
        return generateToken(userId.toString(), email, accessTokenExpiration);
    }

    /**
     * @deprecated Use generateAccessToken(UUID userId, String email) instead
     */
    @Deprecated
    public String generateAccessToken(String email) {
        return generateToken(email, null, accessTokenExpiration);
    }

    /**
     * 새로운 리프레시 토큰 생성 (UUID userId 기반)
     */
    public String generateRefreshToken(UUID userId, String email) {
        return generateToken(userId.toString(), email, refreshTokenExpiration);
    }

    /**
     * @deprecated Use generateRefreshToken(UUID userId, String email) instead
     */
    @Deprecated
    public String generateRefreshToken(String email) {
        return generateToken(email, null, refreshTokenExpiration);
    }

    /**
     * OAuth 회원가입용 임시 토큰 생성 (10분 유효)
     */
    public String generateTempToken(String email, String provider, String providerId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + 600000); // 10분

        return Jwts.builder()
                .subject(email)
                .claim("provider", provider)
                .claim("providerId", providerId)
                .claim("type", "oauth_signup")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 임시 토큰에서 정보 추출
     */
    public Claims parseTempToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 임시 토큰 유효성 검증
     */
    public boolean validateTempToken(String token) {
        try {
            Claims claims = parseTempToken(token);
            return "oauth_signup".equals(claims.get("type", String.class));
        } catch (Exception e) {
            log.error("Invalid temp token: {}", e.getMessage());
            return false;
        }
    }

    private String generateToken(String subject, String email, long expiration) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())  // JTI: 토큰 고유 ID (RTR에서 토큰 구분용)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate);

        if (email != null) {
            builder.claim("email", email);
        }

        return builder.signWith(secretKey).compact();
    }

    /**
     * 토큰에서 사용자 ID(UUID) 추출
     */
    public UUID getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return UUID.fromString(claims.getSubject());
    }

    /**
     * 토큰에서 이메일 추출 (claim에서)
     */
    public String getEmailFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        // 먼저 email claim 확인, 없으면 subject 반환 (하위 호환성)
        String email = claims.get("email", String.class);
        return email != null ? email : claims.getSubject();
    }

    public Date getExpirationFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getExpiration();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }
}
