package com.bulc.homepage.licensing.controller;

import com.bulc.homepage.licensing.domain.LicenseStatus;
import com.bulc.homepage.licensing.dto.MyLicenseView;
import com.bulc.homepage.licensing.dto.MyLicensesResponse;
import com.bulc.homepage.licensing.service.LicenseService;
import com.bulc.homepage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 내 라이선스 API Controller.
 * v0.2.0에서 추가됨.
 *
 * GET /api/v1/me/licenses - 내 라이선스 목록 조회
 */
@RestController
@RequestMapping("/api/v1/me/licenses")
@RequiredArgsConstructor
public class MyLicenseController {

    private final LicenseService licenseService;
    private final UserRepository userRepository;

    /**
     * 내 라이선스 목록 조회.
     * 현재 로그인한 사용자의 라이선스 목록을 반환합니다.
     *
     * GET /api/v1/me/licenses
     * GET /api/v1/me/licenses?productId={uuid}
     * GET /api/v1/me/licenses?status=ACTIVE
     */
    @GetMapping
    public ResponseEntity<MyLicensesResponse> getMyLicenses(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) LicenseStatus status) {
        UUID userId = getCurrentUserId();
        List<MyLicenseView> licenses = licenseService.getMyLicenses(userId, productId, status);
        return ResponseEntity.ok(MyLicensesResponse.of(licenses));
    }

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
