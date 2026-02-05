package com.bulc.homepage.controller;

import com.bulc.homepage.dto.request.LoginRequest;
import com.bulc.homepage.dto.request.OAuthSignupRequest;
import com.bulc.homepage.dto.request.PasswordResetRequest;
import com.bulc.homepage.dto.request.RefreshTokenRequest;
import com.bulc.homepage.dto.request.SignupRequest;
import com.bulc.homepage.dto.request.EmailVerificationRequest;
import com.bulc.homepage.dto.request.VerifyCodeRequest;
import com.bulc.homepage.dto.response.ApiResponse;
import com.bulc.homepage.dto.response.AuthResponse;
import com.bulc.homepage.entity.User;
import com.bulc.homepage.exception.DeactivatedAccountException;
import com.bulc.homepage.repository.UserRepository;
import com.bulc.homepage.service.AuthService;
import com.bulc.homepage.service.EmailVerificationService;
import com.bulc.homepage.service.PasswordResetService;
import com.bulc.homepage.service.TokenBlacklistService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserRepository userRepository;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthResponse>> signup(@Valid @RequestBody SignupRequest request) {
        log.info("Signup request for email: {}", request.getEmail());
        try {
            AuthResponse response = authService.signup(request);
            return ResponseEntity.ok(ApiResponse.success("회원가입이 완료되었습니다", response));
        } catch (DeactivatedAccountException e) {
            // 비활성화된 계정 - 409 Conflict와 특별한 에러 코드 반환
            return ResponseEntity.status(409).body(
                ApiResponse.<AuthResponse>builder()
                    .success(false)
                    .message(e.getMessage())
                    .errorCode("ACCOUNT_DEACTIVATED")
                    .build()
            );
        }
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        log.info("Login request for email: {} from IP: {}", request.getEmail(), ipAddress);
        AuthResponse response = authService.login(request, ipAddress, userAgent);

        // SSO 지원: JWT를 httpOnly 쿠키로 설정
        // 브라우저에서 OAuth 요청 시 자동 로그인에 사용됨
        setAuthCookie(httpResponse, response.getAccessToken());
        setRefreshTokenCookie(httpResponse, response.getRefreshToken());

        return ResponseEntity.ok(ApiResponse.success("로그인 성공", response));
    }

    /**
     * Access Token을 httpOnly 쿠키로 설정.
     * OAuth SSO 자동 로그인에 사용됩니다.
     */
    private void setAuthCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("AUTH_TOKEN", token);
        cookie.setHttpOnly(true);  // JavaScript 접근 불가 (XSS 방지)
        cookie.setSecure(false);   // 개발환경에서는 HTTP 허용, 운영에서는 true로 변경 필요
        cookie.setPath("/");       // 전체 경로에서 접근 가능
        cookie.setMaxAge(60 * 60); // 1시간 (accessToken과 동일)
        response.addCookie(cookie);
    }

    /**
     * Refresh Token을 httpOnly 쿠키로 설정.
     * RTR과 함께 사용하여 안전한 토큰 갱신을 지원합니다.
     */
    private void setRefreshTokenCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("REFRESH_TOKEN", token);
        cookie.setHttpOnly(true);  // JavaScript 접근 불가 (XSS 방지)
        cookie.setSecure(false);   // 개발환경에서는 HTTP 허용, 운영에서는 true로 변경 필요
        cookie.setPath("/api/auth/refresh");  // /refresh 엔드포인트에서만 전송
        cookie.setMaxAge(7 * 24 * 60 * 60); // 7일 (refreshToken과 동일)
        response.addCookie(cookie);
    }

    /**
     * 모든 인증 쿠키 삭제 (로그아웃 시 호출).
     */
    private void clearAuthCookies(HttpServletResponse response) {
        // Access Token 쿠키 삭제
        Cookie authCookie = new Cookie("AUTH_TOKEN", "");
        authCookie.setHttpOnly(true);
        authCookie.setPath("/");
        authCookie.setMaxAge(0);  // 즉시 만료
        response.addCookie(authCookie);

        // Refresh Token 쿠키 삭제
        Cookie refreshCookie = new Cookie("REFRESH_TOKEN", "");
        refreshCookie.setHttpOnly(true);
        refreshCookie.setPath("/api/auth/refresh");
        refreshCookie.setMaxAge(0);  // 즉시 만료
        response.addCookie(refreshCookie);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletResponse httpResponse) {
        log.info("Token refresh request");
        AuthResponse response = authService.refreshToken(request);

        // [RTR] 새 토큰으로 쿠키 갱신
        setAuthCookie(httpResponse, response.getAccessToken());
        setRefreshTokenCookie(httpResponse, response.getRefreshToken());

        return ResponseEntity.ok(ApiResponse.success("토큰 갱신 성공", response));
    }

    /**
     * 로그아웃 (토큰 블랙리스트 추가 + RTR 무효화 + 쿠키 삭제)
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        String token = extractTokenFromRequest(request);

        // SSO 쿠키 삭제 (Access Token + Refresh Token)
        clearAuthCookies(response);

        if (token == null) {
            // 쿠키는 삭제했으므로 성공으로 처리
            return ResponseEntity.ok(ApiResponse.success("로그아웃 성공", null));
        }

        try {
            String email = authService.getEmailFromToken(token);

            // Access Token 블랙리스트 추가
            tokenBlacklistService.blacklistToken(token, email);

            // [RTR] Refresh Token 무효화 (DB에서 삭제)
            authService.invalidateRefreshToken(email);

            log.info("로그아웃 성공 - 사용자: {}", email);
            return ResponseEntity.ok(ApiResponse.success("로그아웃 성공", null));
        } catch (Exception e) {
            log.error("로그아웃 실패: {}", e.getMessage());
            // 쿠키는 이미 삭제했으므로 성공으로 처리
            return ResponseEntity.ok(ApiResponse.success("로그아웃 성공", null));
        }
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * 현재 로그인한 사용자 정보 조회 (OAuth 로그인용)
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthResponse.UserInfo>> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() ||
            "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body(ApiResponse.error("인증되지 않은 사용자입니다."));
        }

        String email = authentication.getName();
        log.info("Get current user request for email: {}", email);

        User user = userRepository.findById(email)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        AuthResponse.UserInfo userInfo = AuthResponse.UserInfo.builder()
                .id(user.getEmail())
                .email(user.getEmail())
                .name(user.getName())
                .rolesCode(user.getRolesCode())
                .language(user.getLanguageCode())
                .build();

        return ResponseEntity.ok(ApiResponse.success("사용자 정보 조회 성공", userInfo));
    }

    /**
     * 이메일 중복 체크 (비활성화 계정 구분)
     */
    @GetMapping("/check-email")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkEmail(@RequestParam String email) {
        log.info("Email check request: {}", email);
        Boolean isActive = emailVerificationService.checkEmailStatus(email);

        if (isActive == null) {
            // 미가입 이메일
            return ResponseEntity.ok(ApiResponse.success(
                    "사용 가능한 이메일입니다",
                    Map.of("exists", false, "deactivated", false)
            ));
        } else if (isActive) {
            // 활성화된 계정
            return ResponseEntity.ok(ApiResponse.success(
                    "이미 가입된 이메일입니다",
                    Map.of("exists", true, "deactivated", false)
            ));
        } else {
            // 비활성화된 계정
            return ResponseEntity.ok(ApiResponse.success(
                    "비활성화된 계정이 존재합니다",
                    Map.of("exists", true, "deactivated", true)
            ));
        }
    }

    /**
     * 이메일 인증 코드 발송
     */
    @PostMapping("/send-verification")
    public ResponseEntity<ApiResponse<Void>> sendVerification(@Valid @RequestBody EmailVerificationRequest request) {
        log.info("Verification code request for email: {}", request.getEmail());
        emailVerificationService.sendVerificationCode(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success("인증 코드가 발송되었습니다", null));
    }

    /**
     * 이메일 인증 코드 검증
     */
    @PostMapping("/verify-code")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> verifyCode(@Valid @RequestBody VerifyCodeRequest request) {
        log.info("Verify code request for email: {}", request.getEmail());
        boolean verified = emailVerificationService.verifyCode(request.getEmail(), request.getCode());
        return ResponseEntity.ok(ApiResponse.success(
                "이메일 인증이 완료되었습니다",
                Map.of("verified", verified)
        ));
    }

    /**
     * OAuth 회원가입 완료 (비밀번호 설정)
     */
    @PostMapping("/oauth/signup")
    public ResponseEntity<ApiResponse<AuthResponse>> oauthSignup(@Valid @RequestBody OAuthSignupRequest request) {
        log.info("OAuth signup request");
        AuthResponse response = authService.oauthSignup(request);
        return ResponseEntity.ok(ApiResponse.success("회원가입이 완료되었습니다", response));
    }

    /**
     * 계정 재활성화 요청 (이메일 인증 발송)
     */
    @PostMapping("/reactivate/request")
    public ResponseEntity<ApiResponse<Void>> requestReactivation(
            @Valid @RequestBody EmailVerificationRequest request) {
        log.info("Reactivation request for email: {}", request.getEmail());

        // 비활성화된 계정인지 확인
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(ApiResponse.error("존재하지 않는 이메일입니다."));
        }
        if (user.getIsActive()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("이미 활성화된 계정입니다."));
        }

        // 이메일 인증 코드 발송
        emailVerificationService.sendVerificationCode(request.getEmail());
        log.info("Reactivation verification code sent to: {}", request.getEmail());

        return ResponseEntity.ok(ApiResponse.success("인증 코드가 발송되었습니다", null));
    }

    /**
     * 계정 재활성화 완료 (이메일 인증 확인 후 활성화)
     */
    @PostMapping("/reactivate/confirm")
    public ResponseEntity<ApiResponse<AuthResponse>> confirmReactivation(
            @Valid @RequestBody VerifyCodeRequest request) {
        log.info("Reactivation confirm for email: {}", request.getEmail());

        // 이메일 인증 확인
        boolean verified = emailVerificationService.verifyCode(request.getEmail(), request.getCode());
        if (!verified) {
            return ResponseEntity.badRequest().body(ApiResponse.error("인증 코드가 올바르지 않습니다."));
        }

        // 사용자 조회
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(ApiResponse.error("존재하지 않는 이메일입니다."));
        }
        if (user.getIsActive()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("이미 활성화된 계정입니다."));
        }

        // 계정 재활성화
        user.setIsActive(true);
        user.setDeactivatedAt(null);
        userRepository.save(user);

        log.info("Account reactivated: {}", request.getEmail());

        // JWT 토큰 발급
        String accessToken = authService.generateAccessToken(user.getEmail());
        String refreshToken = authService.generateRefreshToken(user.getEmail());

        AuthResponse response = AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getEmail())
                        .email(user.getEmail())
                        .name(user.getName())
                        .rolesCode(user.getRolesCode())
                        .language(user.getLanguageCode())
                        .build())
                .build();

        return ResponseEntity.ok(ApiResponse.success("계정이 재활성화되었습니다", response));
    }

    /**
     * 비밀번호 재설정 요청 (인증 코드 발송)
     */
    @PostMapping("/password/reset-request")
    public ResponseEntity<ApiResponse<Void>> requestPasswordReset(
            @Valid @RequestBody EmailVerificationRequest request) {
        log.info("Password reset request for email: {}", request.getEmail());
        try {
            passwordResetService.requestPasswordReset(request.getEmail());
            return ResponseEntity.ok(ApiResponse.success("비밀번호 재설정 코드가 발송되었습니다", null));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 비밀번호 재설정 코드 검증
     */
    @PostMapping("/password/verify-code")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> verifyPasswordResetCode(
            @Valid @RequestBody VerifyCodeRequest request) {
        log.info("Password reset code verification for email: {}", request.getEmail());
        try {
            boolean verified = passwordResetService.verifyResetCode(request.getEmail(), request.getCode());
            return ResponseEntity.ok(ApiResponse.success(
                    "인증 코드가 확인되었습니다",
                    Map.of("verified", verified)
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 비밀번호 재설정 완료
     */
    @PostMapping("/password/reset")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody PasswordResetRequest request) {
        log.info("Password reset for email: {}", request.getEmail());
        try {
            passwordResetService.resetPassword(request.getEmail(), request.getCode(), request.getNewPassword());
            return ResponseEntity.ok(ApiResponse.success("비밀번호가 재설정되었습니다", null));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For는 콤마로 구분된 여러 IP를 포함할 수 있음 (첫 번째가 클라이언트)
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
