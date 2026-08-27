package com.bulc.homepage.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;

    private String refreshToken;

    private String tokenType;

    private Long expiresIn;

    private UserInfo user;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private String id;  // email이 ID로 사용됨
        private String email;
        private String name;
        private String rolesCode;
        private String language;  // 사용자 언어 설정 (ko, en)
        private Boolean marketingAgreed;  // 광고성 메일 수신 동의 여부
        private String marketingConsent;  // 수신 상태 Y:동의 N:거절 P:미선택 (동의 팝업 노출 판단용, MDP-772)
    }
}
