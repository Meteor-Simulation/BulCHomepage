package com.bulc.homepage.oauth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * OAuth 클라이언트 설정.
 *
 * <p>client_id별 허용된 redirect_uri 목록을 관리합니다.
 * 등록되지 않은 redirect_uri는 거부됩니다.</p>
 *
 * <p>포트 와일드카드 지원:</p>
 * <ul>
 *   <li>{@code http://localhost:STAR/path} - localhost의 모든 포트 허용</li>
 *   <li>{@code http://127.0.0.1:STAR/path} - 127.0.0.1의 모든 포트 허용</li>
 * </ul>
 * <p>설정에서 STAR 대신 * 문자를 사용합니다.</p>
 */
@Component
@ConfigurationProperties(prefix = "bulc.oauth")
@Getter
@Setter
public class OAuthClientProperties {

    /**
     * 등록된 OAuth 클라이언트 목록.
     * key: client_id, value: 클라이언트 설정
     */
    private Map<String, ClientConfig> clients = new HashMap<>();

    /**
     * 개별 클라이언트 설정.
     */
    @Getter
    @Setter
    public static class ClientConfig {
        /**
         * 허용된 redirect_uri 목록.
         * 여기에 등록된 URI만 OAuth 인증에 사용할 수 있습니다.
         */
        private List<String> allowedRedirectUris = List.of();

        /**
         * 클라이언트 표시 이름 (선택).
         */
        private String displayName;
    }

    /**
     * client_id가 등록되어 있는지 확인.
     */
    public boolean isRegisteredClient(String clientId) {
        return clients.containsKey(clientId);
    }

    /**
     * redirect_uri가 해당 client_id에 대해 허용되어 있는지 확인.
     * 포트 와일드카드(*) 패턴을 지원합니다.
     *
     * @param clientId 클라이언트 ID
     * @param redirectUri 검증할 redirect URI
     * @return 허용된 URI이면 true
     */
    public boolean isAllowedRedirectUri(String clientId, String redirectUri) {
        if (clientId == null || redirectUri == null) {
            return false;
        }

        ClientConfig config = clients.get(clientId);
        if (config == null) {
            return false;
        }

        for (String allowedUri : config.getAllowedRedirectUris()) {
            if (matchesRedirectUri(allowedUri, redirectUri)) {
                return true;
            }
        }
        return false;
    }

    /**
     * redirect_uri가 허용 패턴과 일치하는지 확인.
     * 포트 와일드카드(*)를 지원합니다.
     */
    private boolean matchesRedirectUri(String pattern, String redirectUri) {
        // 정확히 일치
        if (pattern.equals(redirectUri)) {
            return true;
        }

        // 와일드카드 패턴 확인 (포트 부분에 * 사용)
        if (pattern.contains(":*/")) {
            // :* 기준으로 분리하여 각 부분을 quote하고 :\d+로 연결
            String[] parts = pattern.split(":\\*", 2);
            if (parts.length == 2) {
                String regexPattern = Pattern.quote(parts[0]) + ":\\d+" + Pattern.quote(parts[1]);
                return redirectUri.matches(regexPattern);
            }
        }

        return false;
    }

    /**
     * 클라이언트의 표시 이름 조회.
     */
    public String getDisplayName(String clientId) {
        ClientConfig config = clients.get(clientId);
        if (config != null && config.getDisplayName() != null) {
            return config.getDisplayName();
        }
        return clientId;
    }
}
