package com.bulc.homepage.oauth2;

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

        // 이메일이 없는 경우 provider + providerId로 대체 이메일 생성
        if (email == null || email.isEmpty()) {
            email = providerId + "@" + provider.toLowerCase() + ".user";
            log.warn("이메일이 제공되지 않아 대체 이메일 생성: {}", email);
        }

        // 이름이 없는 경우 기본값 설정
        if (name == null || name.isEmpty()) {
            name = provider + " 사용자";
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
                cleanupUserData(user.getEmail());
                // 소셜 계정 삭제 (새로 연결할 것임)
                socialAccountRepository.deleteByUserEmail(user.getEmail());
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
                    cleanupUserData(user.getEmail());
                    // 소셜 계정 삭제
                    socialAccountRepository.deleteByUserEmail(user.getEmail());
                    // 사용자 정보 초기화
                    user.setName(null);
                    user.setPhone(null);
                    user.setPasswordHash(null);
                    user.setDeactivatedAt(null);
                    userRepository.save(user);
                    // 신규 사용자로 처리
                    isNewUser = true;
                } else {
                    // 기존 이메일 사용자에 소셜 계정 연동
                    linkSocialAccount(user, provider, providerId);
                    userEmail = user.getEmail();
                    log.info("기존 사용자에 소셜 계정 연동: {}", userEmail);
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
                .phone(mobile)  // 전화번호 저장
                .rolesCode("002")  // 일반 사용자
                .countryCode("KR")
                .build();
        return userRepository.save(user);
    }

    private void linkSocialAccount(User user, String provider, String providerId) {
        UserSocialAccount socialAccount = UserSocialAccount.builder()
                .userEmail(user.getEmail())
                .provider(provider)
                .providerId(providerId)
                .build();
        socialAccountRepository.save(socialAccount);
    }

    /**
     * 사용자 관련 데이터 정리 (재가입 시)
     */
    private void cleanupUserData(String userEmail) {
        // 활동 로그 삭제
        activityLogRepository.deleteByUserEmail(userEmail);
        // 리프레시 토큰 삭제
        refreshTokenRepository.deleteAllByUserEmail(userEmail);
        log.info("사용자 관련 데이터 정리 완료: {}", userEmail);
    }
}
