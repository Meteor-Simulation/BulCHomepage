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
 * v0.2.3: Offline Token 서비스.
 *
 * 오프라인 실행 허용 기간 동안 클라이언트가 서버 연결 없이 라이선스를 검증할 수 있도록
 * RS256 서명된 offlineToken을 생성합니다.
 *
 * sessionToken과 동일하게 RS256을 사용하며, 클라이언트는 공개키로 검증합니다.
 *
 * Claims (v0.2.3 통일):
 * - iss: 발급자 (bulc-license-server)
 * - aud: 대상 제품 코드 (예: BULC_EVAC)
 * - sub: licenseId
 * - typ: 토큰 타입 ("offline")
 * - dfp: deviceFingerprint (기기 바인딩)
 * - ent: entitlements 배열
 * - kid: 키 식별자 (테스트 키 구분용)
 * - iat: 발급 시각 (epoch seconds)
 * - exp: 만료 시각 (epoch seconds) - **absolute cap: exp ≤ validUntil**
 *
 * 갱신 정책 (v0.2.3):
 * - 갱신 임계값: 남은 기간이 50% 미만 또는 3일 미만일 때만 갱신
 * - absolute cap: offlineToken.exp는 license.validUntil을 초과할 수 없음
 */
@Slf4j
@Service
public class OfflineTokenService {

    private final String issuer;
    private final SigningKeyProvider keyProvider;
    private final double renewalThresholdRatio;
    private final int renewalThresholdDays;

    public OfflineTokenService(
            @Value("${bulc.licensing.offline-token.issuer:bulc-license-server}") String issuer,
            SigningKeyProvider keyProvider,
            @Value("${bulc.licensing.offline-token.renewal-threshold-ratio:0.5}") double renewalThresholdRatio,
            @Value("${bulc.licensing.offline-token.renewal-threshold-days:3}") int renewalThresholdDays) {
        this.issuer = issuer;
        this.keyProvider = keyProvider;
        this.renewalThresholdRatio = renewalThresholdRatio;
        this.renewalThresholdDays = renewalThresholdDays;
    }

    /**
     * offlineToken 생성.
     *
     * @param licenseId 라이선스 ID (sub 클레임)
     * @param productCode 제품 코드 (aud 클레임)
     * @param deviceFingerprint 기기 fingerprint (dfp 클레임)
     * @param entitlements 권한 목록 (ent 클레임)
     * @param allowOfflineDays 오프라인 허용 일수
     * @param licenseValidUntil 라이선스 만료일 (absolute cap 적용)
     * @return OfflineToken 객체 또는 키 미설정 시 null
     */
    public OfflineToken generateOfflineToken(UUID licenseId, String productCode,
                                              String deviceFingerprint, List<String> entitlements,
                                              int allowOfflineDays, Instant licenseValidUntil) {
        if (!keyProvider.isEnabled()) {
            log.warn("OfflineTokenService: RS256 키가 없어 offlineToken을 발급할 수 없습니다.");
            return null;
        }

        Instant now = Instant.now();
        Instant baseExp = now.plus(allowOfflineDays, ChronoUnit.DAYS);

        // v0.2.3: Absolute cap - exp는 licenseValidUntil을 초과할 수 없음
        Instant exp;
        if (licenseValidUntil != null && baseExp.isAfter(licenseValidUntil)) {
            exp = licenseValidUntil;
            log.debug("offlineToken exp capped to licenseValidUntil: {}", exp);
        } else {
            exp = baseExp;
        }

        // v0.2.3: 통일된 claims 구조 + kid 추가
        String token = Jwts.builder()
                .header()
                    .add("alg", "RS256")
                    .add("typ", "JWT")
                    .add("kid", keyProvider.keyId())
                    .and()
                .issuer(issuer)
                .audience().add(productCode).and()
                .subject(licenseId.toString())
                .claim("typ", "offline")  // 토큰 타입 구분
                .claim("dfp", deviceFingerprint)
                .claim("ent", entitlements)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(keyProvider.signingKey(), Jwts.SIG.RS256)
                .compact();

        return new OfflineToken(token, exp);
    }

    /**
     * offlineToken 갱신 필요 여부 확인.
     *
     * 갱신 조건 (v0.2.3):
     * - 남은 기간이 전체의 50% 미만
     * - 또는 남은 기간이 3일 미만
     *
     * @param currentExpiresAt 현재 토큰 만료 시각
     * @param allowOfflineDays 오프라인 허용 일수
     * @return true면 갱신 필요
     */
    public boolean shouldRenew(Instant currentExpiresAt, int allowOfflineDays) {
        if (currentExpiresAt == null) {
            return true; // 토큰 없음 → 발급 필요
        }

        Instant now = Instant.now();
        if (now.isAfter(currentExpiresAt)) {
            return true; // 이미 만료됨
        }

        long remainingSeconds = now.until(currentExpiresAt, ChronoUnit.SECONDS);
        long totalSeconds = allowOfflineDays * 24L * 60 * 60;
        long thresholdDaysSeconds = renewalThresholdDays * 24L * 60 * 60;

        // 조건 1: 남은 기간이 50% 미만
        if (remainingSeconds < totalSeconds * renewalThresholdRatio) {
            return true;
        }

        // 조건 2: 남은 기간이 3일 미만
        if (remainingSeconds < thresholdDaysSeconds) {
            return true;
        }

        return false;
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
     * offlineToken 결과 객체.
     */
    public record OfflineToken(String token, Instant expiresAt) {}
}
