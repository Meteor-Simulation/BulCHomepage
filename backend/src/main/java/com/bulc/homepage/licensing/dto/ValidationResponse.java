package com.bulc.homepage.licensing.dto;

import com.bulc.homepage.licensing.domain.LicenseStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 라이선스 검증 응답 DTO.
 *
 * v0.3.0 응답 구조:
 * - resolution: "OK", "AUTO_RECOVERED", "USER_ACTION_REQUIRED"
 * - OK: 빈 슬롯 있어서 정상 활성화
 * - AUTO_RECOVERED: stale 세션 자동 종료 후 활성화 (recoveryAction/terminatedSession 포함)
 * - USER_ACTION_REQUIRED: 모든 라이선스 full, 사용자가 세션 선택하여 kick 필요
 *
 * 성공 시: valid=true, resolution=OK/AUTO_RECOVERED, licenseId/status/validUntil/entitlements/sessionToken 제공
 * 실패 시: valid=false, resolution=USER_ACTION_REQUIRED, errorCode/errorMessage/activeSessions 제공
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

        // v0.3.0: Resolution 필드
        String resolution,           // "OK", "AUTO_RECOVERED", "USER_ACTION_REQUIRED"
        String actionRequired,       // USER_ACTION_REQUIRED 시: "KICK_REQUIRED"
        String recoveryAction,       // AUTO_RECOVERED 시: "STALE_SESSION_TERMINATED"
        TerminatedSessionInfo terminatedSession,  // AUTO_RECOVERED 시: 종료된 세션 정보

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
        List<LicenseCandidate> candidates,     // v0.3.0: deprecated (하위 호환용 유지)
        List<GlobalSessionInfo> activeSessions, // v0.3.0: 모든 후보 라이선스의 세션 통합 목록
        Integer maxConcurrentSessions           // v1.1.1: 최대 동시 세션 수
) {
    /**
     * 복수 라이선스 선택 시 후보 정보.
     * v0.3.0: deprecated - 서버 자동 선택으로 더 이상 사용하지 않음 (하위 호환용 유지)
     */
    @Deprecated
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
     * v0.3.0: Global Session Info - 모든 후보 라이선스의 세션을 통합하여 반환.
     * 클라이언트가 어떤 세션을 비활성화할지 선택할 수 있도록 정보 제공.
     */
    public record GlobalSessionInfo(
            UUID licenseId,            // v0.3.0: 세션이 속한 라이선스 ID
            String productName,        // v0.3.0: 제품명 (예: "METEOR Pro")
            String planName,           // v0.3.0: 플랜명 (예: "Pro 연간 구독")
            UUID activationId,
            String deviceDisplayName,  // "MacBook Pro" 등 사용자 친화적 이름
            String deviceFingerprint,  // 기기 식별자 (마스킹 가능)
            Instant lastSeenAt,        // 마지막 접속 시간
            String clientOs,           // "macOS 14.2"
            String clientVersion,      // "1.2.3"
            boolean isStale            // v0.3.0: 30분 기준 stale 여부
    ) {}

    /**
     * v0.3.0: AUTO_RECOVERED 시 종료된 세션 정보.
     */
    public record TerminatedSessionInfo(
            String deviceDisplayName,
            Instant lastSeenAt
    ) {}

    /**
     * v1.1.1: 동시 세션 초과 시 현재 활성 세션 정보.
     * v0.3.0: deprecated - GlobalSessionInfo로 대체
     */
    @Deprecated
    public record ActiveSessionInfo(
            UUID activationId,
            String deviceDisplayName,
            String deviceFingerprint,
            Instant lastSeenAt,
            String clientOs,
            String clientVersion
    ) {}

    /**
     * v0.3.0: 성공 응답 (resolution: OK).
     */
    public static ValidationResponse success(UUID licenseId, LicenseStatus status, Instant validUntil,
                                              List<String> entitlements, String sessionToken,
                                              String offlineToken, Instant offlineTokenExpiresAt) {
        return new ValidationResponse(true, "OK", null, null, null,
                licenseId, status, validUntil, entitlements,
                sessionToken, offlineToken, offlineTokenExpiresAt,
                Instant.now(), null, null, null, null, null);
    }

    /**
     * v0.3.0: 성공 응답 (resolution: AUTO_RECOVERED).
     * stale 세션 자동 종료 후 활성화 성공.
     */
    public static ValidationResponse successWithRecovery(UUID licenseId, LicenseStatus status, Instant validUntil,
                                                          List<String> entitlements, String sessionToken,
                                                          String offlineToken, Instant offlineTokenExpiresAt,
                                                          TerminatedSessionInfo terminatedSession) {
        return new ValidationResponse(true, "AUTO_RECOVERED", null, "STALE_SESSION_TERMINATED", terminatedSession,
                licenseId, status, validUntil, entitlements,
                sessionToken, offlineToken, offlineTokenExpiresAt,
                Instant.now(), null, null, null, null, null);
    }

    public static ValidationResponse failure(String errorCode, String errorMessage) {
        return new ValidationResponse(false, null, null, null, null,
                null, null, null, null, null, null, null,
                Instant.now(), errorCode, errorMessage, null, null, null);
    }

    /**
     * v0.3.0: 모든 라이선스 full 시 응답 (resolution: USER_ACTION_REQUIRED).
     * 클라이언트는 이 응답을 받으면 비활성화할 세션을 선택하여 /validate/force 호출.
     */
    public static ValidationResponse allLicensesFull(List<GlobalSessionInfo> activeSessions) {
        return new ValidationResponse(false, "USER_ACTION_REQUIRED", "KICK_REQUIRED", null, null,
                null, null, null, null, null, null, null,
                Instant.now(), "ALL_LICENSES_FULL",
                "사용 가능한 라이선스가 없습니다. 접속을 위해 종료할 세션을 선택해주세요",
                null, activeSessions, null);
    }

    /**
     * 복수 라이선스 선택 필요 시 응답.
     * v0.3.0: deprecated - 서버가 자동 선택하므로 더 이상 사용하지 않음
     */
    @Deprecated
    public static ValidationResponse selectionRequired(List<LicenseCandidate> candidates) {
        return new ValidationResponse(false, "USER_ACTION_REQUIRED", null, null, null,
                null, null, null, null, null, null, null,
                Instant.now(), "LICENSE_SELECTION_REQUIRED",
                "복수의 라이선스가 존재합니다. licenseId를 지정해주세요", candidates, null, null);
    }

    /**
     * v1.1.1: 동시 세션 제한 초과 시 응답.
     * v0.3.0: deprecated - allLicensesFull()로 대체
     */
    @Deprecated
    public static ValidationResponse concurrentSessionLimitExceeded(
            UUID licenseId, List<ActiveSessionInfo> activeSessions, int maxConcurrentSessions) {
        // 하위 호환을 위해 GlobalSessionInfo로 변환
        List<GlobalSessionInfo> globalSessions = activeSessions.stream()
                .map(s -> new GlobalSessionInfo(
                        licenseId, null, null,
                        s.activationId(), s.deviceDisplayName(), s.deviceFingerprint(),
                        s.lastSeenAt(), s.clientOs(), s.clientVersion(), false))
                .toList();

        return new ValidationResponse(false, "USER_ACTION_REQUIRED", "KICK_REQUIRED", null, null,
                licenseId, null, null, null, null, null, null,
                Instant.now(), "CONCURRENT_SESSION_LIMIT_EXCEEDED",
                "동시 세션 수를 초과했습니다. 기존 세션을 비활성화하거나 다른 기기를 사용해주세요",
                null, globalSessions, maxConcurrentSessions);
    }
}
