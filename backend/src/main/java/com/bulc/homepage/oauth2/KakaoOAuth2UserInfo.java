package com.bulc.homepage.oauth2;

import lombok.extern.slf4j.Slf4j;
import java.util.Map;

@Slf4j
public class KakaoOAuth2UserInfo extends OAuth2UserInfo {

    public KakaoOAuth2UserInfo(Map<String, Object> attributes) {
        super(attributes);
        log.info("카카오 OAuth 응답 데이터: {}", attributes);
    }

    @Override
    public String getId() {
        // 카카오는 id가 Long 타입으로 반환됨
        Object id = attributes.get("id");
        if (id == null) {
            return null;
        }
        return String.valueOf(id);
    }

    @Override
    public String getName() {
        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
        if (kakaoAccount != null) {
            // 실명(name) 먼저 확인
            String name = (String) kakaoAccount.get("name");
            if (name != null) {
                log.info("카카오 이름 추출: {}", name);
                return name;
            }

            // 실명이 없으면 닉네임에서 찾기
            Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
            if (profile != null) {
                String nickname = (String) profile.get("nickname");
                if (nickname != null) {
                    log.info("카카오 닉네임 추출: {}", nickname);
                    return nickname;
                }
            }
        }

        // 마지막으로 properties에서 닉네임 확인
        Map<String, Object> properties = (Map<String, Object>) attributes.get("properties");
        if (properties != null && properties.get("nickname") != null) {
            return (String) properties.get("nickname");
        }

        return null;
    }

    @Override
    public String getEmail() {
        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
        if (kakaoAccount == null) {
            return null;
        }

        // 이메일 제공 동의 여부 확인
        Boolean hasEmail = (Boolean) kakaoAccount.get("has_email");
        Boolean isEmailValid = (Boolean) kakaoAccount.get("is_email_valid");
        Boolean isEmailVerified = (Boolean) kakaoAccount.get("is_email_verified");

        if (Boolean.TRUE.equals(hasEmail) && Boolean.TRUE.equals(isEmailValid)) {
            String email = (String) kakaoAccount.get("email");
            log.info("카카오 이메일 추출: {} (verified: {})", email, isEmailVerified);
            return email;
        }

        return null;
    }

    @Override
    public String getMobile() {
        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
        if (kakaoAccount == null) {
            return null;
        }

        // 전화번호 제공 동의 여부 확인
        Boolean hasPhoneNumber = (Boolean) kakaoAccount.get("has_phone_number");
        if (Boolean.TRUE.equals(hasPhoneNumber)) {
            String phoneNumber = (String) kakaoAccount.get("phone_number");
            if (phoneNumber != null) {
                // 카카오 전화번호 형식: +82 10-1234-5678 -> 010-1234-5678로 변환
                phoneNumber = phoneNumber.replace("+82 ", "0").replace(" ", "");
                log.info("카카오 전화번호 추출: {}", phoneNumber);
                return phoneNumber;
            }
        }

        return null;
    }
}
