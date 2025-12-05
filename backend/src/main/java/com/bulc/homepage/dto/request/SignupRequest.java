package com.bulc.homepage.dto.request;

import com.bulc.homepage.validation.ValidPassword;
import jakarta.validation.constraints.Email;
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

    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "올바른 이메일 형식을 입력해주세요")
    @Size(max = 255, message = "이메일은 255자 이하여야 합니다")
    private String email;

    @ValidPassword
    private String password;

    @Size(min = NAME_MIN_LENGTH, max = NAME_MAX_LENGTH,
            message = "이름은 " + NAME_MIN_LENGTH + "자 이상 " + NAME_MAX_LENGTH + "자 이하여야 합니다")
    private String name;

    private String phoneNumber;
}
