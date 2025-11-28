package com.bulc.homepage.dto.request;

import com.bulc.homepage.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.bulc.homepage.config.ValidationConfig.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignupRequest {

    @NotBlank(message = "아이디는 필수입니다")
    @Size(min = USERNAME_MIN_LENGTH, max = USERNAME_MAX_LENGTH,
            message = "아이디는 " + USERNAME_MIN_LENGTH + "자 이상 " + USERNAME_MAX_LENGTH + "자 이하여야 합니다")
    private String email;

    @ValidPassword
    private String password;

    @Size(min = NAME_MIN_LENGTH, max = NAME_MAX_LENGTH,
            message = "이름은 " + NAME_MIN_LENGTH + "자 이상 " + NAME_MAX_LENGTH + "자 이하여야 합니다")
    private String name;

    private String phoneNumber;
}
