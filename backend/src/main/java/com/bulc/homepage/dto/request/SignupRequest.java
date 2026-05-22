package com.bulc.homepage.dto.request;

import com.bulc.homepage.validation.ValidPassword;
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

    @Size(min = NAME_MIN_LENGTH, max = NAME_MAX_LENGTH,
            message = "이름은 " + NAME_MIN_LENGTH + "자 이상 " + NAME_MAX_LENGTH + "자 이하여야 합니다")
    private String name;

    private String phoneNumber;

    @Builder.Default
    private Boolean marketingAgreed = false;
}
