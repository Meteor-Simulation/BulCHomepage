package com.bulc.homepage.service;

import com.bulc.homepage.dto.request.LoginRequest;
import com.bulc.homepage.dto.request.OAuthSignupRequest;
import com.bulc.homepage.dto.request.RefreshTokenRequest;
import com.bulc.homepage.dto.request.SignupRequest;
import com.bulc.homepage.dto.response.AuthResponse;
import com.bulc.homepage.entity.ActivityLog;
import com.bulc.homepage.entity.User;
import com.bulc.homepage.entity.UserSocialAccount;
import com.bulc.homepage.exception.DeactivatedAccountException;
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
        User existingUser = userRepository.findByEmail(request.getEmail()).orElse(null);
        User user;

        if (existingUser != null) {
            // 비활성화된 계정인 경우 재활성화
            if (!existingUser.getIsActive()) {
                existingUser.setIsActive(true);
                existingUser.setDeactivatedAt(null);
                existingUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));
                // 재가입 시 개인정보 삭제
                existingUser.setName(null);
                existingUser.setPhone(null);
                user = userRepository.save(existingUser);

                // 계정 재활성화 로그 저장
                saveActivityLog(user.getEmail(), "reactivate", "user", null, "계정 재활성화 완료");
            } else {
                throw new RuntimeException("이미 가입된 이메일입니다");
            }
        } else {
            // 신규 User 생성 (email이 PK)
            user = User.builder()
                    .email(request.getEmail())
                    .passwordHash(passwordEncoder.encode(request.getPassword()))
                    .rolesCode("002")  // 기본값: 일반 사용자
                    .build();

            user = userRepository.save(user);

            // 회원가입 로그 저장
            saveActivityLog(user.getEmail(), "signup", "user", null, "회원가입 완료");
        }

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

    /**
     * 순수 인증만 수행 (ID/PW 검증).
     * OAuth 2.0 및 기존 로그인에서 공통으로 사용.
     *
     * @param email 사용자 이메일
     * @param password 비밀번호
     * @return 인증된 User 객체
     * @throws RuntimeException 인증 실패 시
     */
    private static final String AUTH_FAILURE_MESSAGE = "이메일 또는 비밀번호가 올바르지 않습니다.";

    public User authenticateUser(String email, String password) {
        // 사용자 존재 여부 확인
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            log.warn("인증 실패 - 존재하지 않는 이메일: {}", email);
            // 보안: 이메일 존재 여부를 노출하지 않음
            throw new RuntimeException(AUTH_FAILURE_MESSAGE);
        }

        // 비활성화된 계정인지 확인 (존재하지 않는 것처럼 처리)
        if (!user.getIsActive()) {
            log.warn("인증 실패 - 비활성화된 계정: {}", email);
            throw new RuntimeException(AUTH_FAILURE_MESSAGE);
        }

        try {
            // 인증 (비밀번호 확인)
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );
            return user;
        } catch (org.springframework.security.authentication.BadCredentialsException e) {
            log.warn("인증 실패 - 비밀번호 오류, 이메일: {}", email);
            // 보안: 비밀번호 오류와 이메일 미존재를 구분하지 않음
            throw new RuntimeException(AUTH_FAILURE_MESSAGE);
        }
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        log.info("로그인 시도 - 이메일: {}, IP: {}, User-Agent: {}", request.getEmail(), ipAddress, userAgent);

        try {
            // 공통 인증 로직 호출
            User user = authenticateUser(request.getEmail(), request.getPassword());

            // JWT 토큰 생성
            String accessToken = jwtTokenProvider.generateAccessToken(user.getEmail());
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
        } catch (RuntimeException e) {
            // 인증 실패 로그
            saveActivityLog(request.getEmail(), "login_failed", "user", null,
                    e.getMessage() + " - IP: " + ipAddress);
            throw e;
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
     * Access Token 생성 (외부 호출용)
     */
    public String generateAccessToken(String email) {
        return jwtTokenProvider.generateAccessToken(email);
    }

    /**
     * Refresh Token 생성 (외부 호출용)
     */
    public String generateRefreshToken(String email) {
        return jwtTokenProvider.generateRefreshToken(email);
    }

    /**
     * 토큰에서 이메일 추출 (외부 호출용)
     */
    public String getEmailFromToken(String token) {
        return jwtTokenProvider.getEmailFromToken(token);
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

        // 비활성화된 계정인지 확인
        if (!user.getIsActive()) {
            throw new RuntimeException("비활성화된 계정입니다. 고객센터에 문의해주세요.");
        }

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
