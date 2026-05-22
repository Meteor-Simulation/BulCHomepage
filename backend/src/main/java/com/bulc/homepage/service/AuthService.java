package com.bulc.homepage.service;

import com.bulc.homepage.dto.request.LoginRequest;
import com.bulc.homepage.dto.request.OAuthSignupRequest;
import com.bulc.homepage.dto.request.RefreshTokenRequest;
import com.bulc.homepage.dto.request.SignupRequest;
import com.bulc.homepage.dto.response.AuthResponse;
import com.bulc.homepage.entity.ActivityLog;
import com.bulc.homepage.entity.RefreshToken;
import com.bulc.homepage.entity.SignupTicket;
import com.bulc.homepage.entity.User;
import com.bulc.homepage.entity.UserSocialAccount;
import com.bulc.homepage.exception.DeactivatedAccountException;
import com.bulc.homepage.licensing.domain.OwnerType;
import com.bulc.homepage.licensing.domain.UsageCategory;
import com.bulc.homepage.licensing.service.LicenseService;
import com.bulc.homepage.repository.ActivityLogRepository;
import com.bulc.homepage.repository.RefreshTokenRepository;
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

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserSocialAccountRepository socialAccountRepository;
    private final ActivityLogRepository activityLogRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SignupTicketService signupTicketService;
    private final LicenseService licenseService;
    private final LoginAttemptService loginAttemptService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;

    private static final String TRIAL_PLAN_CODE = "BULC-TRIAL-14D";

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Value("${jwt.oauth-refresh-token-expiration:31536000000}")  // 기본값 1년
    private long oauthRefreshTokenExpiration;

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        // 1. 티켓 소비 (비관적 락 + 유효성 검증 + used_at 설정)
        SignupTicket ticket = signupTicketService.consumeTicket(request.getSignupTicket());

        // 2. 이메일은 티켓에서 가져옴 (요청 바디에 email 없음)
        String email = ticket.getEmail();

        log.info("이메일 회원가입 시도 - 이메일: {}", email);

        // 3. 이메일 중복 확인
        User existingUser = userRepository.findByEmail(email).orElse(null);
        User user;

        boolean isNewUser = false;

        if (existingUser != null) {
            // 비활성화된 계정인 경우 재활성화 (라이선스는 기존 것 유지)
            if (!existingUser.getIsActive()) {
                log.info("비활성화된 계정 재활성화 처리: {}", email);
                // 관련 데이터 정리
                activityLogRepository.deleteByUserId(existingUser.getId());
                refreshTokenRepository.deleteAllByUserId(existingUser.getId());
                // 소셜 계정 삭제
                socialAccountRepository.deleteByUserId(existingUser.getId());

                // 기존 사용자 정보 초기화 및 재활성화
                existingUser.setName(null);
                existingUser.setPhone(null);
                existingUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));
                existingUser.setEmailVerified(true);
                existingUser.setEmailVerifiedAt(LocalDateTime.now());
                existingUser.setIsActive(true);
                existingUser.setDeactivatedAt(null);
                existingUser.setCreatedAt(LocalDateTime.now());
                user = userRepository.save(existingUser);

                // 회원가입 로그 저장
                saveActivityLog(user.getId(), "signup", "user", null, "회원가입 완료 (비활성화 계정 재가입)");
            } else {
                log.warn("이메일 회원가입 실패 - 이미 가입된 이메일: {}", email);
                throw new RuntimeException("이미 가입된 이메일입니다");
            }
        } else {
            // 신규 User 생성
            isNewUser = true;
            boolean marketing = Boolean.TRUE.equals(request.getMarketingAgreed());
            user = User.builder()
                    .email(email)
                    .passwordHash(passwordEncoder.encode(request.getPassword()))
                    .emailVerified(true)
                    .emailVerifiedAt(LocalDateTime.now())
                    .rolesCode("002")  // 기본값: 일반 사용자
                    .marketingAgreed(marketing)
                    .marketingAgreedAt(marketing ? LocalDateTime.now() : null)
                    .unsubscribeToken(UUID.randomUUID().toString())
                    .build();

            user = userRepository.save(user);

            // 회원가입 로그 저장
            saveActivityLog(user.getId(), "signup", "user", null, "회원가입 완료");
        }

        // 14일 무료 체험 라이선스 발급 (신규 가입만, 재활성화 계정은 기존 라이선스 유지)
        if (isNewUser) {
            issueTrialLicense(user.getId());
        }

        log.info("이메일 회원가입 완료 - 이메일: {}, 신규: {}, userId: {}", email, isNewUser, user.getId());

        // JWT 토큰 생성 (userId 기반)
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getEmail());

        // [RTR] Refresh Token을 DB에 저장
        saveOrUpdateRefreshToken(user.getId(), refreshToken, "signup");

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiration / 1000)
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId().toString())
                        .email(user.getEmail())
                        .name(user.getEmail())  // name 필드가 없으므로 email 사용
                        .rolesCode(user.getRolesCode())
                        .language(user.getLanguageCode())
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
            // 인증 (비밀번호 확인) - userId를 username으로 사용
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getId().toString(), password)
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

        // 로그인 잠금 상태 확인
        loginAttemptService.checkLocked(request.getEmail());

        try {
            // 공통 인증 로직 호출
            User user = authenticateUser(request.getEmail(), request.getPassword());

            // JWT 토큰 생성 (userId 기반)
            String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getEmail());

            // [RTR] Refresh Token을 DB에 저장 (기존 토큰 교체)
            saveOrUpdateRefreshToken(user.getId(), refreshToken, userAgent);

            // 로그인 성공 → 실패 카운트 초기화
            loginAttemptService.resetAttempts(request.getEmail());

            // 로그인 성공 로그
            saveActivityLog(user.getId(), "login", "user", null, "로그인 성공 - IP: " + ipAddress);
            log.info("로그인 성공 - 이메일: {}, IP: {}", request.getEmail(), ipAddress);

            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(accessTokenExpiration / 1000)
                    .user(AuthResponse.UserInfo.builder()
                            .id(user.getId().toString())
                            .email(user.getEmail())
                            .name(user.getEmail())
                            .rolesCode(user.getRolesCode())
                            .language(user.getLanguageCode())
                            .build())
                    .build();
        } catch (RuntimeException e) {
            // 로그인 실패 → 실패 횟수 기록
            loginAttemptService.recordFailure(request.getEmail());

            // 인증 실패 로그 - 사용자 조회 시도
            User failedUser = userRepository.findByEmail(request.getEmail()).orElse(null);
            UUID userId = failedUser != null ? failedUser.getId() : null;
            saveActivityLog(userId, "login_failed", "user", null,
                    e.getMessage() + " - IP: " + ipAddress);
            throw e;
        }
    }

    /**
     * 회원가입 시 14일 무료 체험 라이선스를 발급합니다.
     * 라이선스 발급 실패가 회원가입을 막지 않도록 예외를 catch합니다.
     */
    private void issueTrialLicense(UUID userId) {
        try {
            licenseService.issueLicenseWithPlanCode(
                    OwnerType.USER, userId, TRIAL_PLAN_CODE, null, UsageCategory.INTERNAL_EVAL);
            log.info("Trial 라이선스 발급 완료 - userId: {}", userId);
        } catch (Exception e) {
            log.error("Trial 라이선스 발급 실패 - userId: {}, error: {}", userId, e.getMessage());
        }
    }

    private void saveActivityLog(UUID userId, String action, String targetType, Long targetId, String description) {
        try {
            ActivityLog activityLog = ActivityLog.builder()
                    .userId(userId)
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
     * [RTR] Refresh Token을 DB에 저장 또는 업데이트.
     * 기존 토큰이 있으면 새 토큰으로 교체, 없으면 새로 생성.
     */
    private void saveOrUpdateRefreshToken(UUID userId, String refreshToken, String deviceInfo) {
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000);

        RefreshToken rtEntity = refreshTokenRepository.findByUserId(userId)
                .orElse(RefreshToken.builder()
                        .userId(userId)
                        .build());

        rtEntity.setToken(refreshToken);
        rtEntity.setExpiresAt(expiresAt);
        rtEntity.setDeviceInfo(deviceInfo);
        rtEntity.setLastUsedAt(LocalDateTime.now());

        refreshTokenRepository.save(rtEntity);
        log.debug("Refresh Token 저장 완료 - userId: {}", userId);
    }

    /**
     * [RTR] Refresh Token 무효화 (로그아웃 시 호출).
     * DB에서 해당 사용자의 Refresh Token 삭제.
     */
    @Transactional
    public void invalidateRefreshToken(UUID userId) {
        refreshTokenRepository.deleteAllByUserId(userId);
        log.info("Refresh Token 무효화 완료 - userId: {}", userId);
    }

    /**
     * Access Token 생성 (외부 호출용)
     */
    public String generateAccessToken(UUID userId, String email) {
        return jwtTokenProvider.generateAccessToken(userId, email);
    }

    /**
     * Refresh Token 생성 (외부 호출용)
     */
    public String generateRefreshToken(UUID userId, String email) {
        return jwtTokenProvider.generateRefreshToken(userId, email);
    }

    /**
     * 토큰에서 userId 추출 (외부 호출용)
     */
    public UUID getUserIdFromToken(String token) {
        return jwtTokenProvider.getUserIdFromToken(token);
    }

    /**
     * 토큰에서 이메일 추출 (외부 호출용)
     */
    public String getEmailFromToken(String token) {
        return jwtTokenProvider.getEmailFromToken(token);
    }

    /**
     * Refresh Token을 사용하여 새로운 Access Token 발급 (RTR 적용).
     *
     * RTR (Refresh Token Rotation):
     * 1. 요청된 RT가 DB에 저장된 RT와 일치하는지 확인
     * 2. 일치하면 새 AT + 새 RT 발급, DB의 RT 교체
     * 3. 불일치하면 토큰 탈취 의심 → 해당 사용자의 모든 RT 무효화 (강제 로그아웃)
     */
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        // Refresh Token JWT 유효성 검사
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            log.warn("토큰 갱신 실패 - 유효하지 않은 Refresh Token");
            throw new RuntimeException("유효하지 않은 Refresh Token입니다");
        }

        // Refresh Token에서 userId 추출
        UUID userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        log.info("토큰 갱신 시도 - userId: {}", userId);

        // 사용자 조회
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("토큰 갱신 실패 - 사용자 없음: userId={}", userId);
            throw new RuntimeException("사용자를 찾을 수 없습니다");
        }

        // 비활성화된 계정인지 확인
        if (!user.getIsActive()) {
            log.warn("토큰 갱신 실패 - 비활성화 계정: userId={}, email={}", userId, user.getEmail());
            throw new RuntimeException("비활성화된 계정입니다. 고객센터에 문의해주세요.");
        }

        // [RTR 핵심] DB에 저장된 Refresh Token과 비교
        RefreshToken storedToken = refreshTokenRepository.findByUserId(userId).orElse(null);

        if (storedToken == null) {
            // DB에 RT가 없음 - 로그아웃된 상태이거나 비정상 접근
            log.warn("RTR 실패 - DB에 저장된 토큰 없음: userId={}", userId);
            throw new RuntimeException("세션이 만료되었습니다. 다시 로그인해주세요.");
        }

        if (!storedToken.getToken().equals(refreshToken)) {
            // [Token Theft Detection] DB의 RT와 요청된 RT가 불일치
            // 탈취된 RT 재사용 시도로 간주 → 모든 세션 무효화
            log.warn("RTR 토큰 탈취 의심 - userId: {}, 모든 세션 강제 종료", userId);
            refreshTokenRepository.deleteAllByUserId(userId);
            saveActivityLog(userId, "token_theft_detected", "security", null,
                    "Refresh Token 재사용 감지, 모든 세션 강제 종료");
            throw new RuntimeException("보안 문제가 감지되었습니다. 다시 로그인해주세요.");
        }

        // 토큰 만료 확인
        if (storedToken.isExpired()) {
            log.info("RTR 실패 - 만료된 토큰: userId={}", userId);
            refreshTokenRepository.deleteAllByUserId(userId);
            throw new RuntimeException("세션이 만료되었습니다. 다시 로그인해주세요.");
        }

        // 새로운 Access Token, Refresh Token 생성
        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getEmail());

        // [RTR] DB의 Refresh Token 교체 (Rotation)
        LocalDateTime newExpiresAt = LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000);
        storedToken.rotateToken(newRefreshToken, newExpiresAt);
        refreshTokenRepository.save(storedToken);

        log.info("토큰 갱신 성공 (RTR) - userId: {}", userId);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiration / 1000)
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId().toString())
                        .email(user.getEmail())
                        .name(user.getEmail())
                        .rolesCode(user.getRolesCode())
                        .language(user.getLanguageCode())
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
            log.warn("OAuth 회원가입 실패 - 유효하지 않은 임시 토큰");
            throw new RuntimeException("유효하지 않은 토큰입니다. 다시 시도해주세요.");
        }

        // 토큰에서 정보 추출
        Claims claims = jwtTokenProvider.parseTempToken(request.getToken());
        String email = claims.getSubject();
        String provider = claims.get("provider", String.class);
        String providerId = claims.get("providerId", String.class);

        log.info("OAuth 회원가입 시도 - 이메일: {}, Provider: {}", email, provider);

        // 이미 가입된 이메일인지 확인
        User existingUser = userRepository.findByEmail(email).orElse(null);
        User user;

        boolean isNewUser = false;

        if (existingUser != null) {
            // 비활성화된 계정이면 재활성화 (라이선스는 기존 것 유지)
            if (!existingUser.getIsActive()) {
                log.info("비활성화된 계정 재활성화 후 OAuth 재가입 처리: {}", email);
                // 관련 데이터 정리
                activityLogRepository.deleteByUserId(existingUser.getId());
                refreshTokenRepository.deleteAllByUserId(existingUser.getId());
                socialAccountRepository.deleteByUserId(existingUser.getId());

                // 기존 사용자 정보 초기화 및 재활성화
                existingUser.setName(request.getName());
                existingUser.setPhone(request.getPhone());
                existingUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));
                existingUser.setIsActive(true);
                existingUser.setDeactivatedAt(null);
                existingUser.setCreatedAt(LocalDateTime.now());
                user = userRepository.save(existingUser);
            } else {
                // 기존 활성 사용자 - 소셜 계정 연동 및 정보 갱신
                log.info("기존 활성 사용자에 소셜 계정 연동: {}", email);
                existingUser.setName(request.getName() != null ? request.getName() : existingUser.getName());
                existingUser.setPhone(request.getPhone() != null ? request.getPhone() : existingUser.getPhone());
                existingUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));
                user = userRepository.save(existingUser);
            }
        } else {
            // 신규 사용자 생성
            isNewUser = true;
            user = User.builder()
                    .email(email)
                    .passwordHash(passwordEncoder.encode(request.getPassword()))
                    .name(request.getName())
                    .phone(request.getPhone())
                    .rolesCode("002")  // 일반 사용자
                    .countryCode("KR")
                    .build();
            user = userRepository.save(user);
        }

        // 소셜 계정 연동
        UserSocialAccount socialAccount = UserSocialAccount.builder()
                .userId(user.getId())
                .provider(provider)
                .providerId(providerId)
                .build();
        socialAccountRepository.save(socialAccount);

        // 회원가입 로그 저장
        saveActivityLog(user.getId(), "oauth_signup", "user", null,
                "OAuth 회원가입 완료 - Provider: " + provider);

        String signupType = isNewUser ? "신규" : (existingUser != null && !existingUser.getIsActive() ? "재활성화" : "소셜연동");
        log.info("OAuth 회원가입 완료 - 이메일: {}, Provider: {}, 유형: {}, userId: {}", email, provider, signupType, user.getId());

        // 14일 무료 체험 라이선스 발급 (신규 가입만, 재활성화 계정은 기존 라이선스 유지)
        if (isNewUser) {
            issueTrialLicense(user.getId());
        }

        // JWT 토큰 생성 (userId 기반)
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getEmail());

        // [RTR] Refresh Token을 DB에 저장
        saveOrUpdateRefreshToken(user.getId(), refreshToken, "oauth_signup:" + provider);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiration / 1000)
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId().toString())
                        .email(user.getEmail())
                        .name(user.getName())
                        .rolesCode(user.getRolesCode())
                        .language(user.getLanguageCode())
                        .build())
                .build();
    }

    // ==========================================
    // OAuth 2.0 전용 메서드 (RTR 적용 + 장기 만료)
    // ==========================================

    /**
     * OAuth 클라이언트용 토큰 발급 (RTR 적용).
     * Authorization Code 교환 후 호출됩니다.
     * Refresh Token은 1년 만료로 설정됩니다.
     *
     * @param email 사용자 이메일
     * @param clientId OAuth 클라이언트 ID (deviceInfo로 저장)
     * @return AuthResponse (accessToken, refreshToken 포함)
     */
    @Transactional
    public AuthResponse issueTokensForOAuth(String email, String clientId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

        if (!user.getIsActive()) {
            throw new RuntimeException("비활성화된 계정입니다");
        }

        // JWT 토큰 생성 (userId 기반)
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getEmail());

        // [RTR] Refresh Token을 DB에 저장 (OAuth용 장기 만료)
        saveOrUpdateRefreshTokenForOAuth(user.getId(), refreshToken, "oauth:" + clientId);

        saveActivityLog(user.getId(), "oauth_token_issued", "oauth", null,
                "OAuth 토큰 발급 - client_id: " + clientId);
        log.info("OAuth 토큰 발급 성공 - 이메일: {}, client_id: {}", email, clientId);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiration / 1000)
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId().toString())
                        .email(user.getEmail())
                        .name(user.getName() != null ? user.getName() : user.getEmail())
                        .rolesCode(user.getRolesCode())
                        .language(user.getLanguageCode())
                        .build())
                .build();
    }

    /**
     * OAuth 클라이언트용 토큰 갱신 (RTR 적용).
     * Refresh Token Rotation + Token Theft Detection 적용.
     * 새 Refresh Token도 1년 만료로 설정됩니다.
     *
     * @param refreshToken 기존 Refresh Token
     * @return AuthResponse (새 accessToken, 새 refreshToken 포함)
     */
    @Transactional
    public AuthResponse refreshTokenForOAuth(String refreshToken) {
        // Refresh Token JWT 유효성 검사
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new RuntimeException("유효하지 않은 Refresh Token입니다");
        }

        UUID userId = jwtTokenProvider.getUserIdFromToken(refreshToken);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

        if (!user.getIsActive()) {
            throw new RuntimeException("비활성화된 계정입니다");
        }

        // [RTR 핵심] DB에 저장된 Refresh Token과 비교
        RefreshToken storedToken = refreshTokenRepository.findByUserId(userId).orElse(null);

        if (storedToken == null) {
            log.warn("OAuth RTR 실패 - DB에 저장된 토큰 없음: userId={}", userId);
            throw new RuntimeException("세션이 만료되었습니다. 다시 로그인해주세요.");
        }

        if (!storedToken.getToken().equals(refreshToken)) {
            // [Token Theft Detection] 탈취된 RT 재사용 시도
            log.warn("OAuth RTR 토큰 탈취 의심 - userId: {}, 모든 세션 강제 종료", userId);
            refreshTokenRepository.deleteAllByUserId(userId);
            saveActivityLog(userId, "oauth_token_theft_detected", "security", null,
                    "OAuth Refresh Token 재사용 감지, 모든 세션 강제 종료");
            throw new RuntimeException("보안 문제가 감지되었습니다. 다시 로그인해주세요.");
        }

        if (storedToken.isExpired()) {
            log.info("OAuth RTR 실패 - 만료된 토큰: userId={}", userId);
            refreshTokenRepository.deleteAllByUserId(userId);
            throw new RuntimeException("세션이 만료되었습니다. 다시 로그인해주세요.");
        }

        // 새로운 Access Token, Refresh Token 생성
        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getEmail());

        // [RTR] DB의 Refresh Token 교체 (OAuth용 장기 만료)
        LocalDateTime newExpiresAt = LocalDateTime.now().plusSeconds(oauthRefreshTokenExpiration / 1000);
        storedToken.rotateToken(newRefreshToken, newExpiresAt);
        refreshTokenRepository.save(storedToken);

        log.info("OAuth 토큰 갱신 성공 (RTR) - userId: {}", userId);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiration / 1000)
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId().toString())
                        .email(user.getEmail())
                        .name(user.getName() != null ? user.getName() : user.getEmail())
                        .rolesCode(user.getRolesCode())
                        .language(user.getLanguageCode())
                        .build())
                .build();
    }

    /**
     * [RTR] OAuth용 Refresh Token을 DB에 저장 (1년 만료).
     */
    private void saveOrUpdateRefreshTokenForOAuth(UUID userId, String refreshToken, String deviceInfo) {
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(oauthRefreshTokenExpiration / 1000);

        RefreshToken rtEntity = refreshTokenRepository.findByUserId(userId)
                .orElse(RefreshToken.builder()
                        .userId(userId)
                        .build());

        rtEntity.setToken(refreshToken);
        rtEntity.setExpiresAt(expiresAt);
        rtEntity.setDeviceInfo(deviceInfo);
        rtEntity.setLastUsedAt(LocalDateTime.now());

        refreshTokenRepository.save(rtEntity);
        log.debug("OAuth Refresh Token 저장 완료 (1년 만료) - userId: {}", userId);
    }
}
