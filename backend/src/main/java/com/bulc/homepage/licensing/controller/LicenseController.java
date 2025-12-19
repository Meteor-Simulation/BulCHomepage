package com.bulc.homepage.licensing.controller;

import com.bulc.homepage.licensing.dto.*;
import com.bulc.homepage.licensing.service.LicenseService;
import com.bulc.homepage.entity.User;
import com.bulc.homepage.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 라이선스 클라이언트용 API Controller.
 *
 * v1.1 변경사항:
 * - 계정 기반 인증 API (Bearer token 필수)
 * - /api/me/licenses 추가
 * - /api/licenses/validate, /heartbeat 계정 기반으로 변경
 *
 * @see LicenseService
 */
@RestController
@RequestMapping("/api/licenses")
@RequiredArgsConstructor
public class LicenseController {

    private final LicenseService licenseService;
    private final UserRepository userRepository;

    // ==========================================
    // v1.1 계정 기반 API (Bearer token 필수)
    // ==========================================

    /**
     * 라이선스 검증 및 활성화 (v1.1 계정 기반).
     * Bearer token 인증된 사용자의 라이선스를 검증합니다.
     *
     * POST /api/licenses/validate
     * v1.1에서 추가됨 - 기존 /{licenseKey}/validate 대체.
     *
     * 응답:
     * - 200 OK: 검증 성공
     * - 403 Forbidden: 검증 실패 (만료, 정지 등)
     * - 409 Conflict: 복수 라이선스 선택 필요 (candidates 포함)
     */
    @PostMapping("/validate")
    public ResponseEntity<ValidationResponse> validateByUser(@Valid @RequestBody ValidateRequest request) {
        UUID userId = getCurrentUserId();
        ValidationResponse response = licenseService.validateAndActivateByUser(userId, request);
        return buildValidationResponse(response);
    }

    /**
     * Heartbeat (v1.1 계정 기반).
     * Bearer token 인증된 사용자의 활성화 상태를 갱신합니다.
     *
     * POST /api/licenses/heartbeat
     * v1.1에서 추가됨 - 기존 /{licenseKey}/heartbeat 대체.
     *
     * 응답:
     * - 200 OK: 갱신 성공
     * - 403 Forbidden: 갱신 실패 (만료, 정지 등)
     * - 409 Conflict: 복수 라이선스 선택 필요 (candidates 포함)
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<ValidationResponse> heartbeatByUser(@Valid @RequestBody ValidateRequest request) {
        UUID userId = getCurrentUserId();
        ValidationResponse response = licenseService.heartbeatByUser(userId, request);
        return buildValidationResponse(response);
    }

    /**
     * ValidationResponse에 따른 HTTP 상태 코드 결정.
     */
    private ResponseEntity<ValidationResponse> buildValidationResponse(ValidationResponse response) {
        if (response.valid()) {
            return ResponseEntity.ok(response);
        }
        // LICENSE_SELECTION_REQUIRED → 409 Conflict
        if ("LICENSE_SELECTION_REQUIRED".equals(response.errorCode())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
        // 기타 실패 → 403 Forbidden
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * 라이선스 상세 조회 (v1.1 소유자 검증).
     * 본인 소유의 라이선스만 조회 가능합니다.
     *
     * GET /api/licenses/{licenseId}
     */
    @GetMapping("/{licenseId}")
    public ResponseEntity<LicenseResponse> getLicense(@PathVariable UUID licenseId) {
        UUID userId = getCurrentUserId();
        LicenseResponse response = licenseService.getLicenseWithOwnerCheck(userId, licenseId);
        return ResponseEntity.ok(response);
    }

    /**
     * 기기 비활성화 (v1.1 소유자 검증).
     * 본인 소유의 라이선스의 기기만 비활성화 가능합니다.
     *
     * DELETE /api/licenses/{licenseId}/activations/{deviceFingerprint}
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
     * User.email을 기반으로 결정적 UUID를 생성합니다.
     */
    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("인증된 사용자가 없습니다");
        }

        String email = authentication.getName();
        // 사용자 존재 여부 확인
        userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다: " + email));

        // email을 기반으로 결정적 UUID 생성 (Type 3 UUID)
        return UUID.nameUUIDFromBytes(email.getBytes(StandardCharsets.UTF_8));
    }
}
