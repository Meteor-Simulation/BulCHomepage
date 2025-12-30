package com.bulc.homepage.licensing.dto;

import com.bulc.homepage.licensing.domain.LicenseStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 라이선스 검증 응답 DTO.
 *
 * 성공 시: valid=true, licenseId/status/validUntil/entitlements/offlineToken/sessionToken 제공
 * 실패 시: valid=false, errorCode/errorMessage 제공
 * 선택 필요 시: valid=false, errorCode=LICENSE_SELECTION_REQUIRED, candidates 제공
 * 동시 세션 초과 시 (v1.1.1): valid=false, errorCode=CONCURRENT_SESSION_LIMIT_EXCEEDED, activeSessions 제공
 *
 * v1.1.2: sessionToken (JWS RS256 서명) 필수 추가 - CLI 바꿔치기/session.json 조작 방어
 *
 * 클라이언트 필수 검증 규칙 (sessionToken):
 * 1. RS256 서명 검증 (내장 공개키)
 * 2. aud == productCode (대상 제품 일치)
 * 3. dfp == 현재 기기 fingerprint (기기 바인딩)
 * 4. exp > now (만료되지 않음, ±2분 clock skew 허용)
 * 5. ent 배열로 기능 unlock 결정
 */
public record ValidationResponse(
        boolean valid,
        UUID licenseId,
        LicenseStatus status,
        Instant validUntil,
        List<String> entitlements,
        String sessionToken,           // v1.1.2: JWS RS256 서명 - 만료는 exp 클레임으로 판단
        String offlineToken,
        Instant offlineTokenExpiresAt,
        Instant serverTime,           // 클라이언트 시간 조작 방어용
        String errorCode,
        String errorMessage,
        List<LicenseCandidate> candidates,     // 복수 라이선스 선택 시
        List<ActiveSessionInfo> activeSessions, // v1.1.1: 동시 세션 초과 시
        Integer maxConcurrentSessions           // v1.1.1: 최대 동시 세션 수
) {
    /**
     * 복수 라이선스 선택 시 후보 정보.
     * licenseKey를 노출하지 않고 사용자가 선택할 수 있는 정보만 제공.
     */
    public record LicenseCandidate(
            UUID licenseId,
            String planName,           // "Pro 연간 구독"
            String licenseType,        // "SUBSCRIPTION", "PERPETUAL"
            LicenseStatus status,      // ACTIVE, EXPIRED_GRACE
            Instant validUntil,
            String ownerScope,         // "개인" / "조직: ABC Corp"
            int activeDevices,         // 현재 활성화된 기기 수
            int maxDevices,            // 최대 기기 수
            String label               // 사용자 지정 라벨 (옵션)
    ) {}

    /**
     * v1.1.1: 동시 세션 초과 시 현재 활성 세션 정보.
     * 클라이언트가 어떤 세션을 비활성화할지 선택할 수 있도록 정보 제공.
     */
    public record ActiveSessionInfo(
            UUID activationId,
            String deviceDisplayName,  // "MacBook Pro" 등 사용자 친화적 이름
            String deviceFingerprint,  // 기기 식별자 (마스킹 가능)
            Instant lastSeenAt,        // 마지막 접속 시간
            String clientOs,           // "macOS 14.2"
            String clientVersion       // "1.2.3"
    ) {}

    /**
     * v1.1.2: 성공 응답 (sessionToken 포함).
     *
     * sessionToken은 CLI/앱에서 RS256 서명 검증 후 기능 unlock 여부 결정에 사용.
     * sessionToken 만료 시각은 토큰 내부 exp 클레임으로 판단 (별도 필드 없음).
     *
     * @param sessionToken RS256 서명된 JWT (null일 수 있음 - dev 환경에서 키 미설정 시)
     */
    public static ValidationResponse success(UUID licenseId, LicenseStatus status, Instant validUntil,
                                              List<String> entitlements, String sessionToken,
                                              String offlineToken, Instant offlineTokenExpiresAt) {
        return new ValidationResponse(true, licenseId, status, validUntil, entitlements,
                sessionToken, offlineToken, offlineTokenExpiresAt,
                Instant.now(), null, null, null, null, null);
    }

    public static ValidationResponse failure(String errorCode, String errorMessage) {
        return new ValidationResponse(false, null, null, null, null, null, null, null,
                Instant.now(), errorCode, errorMessage, null, null, null);
    }

    /**
     * 복수 라이선스 선택 필요 시 응답.
     */
    public static ValidationResponse selectionRequired(List<LicenseCandidate> candidates) {
        return new ValidationResponse(false, null, null, null, null, null, null, null,
                Instant.now(), "LICENSE_SELECTION_REQUIRED",
                "복수의 라이선스가 존재합니다. licenseId를 지정해주세요", candidates, null, null);
    }

    /**
     * v1.1.1: 동시 세션 제한 초과 시 응답.
     * 클라이언트는 이 응답을 받으면 비활성화할 세션을 선택하여 /validate/force 호출.
     */
    public static ValidationResponse concurrentSessionLimitExceeded(
            UUID licenseId, List<ActiveSessionInfo> activeSessions, int maxConcurrentSessions) {
        return new ValidationResponse(false, licenseId, null, null, null, null, null, null,
                Instant.now(), "CONCURRENT_SESSION_LIMIT_EXCEEDED",
                "동시 세션 수를 초과했습니다. 기존 세션을 비활성화하거나 다른 기기를 사용해주세요",
                null, activeSessions, maxConcurrentSessions);
    }
}
