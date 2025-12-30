package com.bulc.homepage.oauth2;

import java.util.Map;

public class OAuth2UserInfoFactory {

    public static OAuth2UserInfo getOAuth2UserInfo(String registrationId, Map<String, Object> attributes) {
        if (registrationId.equalsIgnoreCase("naver")) {
            return new NaverOAuth2UserInfo(attributes);
        } else if (registrationId.equalsIgnoreCase("kakao")) {
            // TODO: KakaoOAuth2UserInfo 구현 후 추가
            throw new IllegalArgumentException("카카오 로그인은 아직 지원하지 않습니다.");
        } else if (registrationId.equalsIgnoreCase("google")) {
            // TODO: GoogleOAuth2UserInfo 구현 후 추가
            throw new IllegalArgumentException("구글 로그인은 아직 지원하지 않습니다.");
        } else {
            throw new IllegalArgumentException("지원하지 않는 로그인 제공자입니다: " + registrationId);
        }
    }
}
