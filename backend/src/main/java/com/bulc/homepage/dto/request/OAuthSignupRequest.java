package com.bulc.homepage.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthSignupRequest {

    @NotBlank(message = "토큰은 필수입니다")
    private String token;

    @NotBlank(message = "비밀번호는 필수입니다")
    @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다")
    private String password;

    private String name;
    private String phone;

    // 회원가입 시점의 페이지 언어 (ko/en). null이면 DB 기본값(country=KR) 사용.
    private String language;
}
