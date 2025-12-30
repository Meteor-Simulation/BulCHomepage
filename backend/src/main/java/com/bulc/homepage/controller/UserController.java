package com.bulc.homepage.controller;

import com.bulc.homepage.config.ValidationConfig;
import com.bulc.homepage.entity.User;
import com.bulc.homepage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 현재 로그인한 사용자 정보 조회
     */
    @GetMapping("/me")
    public ResponseEntity<UserInfoResponse> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).build();
        }

        String email = auth.getName();
        User user = userRepository.findByEmail(email)
                .orElse(null);

        if (user == null) {
            return ResponseEntity.status(404).build();
        }

        return ResponseEntity.ok(new UserInfoResponse(
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                user.getCountryCode()
        ));
    }

    /**
     * 사용자 정보 업데이트 (이름, 전화번호, 국가)
     */
    @PutMapping("/me")
    public ResponseEntity<UserInfoResponse> updateCurrentUser(@RequestBody UpdateUserRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).build();
        }

        String email = auth.getName();
        User user = userRepository.findByEmail(email)
                .orElse(null);

        if (user == null) {
            return ResponseEntity.status(404).build();
        }

        // 이름이 비어있지 않으면 업데이트
        if (request.name() != null && !request.name().isBlank()) {
            user.setName(request.name());
        }

        // 전화번호가 비어있지 않으면 업데이트
        if (request.phone() != null && !request.phone().isBlank()) {
            user.setPhone(request.phone());
        }

        // 국가가 비어있지 않으면 업데이트
        if (request.country() != null && !request.country().isBlank()) {
            user.setCountryCode(request.country());
        }

        userRepository.save(user);

        return ResponseEntity.ok(new UserInfoResponse(
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                user.getCountryCode()
        ));
    }

    /**
     * 비밀번호 변경
     */
    @PutMapping("/me/password")
    public ResponseEntity<ApiResponse> changePassword(@RequestBody ChangePasswordRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body(new ApiResponse(false, "인증이 필요합니다."));
        }

        String email = auth.getName();
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            return ResponseEntity.status(404).body(new ApiResponse(false, "사용자를 찾을 수 없습니다."));
        }

        // 현재 비밀번호 확인
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "현재 비밀번호가 일치하지 않습니다."));
        }

        // 새 비밀번호 유효성 검사
        String validationError = validatePassword(request.newPassword());
        if (validationError != null) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, validationError));
        }

        // 비밀번호 변경
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        return ResponseEntity.ok(new ApiResponse(true, "비밀번호가 변경되었습니다."));
    }

    /**
     * 비밀번호 유효성 검사
     */
    private String validatePassword(String password) {
        if (password == null || password.length() < ValidationConfig.PASSWORD_MIN_LENGTH) {
            return "비밀번호는 " + ValidationConfig.PASSWORD_MIN_LENGTH + "자 이상이어야 합니다.";
        }
        if (password.length() > ValidationConfig.PASSWORD_MAX_LENGTH) {
            return "비밀번호는 " + ValidationConfig.PASSWORD_MAX_LENGTH + "자 이하여야 합니다.";
        }
        if (ValidationConfig.PASSWORD_REQUIRE_LETTER && !password.matches(".*[a-zA-Z].*")) {
            return "비밀번호는 영문을 포함해야 합니다.";
        }
        if (ValidationConfig.PASSWORD_REQUIRE_DIGIT && !password.matches(".*[0-9].*")) {
            return "비밀번호는 숫자를 포함해야 합니다.";
        }
        if (ValidationConfig.PASSWORD_REQUIRE_SPECIAL_CHAR && !password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{}|;':\",./<>?].*")) {
            return "비밀번호는 특수문자를 포함해야 합니다.";
        }
        return null;
    }

    // DTOs
    public record UserInfoResponse(String email, String name, String phone, String country) {}
    public record UpdateUserRequest(String name, String phone, String country) {}
    public record ChangePasswordRequest(String currentPassword, String newPassword) {}
    public record ApiResponse(boolean success, String message) {}
}
