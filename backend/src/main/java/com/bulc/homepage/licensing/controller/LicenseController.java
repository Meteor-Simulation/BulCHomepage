package com.bulc.homepage.licensing.controller;

import com.bulc.homepage.licensing.dto.*;
import com.bulc.homepage.licensing.service.LicenseService;
import com.bulc.homepage.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 라이선스 클라이언트용 API Controller.
 *
 * v0.3.0 변경사항:
 * - Auto-Resolve: 서버 자동 라이선스/세션 선택
 * - resolution 필드: OK, AUTO_RECOVERED, USER_ACTION_REQUIRED
 * - ALL_LICENSES_FULL (409) 통합 에러 코드
 *
 * v0.2.0 변경사항:
 * - 계정 기반 인증 API (Bearer token 필수)
 * - /api/v1/me/licenses 추가
 * - /api/v1/licenses/validate, /heartbeat 계정 기반으로 변경
 *
 * @see LicenseService
 */
@RestController
@RequestMapping("/api/v1/licenses")
@RequiredArgsConstructor
public class LicenseController {

    private final LicenseService licenseService;
    private final UserRepository userRepository;

    // ==========================================
    // v0.2.0 계정 기반 API (Bearer token 필수)
    // ==========================================

    /**
     * v0.3.0: 라이선스 검증 및 활성화 (Auto-Resolve).
     * Bearer token 인증된 사용자의 라이선스를 검증합니다.
     *
     * POST /api/v1/licenses/validate
     *
     * Auto-Resolve 동작:
     * 1. 빈 슬롯 있으면 바로 활성화 (resolution: OK)
     * 2. stale 세션 있으면 자동 종료 후 활성화 (resolution: AUTO_RECOVERED)
     * 3. 모두 full이면 세션 목록 반환 (resolution: USER_ACTION_REQUIRED)
     *
     * 응답:
     * - 200 OK: 검증 성공 (resolution: OK 또는 AUTO_RECOVERED)
     * - 403 Forbidden: 검증 실패 (만료, 정지 등)
     * - 409 Conflict: 모든 라이선스 full (ALL_LICENSES_FULL, activeSessions 포함)
     */
    @PostMapping("/validate")
    public ResponseEntity<ValidationResponse> validateByUser(@Valid @RequestBody ValidateRequest request) {
        UUID userId = getCurrentUserId();
        ValidationResponse response = licenseService.validateAndActivateByUser(userId, request);
        return buildValidationResponse(response);
    }

    /**
     * v0.3.0: Heartbeat (계정 기반).
     * Bearer token 인증된 사용자의 활성화 상태를 갱신합니다.
     *
     * POST /api/v1/licenses/heartbeat
     *
     * Heartbeat은 validate와 달리:
     * - 이미 활성화된 기기에서만 호출 가능
     * - 새로운 기기 활성화는 불가
     * - Auto-Resolve로 라이선스 자동 선택 (Device Affinity)
     *
     * 응답:
     * - 200 OK: 갱신 성공 (resolution: OK)
     * - 403 Forbidden: 갱신 실패 (만료, 정지, SESSION_DEACTIVATED 등)
     * - 404 Not Found: 활성화된 세션 없음
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<ValidationResponse> heartbeatByUser(@Valid @RequestBody ValidateRequest request) {
        UUID userId = getCurrentUserId();
        ValidationResponse response = licenseService.heartbeatByUser(userId, request);
        return buildValidationResponse(response);
    }

    /**
     * v0.3.0: 강제 검증 및 활성화 (Session Kick).
     * 모든 라이선스 full 시 기존 세션을 비활성화하고 새 세션을 활성화.
     *
     * POST /api/v1/licenses/validate/force
     *
     * 요청 흐름:
     * 1. /validate 호출 → 409 ALL_LICENSES_FULL (activeSessions 포함)
     * 2. 클라이언트가 비활성화할 세션 선택
     * 3. /validate/force 호출 (licenseId, deactivateActivationIds 포함)
     *
     * 응답:
     * - 200 OK: 활성화 성공 (resolution: OK)
     * - 403 Forbidden: 검증 실패 (만료, 정지 등)
     * - 409 Conflict: Race condition으로 여전히 full (재시도 필요)
     */
    @PostMapping("/validate/force")
    public ResponseEntity<ValidationResponse> forceValidateByUser(@Valid @RequestBody ForceValidateRequest request) {
        UUID userId = getCurrentUserId();
        ValidationResponse response = licenseService.forceValidateByUser(userId, request);
        return buildValidationResponse(response);
    }

    /**
     * v0.3.0: ValidationResponse에 따른 HTTP 상태 코드 결정.
     */
    private ResponseEntity<ValidationResponse> buildValidationResponse(ValidationResponse response) {
        if (response.valid()) {
            return ResponseEntity.ok(response);
        }
        // v0.3.0: ALL_LICENSES_FULL → 409 Conflict
        if ("ALL_LICENSES_FULL".equals(response.errorCode())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
        // 기타 실패 (LICENSE_EXPIRED, LICENSE_SUSPENDED 등) → 403 Forbidden
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * 라이선스 상세 조회 (v0.2.0 소유자 검증).
     * 본인 소유의 라이선스만 조회 가능합니다.
     *
     * GET /api/v1/licenses/{licenseId}
     */
    @GetMapping("/{licenseId}")
    public ResponseEntity<LicenseResponse> getLicense(@PathVariable UUID licenseId) {
        UUID userId = getCurrentUserId();
        LicenseResponse response = licenseService.getLicenseWithOwnerCheck(userId, licenseId);
        return ResponseEntity.ok(response);
    }

    /**
     * 기기 비활성화 (v0.2.0 소유자 검증).
     * 본인 소유의 라이선스의 기기만 비활성화 가능합니다.
     *
     * DELETE /api/v1/licenses/{licenseId}/activations/{deviceFingerprint}
     */
    @DeleteMapping("/{licenseId}/activations/{deviceFingerprint}")
    public ResponseEntity<Void> deactivate(
            @PathVariable UUID licenseId,
            @PathVariable String deviceFingerprint) {
        UUID userId = getCurrentUserId();
        licenseService.deactivateWithOwnerCheck(userId, licenseId, deviceFingerprint);
        return ResponseEntity.noContent().build();
    }

    // ==========================================
    // Private 헬퍼 메서드
    // ==========================================

    /**
     * 현재 인증된 사용자의 ID를 UUID로 반환.
     * auth.getName()은 이제 userId.toString()을 반환합니다.
     */
    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("인증된 사용자가 없습니다");
        }

        UUID userId = UUID.fromString(authentication.getName());
        // 사용자 존재 여부 확인
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다: " + userId));

        return userId;
    }
}
