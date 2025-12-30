package com.bulc.homepage.controller;

import com.bulc.homepage.dto.request.LoginRequest;
import com.bulc.homepage.dto.request.OAuthSignupRequest;
import com.bulc.homepage.dto.request.RefreshTokenRequest;
import com.bulc.homepage.dto.request.SignupRequest;
import com.bulc.homepage.dto.request.EmailVerificationRequest;
import com.bulc.homepage.dto.request.VerifyCodeRequest;
import com.bulc.homepage.dto.response.ApiResponse;
import com.bulc.homepage.dto.response.AuthResponse;
import com.bulc.homepage.entity.User;
import com.bulc.homepage.repository.UserRepository;
import com.bulc.homepage.service.AuthService;
import com.bulc.homepage.service.EmailVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthResponse>> signup(@Valid @RequestBody SignupRequest request) {
        log.info("Signup request for email: {}", request.getEmail());
        AuthResponse response = authService.signup(request);
        return ResponseEntity.ok(ApiResponse.success("회원가입이 완료되었습니다", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        log.info("Login request for email: {} from IP: {}", request.getEmail(), ipAddress);
        AuthResponse response = authService.login(request, ipAddress, userAgent);
        return ResponseEntity.ok(ApiResponse.success("로그인 성공", response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("Token refresh request");
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("토큰 갱신 성공", response));
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
                .build();

        return ResponseEntity.ok(ApiResponse.success("사용자 정보 조회 성공", userInfo));
    }

    /**
     * 이메일 중복 체크
     */
    @GetMapping("/check-email")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> checkEmail(@RequestParam String email) {
        log.info("Email check request: {}", email);
        boolean exists = emailVerificationService.isEmailExists(email);
        return ResponseEntity.ok(ApiResponse.success(
                exists ? "이미 가입된 이메일입니다" : "사용 가능한 이메일입니다",
                Map.of("exists", exists)
        ));
    }

    /**
     * 이메일 인증 코드 발송
     */
    @PostMapping("/send-verification")
    public ResponseEntity<ApiResponse<Map<String, String>>> sendVerification(@Valid @RequestBody EmailVerificationRequest request) {
        log.info("Verification code request for email: {}", request.getEmail());
        String code = emailVerificationService.sendVerificationCode(request.getEmail());
        // 개발 환경에서는 코드 반환, 운영에서는 제거
        return ResponseEntity.ok(ApiResponse.success(
                "인증 코드가 발송되었습니다",
                Map.of("code", code) // TODO: 운영 환경에서 제거
        ));
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
     * 비밀번호 해시 생성 (개발용 - 운영 환경에서 제거)
     */
    @GetMapping("/hash")
    public ResponseEntity<ApiResponse<Map<String, String>>> generateHash(@RequestParam String password) {
        String hash = passwordEncoder.encode(password);
        log.info("Generated hash for password");
        return ResponseEntity.ok(ApiResponse.success("해시 생성 완료", Map.of("hash", hash)));
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
