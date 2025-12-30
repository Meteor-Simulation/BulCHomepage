package com.bulc.homepage.service;

import com.bulc.homepage.dto.request.LoginRequest;
import com.bulc.homepage.dto.request.OAuthSignupRequest;
import com.bulc.homepage.dto.request.RefreshTokenRequest;
import com.bulc.homepage.dto.request.SignupRequest;
import com.bulc.homepage.dto.response.AuthResponse;
import com.bulc.homepage.entity.ActivityLog;
import com.bulc.homepage.entity.User;
import com.bulc.homepage.entity.UserSocialAccount;
import com.bulc.homepage.repository.ActivityLogRepository;
import com.bulc.homepage.repository.UserRepository;
import com.bulc.homepage.repository.UserSocialAccountRepository;
import com.bulc.homepage.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
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
    private final UserSocialAccountRepository socialAccountRepository;
    private final ActivityLogRepository activityLogRepository;
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

        // User 생성 (email이 PK)
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .rolesCode("002")  // 기본값: 일반 사용자
                .build();

        user = userRepository.save(user);

        // 회원가입 로그 저장
        saveActivityLog(user.getEmail(), "signup", "user", null, "회원가입 완료");

        // JWT 토큰 생성
        String accessToken = jwtTokenProvider.generateAccessToken(user.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiration / 1000)
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getEmail())
                        .email(user.getEmail())
                        .name(user.getEmail())  // name 필드가 없으므로 email 사용
                        .rolesCode(user.getRolesCode())
                        .build())
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        log.info("로그인 시도 - 이메일: {}, IP: {}, User-Agent: {}", request.getEmail(), ipAddress, userAgent);

        // 먼저 사용자 존재 여부 확인
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (user == null) {
            log.warn("로그인 실패 - 존재하지 않는 이메일: {}, IP: {}", request.getEmail(), ipAddress);
            throw new RuntimeException("존재하지 않는 이메일입니다.");
        }

        try {
            // 인증 (비밀번호 확인)
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            // JWT 토큰 생성
            String accessToken = jwtTokenProvider.generateAccessToken(authentication);
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail());

            // 로그인 성공 로그
            saveActivityLog(user.getEmail(), "login", "user", null, "로그인 성공 - IP: " + ipAddress);
            log.info("로그인 성공 - 이메일: {}, IP: {}", request.getEmail(), ipAddress);

            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(accessTokenExpiration / 1000)
                    .user(AuthResponse.UserInfo.builder()
                            .id(user.getEmail())
                            .email(user.getEmail())
                            .name(user.getEmail())
                            .rolesCode(user.getRolesCode())
                            .build())
                    .build();
        } catch (org.springframework.security.authentication.BadCredentialsException e) {
            // 비밀번호 오류
            saveActivityLog(user.getEmail(), "login_failed", "user", null, "비밀번호 오류 - IP: " + ipAddress);
            log.warn("로그인 실패 - 비밀번호 오류, 이메일: {}, IP: {}", request.getEmail(), ipAddress);
            throw new RuntimeException("비밀번호가 올바르지 않습니다.");
        } catch (RuntimeException e) {
            // 이미 처리된 RuntimeException은 그대로 전파
            throw e;
        } catch (Exception e) {
            // 기타 예외
            saveActivityLog(user.getEmail(), "login_failed", "user", null, "로그인 실패: " + e.getMessage() + " - IP: " + ipAddress);
            log.warn("로그인 실패 - 이메일: {}, IP: {}, 사유: {}", request.getEmail(), ipAddress, e.getMessage());
            throw new RuntimeException("로그인 중 오류가 발생했습니다.");
        }
    }

    private void saveActivityLog(String userEmail, String action, String targetType, Long targetId, String description) {
        try {
            ActivityLog activityLog = ActivityLog.builder()
                    .userEmail(userEmail)
                    .action(action)
                    .targetType(targetType)
                    .targetId(targetId)
                    .description(description)
                    .build();
            activityLogRepository.save(activityLog);
        } catch (Exception e) {
            log.error("활동 로그 저장 실패: {}", e.getMessage());
        }
    }

    /**
     * Refresh Token을 사용하여 새로운 Access Token 발급
     */
    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        // Refresh Token 유효성 검사
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new RuntimeException("유효하지 않은 Refresh Token입니다");
        }

        // Refresh Token에서 이메일 추출
        String email = jwtTokenProvider.getEmailFromToken(refreshToken);

        // 사용자 조회
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

        // 새로운 Access Token 생성
        String newAccessToken = jwtTokenProvider.generateAccessToken(email);
        // 새로운 Refresh Token도 발급 (선택적)
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(email);

        log.info("토큰 갱신 성공 - 이메일: {}", email);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiration / 1000)
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getEmail())
                        .email(user.getEmail())
                        .name(user.getEmail())
                        .rolesCode(user.getRolesCode())
                        .build())
                .build();
    }

    /**
     * OAuth 회원가입 완료 (비밀번호 설정)
     */
    @Transactional
    public AuthResponse oauthSignup(OAuthSignupRequest request) {
        // 임시 토큰 검증
        if (!jwtTokenProvider.validateTempToken(request.getToken())) {
            throw new RuntimeException("유효하지 않은 토큰입니다. 다시 시도해주세요.");
        }

        // 토큰에서 정보 추출
        Claims claims = jwtTokenProvider.parseTempToken(request.getToken());
        String email = claims.getSubject();
        String provider = claims.get("provider", String.class);
        String providerId = claims.get("providerId", String.class);

        // 이미 가입된 이메일인지 확인
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("이미 가입된 이메일입니다.");
        }

        // 사용자 생성
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .phone(request.getPhone())
                .rolesCode("002")  // 일반 사용자
                .countryCode("KR")
                .build();
        user = userRepository.save(user);

        // 소셜 계정 연동
        UserSocialAccount socialAccount = UserSocialAccount.builder()
                .userEmail(user.getEmail())
                .provider(provider)
                .providerId(providerId)
                .build();
        socialAccountRepository.save(socialAccount);

        // 회원가입 로그 저장
        saveActivityLog(user.getEmail(), "oauth_signup", "user", null,
                "OAuth 회원가입 완료 - Provider: " + provider);

        log.info("OAuth 회원가입 완료 - 이메일: {}, Provider: {}", email, provider);

        // JWT 토큰 생성
        String accessToken = jwtTokenProvider.generateAccessToken(user.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiration / 1000)
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getEmail())
                        .email(user.getEmail())
                        .name(user.getName())
                        .rolesCode(user.getRolesCode())
                        .build())
                .build();
    }
}
