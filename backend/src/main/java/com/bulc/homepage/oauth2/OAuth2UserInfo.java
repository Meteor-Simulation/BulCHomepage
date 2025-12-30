package com.bulc.homepage.oauth2;

import java.util.Map;

public abstract class OAuth2UserInfo {
    protected Map<String, Object> attributes;

    public OAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public abstract String getId();
    public abstract String getName();
    public abstract String getEmail();

    // 전화번호 (기본값 null, 하위 클래스에서 필요시 오버라이드)
    public String getMobile() {
        return null;
    }
}
