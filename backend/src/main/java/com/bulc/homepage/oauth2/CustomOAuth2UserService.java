package com.bulc.homepage.oauth2;

import com.bulc.homepage.config.ValidationConfig;
import com.bulc.homepage.entity.User;
import com.bulc.homepage.entity.UserSocialAccount;
import com.bulc.homepage.repository.ActivityLogRepository;
import com.bulc.homepage.repository.RefreshTokenRepository;
import com.bulc.homepage.repository.UserRepository;
import com.bulc.homepage.repository.UserSocialAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final UserSocialAccountRepository socialAccountRepository;
    private final ActivityLogRepository activityLogRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        String userNameAttributeName = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

        OAuth2UserInfo oAuth2UserInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(
                registrationId, oAuth2User.getAttributes());

        String providerId = oAuth2UserInfo.getId();
        String email = oAuth2UserInfo.getEmail();
        String name = oAuth2UserInfo.getName();
        String mobile = oAuth2UserInfo.getMobile();
        String provider = registrationId.toUpperCase();

        log.info("OAuth2 로그인 시도 - Provider: {}, ProviderId: {}, Email: {}, Name: {}, Mobile: {}",
                provider, providerId, email, name, mobile);

        // 이메일이 없는 경우 가입 불가
        if (email == null || email.isEmpty()) {
            log.error("이메일이 제공되지 않아 가입 불가 - Provider: {}, ProviderId: {}", provider, providerId);
            throw new OAuth2AuthenticationException("이메일 정보가 필요합니다. 이메일 제공에 동의해주세요.");
        }

        // 이름이 없는 경우 null 유지 (동의하지 않으면 비워둠)
        if (name != null && name.isEmpty()) {
            name = null;
        }

        // 소셜 계정으로 이미 가입한 사용자 확인
        Optional<UserSocialAccount> existingSocialAccount =
                socialAccountRepository.findByProviderAndProviderId(provider, providerId);

        boolean isNewUser = false;
        String userEmail = email;

        if (existingSocialAccount.isPresent()) {
            // 기존 소셜 계정 사용자
            User user = existingSocialAccount.get().getUser();

            // 비활성화된 계정이면 초기화 후 신규 가입으로 처리
            if (!user.getIsActive()) {
                log.info("비활성화된 계정 초기화 후 신규 가입 처리: {}", user.getEmail());
                // 관련 데이터 정리
                cleanupUserData(user.getId());
                // 소셜 계정 삭제 (새로 연결할 것임)
                socialAccountRepository.deleteByUserId(user.getId());
                // 사용자 정보 초기화 (신규 가입 형태로)
                user.setName(null);
                user.setPhone(null);
                user.setPasswordHash(null);
                user.setDeactivatedAt(null);
                // 아직 isActive는 false 유지 (비밀번호 설정 후 true로)
                userRepository.save(user);
                // 신규 사용자로 처리
                isNewUser = true;
            } else {
                userEmail = user.getEmail();
                log.info("기존 소셜 계정 사용자 로그인: {}", userEmail);
            }
        } else {
            // 새로운 소셜 로그인 - 이메일로 기존 사용자 확인
            Optional<User> existingUser = userRepository.findByEmail(email);
            if (existingUser.isPresent()) {
                User user = existingUser.get();

                // 비활성화된 계정이면 초기화 후 신규 가입으로 처리
                if (!user.getIsActive()) {
                    log.info("비활성화된 계정 초기화 후 신규 가입 처리: {}", email);
                    // 관련 데이터 정리
                    cleanupUserData(user.getId());
                    // 소셜 계정 삭제
                    socialAccountRepository.deleteByUserId(user.getId());
                    // 사용자 정보 초기화
                    user.setName(null);
                    user.setPhone(null);
                    user.setPasswordHash(null);
                    user.setDeactivatedAt(null);
                    userRepository.save(user);
                    // 신규 사용자로 처리
                    isNewUser = true;
                } else {
                    // 활성 계정이 같은 이메일로 이미 존재 — OAuth 가입 차단
                    // (비밀번호 덮어쓰기 취약점 방지: MDP-523)
                    log.warn("OAuth 가입 거부 - 활성 계정 이미 존재: {}", email);
                    throw new OAuth2AuthenticationException(
                            "이미 가입된 이메일입니다. 이메일/비밀번호로 로그인해주세요.");
                }
            } else {
                // 신규 사용자 - 비밀번호 설정 페이지로 이동 필요
                isNewUser = true;
                log.info("신규 소셜 사용자 - 비밀번호 설정 필요: {}", email);
            }
        }

        return new CustomOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                oAuth2User.getAttributes(),
                userNameAttributeName,
                userEmail,
                provider,
                providerId,
                isNewUser,
                name,
                mobile
        );
    }

    private User createNewUser(String email, String name, String mobile) {
        User user = User.builder()
                .email(email)
                .name(name)
                .phone(sanitizePhone(mobile))
                .rolesCode("002")  // 일반 사용자
                .countryCode("KR")
                .build();
        return userRepository.save(user);
    }

    /**
     * 소셜 제공자가 내려준 mobile 값을 전화번호 컬럼에 넣기 전에 검증한다.
     *
     * <p>이 경로는 사용자 입력 폼을 거치지 않아 {@code @ValidPhone}(SignupRequest·
     * OAuthSignupRequest·UpdateUserRequest)이 적용되지 않는다. 제공자 응답 형식이
     * 바뀌거나 예상과 다른 값이 오면 그대로 저장되므로(실제로 phone 컬럼에 이메일이
     * 저장된 사례가 있었다) 형식에 맞지 않으면 null 로 두고 경고만 남긴다.
     * 전화번호는 선택 항목이라 저장하지 않아도 가입에는 지장이 없다.
     */
    private String sanitizePhone(String mobile) {
        if (mobile == null || mobile.isBlank()) {
            return null;
        }
        String value = mobile.trim();
        if (!value.matches(ValidationConfig.PHONE_PATTERN)
                || value.length() < ValidationConfig.PHONE_MIN_LENGTH
                || value.length() > ValidationConfig.PHONE_MAX_LENGTH) {
            log.warn("소셜 로그인 mobile 값이 전화번호 형식이 아니어서 저장하지 않음 (길이={})", value.length());
            return null;
        }
        return value;
    }

    private void linkSocialAccount(User user, String provider, String providerId) {
        UserSocialAccount socialAccount = UserSocialAccount.builder()
                .userId(user.getId())
                .provider(provider)
                .providerId(providerId)
                .build();
        socialAccountRepository.save(socialAccount);
    }

    /**
     * 사용자 관련 데이터 정리 (재가입 시)
     */
    private void cleanupUserData(UUID userId) {
        // 활동 로그 삭제
        activityLogRepository.deleteByUserId(userId);
        // 리프레시 토큰 삭제
        refreshTokenRepository.deleteAllByUserId(userId);
        log.info("사용자 관련 데이터 정리 완료: userId={}", userId);
    }
}
