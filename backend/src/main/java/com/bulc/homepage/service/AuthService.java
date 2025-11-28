package com.bulc.homepage.service;

import com.bulc.homepage.dto.request.LoginRequest;
import com.bulc.homepage.dto.request.SignupRequest;
import com.bulc.homepage.dto.response.AuthResponse;
import com.bulc.homepage.entity.AuthLoginAttempt;
import com.bulc.homepage.entity.User;
import com.bulc.homepage.entity.UserProfile;
import com.bulc.homepage.entity.UserRole;
import com.bulc.homepage.entity.UserRoleMapping;
import com.bulc.homepage.repository.AuthLoginAttemptRepository;
import com.bulc.homepage.repository.UserProfileRepository;
import com.bulc.homepage.repository.UserRepository;
import com.bulc.homepage.repository.UserRoleMappingRepository;
import com.bulc.homepage.repository.UserRoleRepository;
import com.bulc.homepage.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserRoleMappingRepository userRoleMappingRepository;
    private final AuthLoginAttemptRepository loginAttemptRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        // 이메일 중복 확인
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("이미 가입된 이메일입니다");
        }

        // User 생성
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .emailVerified(false)
                .status("active")
                .signUpChannel("web")
                .locale("ko")
                .timezone("Asia/Seoul")
                .build();

        user = userRepository.save(user);

        // UserProfile 생성
        UserProfile profile = UserProfile.builder()
                .user(user)
                .name(request.getName())
                .phoneNumber(request.getPhoneNumber())
                .build();

        userProfileRepository.save(profile);

        // 기본 역할 (user) 할당
        UserRole userRole = userRoleRepository.findByCode("user")
                .orElseThrow(() -> new RuntimeException("기본 역할이 없습니다"));

        UserRoleMapping roleMapping = UserRoleMapping.builder()
                .user(user)
                .role(userRole)
                .build();

        userRoleMappingRepository.save(roleMapping);

        // JWT 토큰 생성
        String accessToken = jwtTokenProvider.generateAccessToken(user.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiration / 1000)
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .name(request.getName())
                        .status(user.getStatus())
                        .build())
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        log.info("로그인 시도 - 아이디: {}, IP: {}, User-Agent: {}", request.getEmail(), ipAddress, userAgent);

        try {
            // 인증
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            // 사용자 조회
            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

            // 계정 상태 확인
            if (!"active".equals(user.getStatus())) {
                saveLoginAttempt(request.getEmail(), user.getId(), false, "비활성화된 계정", ipAddress, userAgent);
                throw new RuntimeException("비활성화된 계정입니다");
            }

            // 프로필 조회
            UserProfile profile = userProfileRepository.findByUserId(user.getId())
                    .orElse(null);

            // JWT 토큰 생성
            String accessToken = jwtTokenProvider.generateAccessToken(authentication);
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail());

            // 로그인 성공 로그
            saveLoginAttempt(request.getEmail(), user.getId(), true, null, ipAddress, userAgent);
            log.info("로그인 성공 - 아이디: {}, IP: {}", request.getEmail(), ipAddress);

            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(accessTokenExpiration / 1000)
                    .user(AuthResponse.UserInfo.builder()
                            .id(user.getId())
                            .email(user.getEmail())
                            .name(profile != null ? profile.getName() : null)
                            .status(user.getStatus())
                            .build())
                    .build();
        } catch (Exception e) {
            // 로그인 실패 로그
            User user = userRepository.findByEmail(request.getEmail()).orElse(null);
            saveLoginAttempt(request.getEmail(), user != null ? user.getId() : null, false, e.getMessage(), ipAddress, userAgent);
            log.warn("로그인 실패 - 아이디: {}, IP: {}, 사유: {}", request.getEmail(), ipAddress, e.getMessage());
            throw e;
        }
    }

    private void saveLoginAttempt(String email, Long userId, boolean success, String failureReason, String ipAddress, String userAgent) {
        try {
            AuthLoginAttempt attempt = AuthLoginAttempt.builder()
                    .email(email)
                    .userId(userId)
                    .success(success)
                    .failureReason(failureReason)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .build();
            loginAttemptRepository.save(attempt);
        } catch (Exception e) {
            log.error("로그인 시도 로그 저장 실패: {}", e.getMessage());
        }
    }
}
