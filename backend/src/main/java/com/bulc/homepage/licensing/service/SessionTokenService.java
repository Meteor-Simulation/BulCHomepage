package com.bulc.homepage.licensing.service;

import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * v0.2.2: Session Token 서비스.
 *
 * validate/validate-force/heartbeat 성공 시 RS256 서명된 sessionToken을 생성합니다.
 * sessionToken은 CLI/앱에서 서명 검증 후 기능 unlock 여부를 결정하는 최종 기준입니다.
 *
 * 클레임 (고정):
 * - iss: 발급자 (bulc-license-server)
 * - aud: 대상 제품 코드 (예: BULC_EVAC)
 * - sub: licenseId
 * - dfp: deviceFingerprint (기기 바인딩)
 * - ent: entitlements 배열
 * - kid: 키 식별자 (테스트 키 구분용)
 * - iat: 발급 시각 (epoch seconds)
 * - exp: 만료 시각 (epoch seconds)
 *
 * 보안 정책:
 * - **RS256 (RSA-SHA256) 전용** - HS256 폴백 없음
 * - SigningKeyProvider를 통한 키 주입 (테스트/프로덕션 분리)
 * - 알고리즘 혼동(alg confusion) 공격 방지를 위해 단일 알고리즘만 지원
 */
@Slf4j
@Service
public class SessionTokenService {

    private final int ttlMinutes;
    private final String issuer;
    private final SigningKeyProvider keyProvider;

    public SessionTokenService(
            @Value("${bulc.licensing.session-token.ttl-minutes:15}") int ttlMinutes,
            @Value("${bulc.licensing.session-token.issuer:bulc-license-server}") String issuer,
            SigningKeyProvider keyProvider) {
        this.ttlMinutes = ttlMinutes;
        this.issuer = issuer;
        this.keyProvider = keyProvider;
    }

    /**
     * sessionToken 생성.
     *
     * @param licenseId 라이선스 ID (sub 클레임)
     * @param productCode 제품 코드 (aud 클레임, 예: BULC_EVAC)
     * @param deviceFingerprint 기기 fingerprint (dfp 클레임 - 기기 바인딩)
     * @param entitlements 권한 목록 (ent 클레임)
     * @return SessionToken 객체 (토큰 문자열) 또는 키 미설정 시 null 반환
     */
    public SessionToken generateSessionToken(UUID licenseId, String productCode,
                                              String deviceFingerprint, List<String> entitlements) {
        if (!keyProvider.isEnabled()) {
            log.warn("SessionTokenService: RS256 키가 없어 sessionToken을 발급할 수 없습니다.");
            return null;
        }

        Instant now = Instant.now();
        Instant exp = now.plus(ttlMinutes, ChronoUnit.MINUTES);

        // RS256 전용 - 알고리즘 혼동 방지
        String token = Jwts.builder()
                .header()
                    .add("alg", "RS256")
                    .add("typ", "JWT")
                    .add("kid", keyProvider.keyId())
                    .and()
                .issuer(issuer)
                .audience().add(productCode).and()
                .subject(licenseId.toString())
                .claim("dfp", deviceFingerprint)
                .claim("ent", entitlements)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(keyProvider.signingKey(), Jwts.SIG.RS256)
                .compact();

        return new SessionToken(token);
    }

    /**
     * sessionToken TTL (분) 반환.
     */
    public int getTtlMinutes() {
        return ttlMinutes;
    }

    /**
     * RS256 키 로드 여부 반환.
     */
    public boolean isEnabled() {
        return keyProvider.isEnabled();
    }

    /**
     * 현재 키 식별자 반환 (테스트 검증용).
     */
    public String getKeyId() {
        return keyProvider.keyId();
    }

    /**
     * sessionToken 결과 객체.
     *
     * exp는 토큰 내부에 포함되어 있으므로 별도 필드로 제공하지 않음.
     * 클라이언트는 JWT를 디코드하여 exp 클레임으로 만료를 판단해야 함.
     */
    public record SessionToken(String token) {}
}
