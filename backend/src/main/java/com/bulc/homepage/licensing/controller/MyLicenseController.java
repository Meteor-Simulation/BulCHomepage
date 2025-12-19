package com.bulc.homepage.licensing.controller;

import com.bulc.homepage.licensing.domain.LicenseStatus;
import com.bulc.homepage.licensing.dto.MyLicenseView;
import com.bulc.homepage.licensing.dto.MyLicensesResponse;
import com.bulc.homepage.licensing.service.LicenseService;
import com.bulc.homepage.entity.User;
import com.bulc.homepage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 내 라이선스 API Controller.
 * v1.1에서 추가됨.
 *
 * GET /api/me/licenses - 내 라이선스 목록 조회
 */
@RestController
@RequestMapping("/api/me/licenses")
@RequiredArgsConstructor
public class MyLicenseController {

    private final LicenseService licenseService;
    private final UserRepository userRepository;

    /**
     * 내 라이선스 목록 조회.
     * 현재 로그인한 사용자의 라이선스 목록을 반환합니다.
     *
     * GET /api/me/licenses
     * GET /api/me/licenses?productId={uuid}
     * GET /api/me/licenses?status=ACTIVE
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
