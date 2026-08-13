package com.bulc.homepage.dto.request;

import com.bulc.homepage.validation.ValidPassword;
import com.bulc.homepage.validation.ValidPhone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

import static com.bulc.homepage.config.ValidationConfig.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignupRequest {

    @NotNull(message = "가입 티켓은 필수입니다")
    private UUID signupTicket;

    @ValidPassword
    private String password;

    // @Size 는 null 을 통과시켜 이름 없이 가입되던 문제가 있었다(MDP-722). @NotBlank 로 필수화한다.
    @NotBlank(message = "이름은 필수입니다")
    @Size(min = NAME_MIN_LENGTH, max = NAME_MAX_LENGTH,
            message = "이름은 " + NAME_MIN_LENGTH + "자 이상 " + NAME_MAX_LENGTH + "자 이하여야 합니다")
    private String name;

    @ValidPhone
    private String phoneNumber;

    @Builder.Default
    private Boolean marketingAgreed = false;

    // 회원가입 시점의 페이지 언어 (ko/en). null이면 DB 기본값(country=KR) 사용.
    private String language;
}
