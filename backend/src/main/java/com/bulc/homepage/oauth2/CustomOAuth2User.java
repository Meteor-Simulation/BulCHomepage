package com.bulc.homepage.oauth2;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.Collection;
import java.util.Map;

@Getter
public class CustomOAuth2User extends DefaultOAuth2User {

    private final String email;
    private final String provider;
    private final String providerId;
    private final boolean newUser;
    private final String userName;
    private final String mobile;

    public CustomOAuth2User(Collection<? extends GrantedAuthority> authorities,
                            Map<String, Object> attributes,
                            String nameAttributeKey,
                            String email,
                            String provider,
                            String providerId,
                            boolean newUser,
                            String userName,
                            String mobile) {
        super(authorities, attributes, nameAttributeKey);
        this.email = email;
        this.provider = provider;
        this.providerId = providerId;
        this.newUser = newUser;
        this.userName = userName;
        this.mobile = mobile;
    }
}
