package com.bulc.homepage.licensing.service;

import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * v1.1.2: Session Token 서비스.
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
 * - iat: 발급 시각 (epoch seconds)
 * - exp: 만료 시각 (epoch seconds)
 *
 * 보안 정책:
 * - **RS256 (RSA-SHA256) 전용** - HS256 폴백 없음
 * - prod 프로필에서 개인키 미설정 시 서버 부팅 실패 (fail-fast)
 * - dev 프로필에서도 RS256 사용 권장 (테스트 키 생성 스크립트 제공)
 * - 알고리즘 혼동(alg confusion) 공격 방지를 위해 단일 알고리즘만 지원
 */
@Slf4j
@Service
public class SessionTokenService {

    private final int ttlMinutes;
    private final String issuer;
    private final String privateKeyBase64;
    private final String activeProfile;

    private PrivateKey rsaPrivateKey;

    public SessionTokenService(
            @Value("${bulc.licensing.session-token.ttl-minutes:15}") int ttlMinutes,
            @Value("${bulc.licensing.session-token.issuer:bulc-license-server}") String issuer,
            @Value("${bulc.licensing.session-token.private-key:}") String privateKeyBase64,
            @Value("${spring.profiles.active:dev}") String activeProfile) {
        this.ttlMinutes = ttlMinutes;
        this.issuer = issuer;
        this.privateKeyBase64 = privateKeyBase64;
        this.activeProfile = activeProfile;
    }

    @PostConstruct
    public void init() {
        boolean isProd = activeProfile.contains("prod");

        if (privateKeyBase64 == null || privateKeyBase64.isBlank()) {
            if (isProd) {
                // prod에서 키 없으면 부팅 실패 (fail-fast)
                throw new IllegalStateException(
                    "[FATAL] SessionTokenService: RS256 개인키가 설정되지 않았습니다. " +
                    "prod 환경에서는 SESSION_TOKEN_PRIVATE_KEY 환경변수가 필수입니다. " +
                    "서버를 시작할 수 없습니다."
                );
            } else {
                // dev에서도 경고 (RS256 사용 권장)
                log.warn("========================================");
                log.warn("SessionTokenService: RS256 개인키가 설정되지 않았습니다.");
                log.warn("sessionToken 발급이 비활성화됩니다.");
                log.warn("개발 환경에서도 RS256 키 설정을 권장합니다.");
                log.warn("키 생성: openssl genrsa -out private_key.pem 2048");
                log.warn("========================================");
                this.rsaPrivateKey = null;
                return;
            }
        }

        try {
            this.rsaPrivateKey = loadPrivateKey(privateKeyBase64);
            log.info("SessionTokenService: RS256 개인키 로드 성공 (알고리즘: RS256 전용)");
        } catch (Exception e) {
            if (isProd) {
                throw new IllegalStateException(
                    "[FATAL] SessionTokenService: RS256 개인키 로드 실패. " +
                    "키 형식을 확인하세요 (PKCS#8 PEM, Base64 인코딩). 에러: " + e.getMessage(), e
                );
            } else {
                log.error("SessionTokenService: RS256 개인키 로드 실패. sessionToken 발급이 비활성화됩니다.", e);
                this.rsaPrivateKey = null;
            }
        }
    }

    /**
     * sessionToken 생성.
     *
     * @param licenseId 라이선스 ID (sub 클레임)
     * @param productCode 제품 코드 (aud 클레임, 예: BULC_EVAC)
     * @param deviceFingerprint 기기 fingerprint (dfp 클레임 - 기기 바인딩)
     * @param entitlements 권한 목록 (ent 클레임)
     * @return SessionToken 객체 (토큰 문자열) 또는 키 미설정 시 null 반환
     *
     * @throws IllegalStateException prod 환경에서 키가 없는 경우 (init에서 이미 실패하므로 도달 불가)
     */
    public SessionToken generateSessionToken(UUID licenseId, String productCode,
                                              String deviceFingerprint, List<String> entitlements) {
        if (rsaPrivateKey == null) {
            log.warn("SessionTokenService: RS256 키가 없어 sessionToken을 발급할 수 없습니다.");
            return null;
        }

        Instant now = Instant.now();
        Instant exp = now.plus(ttlMinutes, ChronoUnit.MINUTES);

        // RS256 전용 - 알고리즘 혼동 방지
        String token = Jwts.builder()
                .header().add("alg", "RS256").add("typ", "JWT").and()
                .issuer(issuer)
                .audience().add(productCode).and()
                .subject(licenseId.toString())
                .claim("dfp", deviceFingerprint)
                .claim("ent", entitlements)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(rsaPrivateKey, Jwts.SIG.RS256)
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
        return rsaPrivateKey != null;
    }

    /**
     * Base64 인코딩된 PKCS#8 개인키 로드.
     *
     * 지원 형식:
     * - PEM 형식 (-----BEGIN PRIVATE KEY----- 헤더/푸터 포함)
     * - Base64 인코딩된 DER
     */
    private PrivateKey loadPrivateKey(String keyData) throws Exception {
        // PEM 형식 헤더/푸터 제거
        String cleanedKey = keyData
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(cleanedKey);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }

    /**
     * sessionToken 결과 객체.
     *
     * exp는 토큰 내부에 포함되어 있으므로 별도 필드로 제공하지 않음.
     * 클라이언트는 JWT를 디코드하여 exp 클레임으로 만료를 판단해야 함.
     */
    public record SessionToken(String token) {}
}
