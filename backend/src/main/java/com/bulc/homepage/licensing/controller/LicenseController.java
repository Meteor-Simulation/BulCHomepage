package com.bulc.homepage.licensing.controller;

import com.bulc.homepage.licensing.dto.*;
import com.bulc.homepage.licensing.service.LicenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 라이선스 클라이언트용 API Controller.
 *
 * 이 컨트롤러는 클라이언트 앱에서 필요한 검증/조회 API만 노출합니다.
 *
 * 발급(issue), 정지(suspend), 회수(revoke) 등의 관리 기능은
 * HTTP API로 노출하지 않으며, Billing 모듈에서 LicenseService를
 * 직접 호출하는 방식으로 트리거됩니다.
 *
 * @see LicenseService - 내부 발급/회수 로직은 Billing에서 직접 호출
 */
@RestController
@RequestMapping("/api/licenses")
@RequiredArgsConstructor
public class LicenseController {

    private final LicenseService licenseService;

    // ==========================================
    // 클라이언트 앱용 API (검증/활성화)
    // ==========================================

    /**
     * 라이선스 검증 및 기기 활성화.
     * 클라이언트 앱이 실행 시 호출하여 라이선스 유효성을 확인하고 기기를 활성화합니다.
     *
     * POST /api/licenses/{licenseKey}/validate
     */
    @PostMapping("/{licenseKey}/validate")
    public ResponseEntity<ValidationResponse> validateAndActivate(
            @PathVariable String licenseKey,
            @Valid @RequestBody ActivationRequest request) {
        ValidationResponse response = licenseService.validateAndActivate(licenseKey, request);
        if (response.valid()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }
    }

    /**
     * Heartbeat - 활성화 상태 유지 및 LastSeenAt 갱신.
     * 클라이언트 앱이 주기적으로 호출하여 활성 상태를 유지합니다.
     *
     * POST /api/licenses/{licenseKey}/heartbeat
     */
    @PostMapping("/{licenseKey}/heartbeat")
    public ResponseEntity<ValidationResponse> heartbeat(
            @PathVariable String licenseKey,
            @Valid @RequestBody ActivationRequest request) {
        // heartbeat는 validate와 동일한 로직 (LastSeenAt 갱신)
        ValidationResponse response = licenseService.validateAndActivate(licenseKey, request);
        if (response.valid()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }
    }

    /**
     * 기기 비활성화 (사용자가 직접 기기 해제).
     * 사용자가 특정 기기에서 라이선스를 해제할 때 사용합니다.
     *
     * DELETE /api/licenses/{licenseId}/activations/{deviceFingerprint}
     */
    @DeleteMapping("/{licenseId}/activations/{deviceFingerprint}")
    public ResponseEntity<Void> deactivate(
            @PathVariable UUID licenseId,
            @PathVariable String deviceFingerprint) {
        licenseService.deactivate(licenseId, deviceFingerprint);
        return ResponseEntity.noContent().build();
    }

    // ==========================================
    // 사용자 마이페이지용 API (조회)
    // ==========================================

    /**
     * 라이선스 조회 (ID).
     * 사용자가 자신의 라이선스 상세 정보를 조회합니다.
     *
     * GET /api/licenses/{licenseId}
     *
     * TODO: 인증된 사용자가 자신의 라이선스만 조회할 수 있도록 권한 체크 필요
     */
    @GetMapping("/{licenseId}")
    public ResponseEntity<LicenseResponse> getLicense(@PathVariable UUID licenseId) {
        return ResponseEntity.ok(licenseService.getLicense(licenseId));
    }

    /**
     * 라이선스 조회 (라이선스 키).
     * 라이선스 키로 라이선스 정보를 조회합니다.
     *
     * GET /api/licenses/key/{licenseKey}
     */
    @GetMapping("/key/{licenseKey}")
    public ResponseEntity<LicenseResponse> getLicenseByKey(@PathVariable String licenseKey) {
        return ResponseEntity.ok(licenseService.getLicenseByKey(licenseKey));
    }

    // ==========================================
    // 아래 API들은 의도적으로 제거됨
    // ==========================================
    //
    // POST /api/licenses (발급)
    //   → Billing 모듈에서 결제 완료 시 LicenseService.issueLicense() 직접 호출
    //
    // GET /api/licenses/owner/{ownerType}/{ownerId} (소유자별 조회)
    //   → 마이페이지에서 필요 시 인증된 사용자 기준으로 별도 API 구성
    //
    // POST /api/licenses/{licenseId}/suspend (정지)
    //   → 관리자 모듈에서 LicenseService.suspendLicense() 직접 호출
    //
    // POST /api/licenses/{licenseId}/revoke (회수)
    //   → Billing 모듈에서 환불 시 LicenseService.revokeLicenseByOrderId() 직접 호출
    //
    // POST /api/licenses/{licenseId}/renew (갱신)
    //   → Billing 모듈에서 구독 갱신 시 LicenseService.renewLicense() 직접 호출
}
