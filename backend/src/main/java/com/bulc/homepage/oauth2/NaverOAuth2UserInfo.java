package com.bulc.homepage.oauth2;

import lombok.extern.slf4j.Slf4j;
import java.util.Map;

@Slf4j
public class NaverOAuth2UserInfo extends OAuth2UserInfo {

    public NaverOAuth2UserInfo(Map<String, Object> attributes) {
        super(attributes);
        // 네이버 응답 전체 로깅 (디버깅용)
        Map<String, Object> response = (Map<String, Object>) attributes.get("response");
        if (response != null) {
            log.info("네이버 OAuth 응답 데이터: {}", response);
        }
    }

    @Override
    public String getId() {
        Map<String, Object> response = (Map<String, Object>) attributes.get("response");
        if (response == null) {
            return null;
        }
        return (String) response.get("id");
    }

    @Override
    public String getName() {
        Map<String, Object> response = (Map<String, Object>) attributes.get("response");
        if (response == null) {
            return null;
        }
        return (String) response.get("name");
    }

    @Override
    public String getEmail() {
        Map<String, Object> response = (Map<String, Object>) attributes.get("response");
        if (response == null) {
            return null;
        }
        // 연락처 이메일 (email 필드)
        String email = (String) response.get("email");
        log.info("네이버 이메일 추출: {}", email);
        return email;
    }

    @Override
    public String getMobile() {
        Map<String, Object> response = (Map<String, Object>) attributes.get("response");
        if (response == null) {
            return null;
        }
        return (String) response.get("mobile");
    }
}
