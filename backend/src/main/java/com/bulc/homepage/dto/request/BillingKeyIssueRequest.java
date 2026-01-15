package com.bulc.homepage.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BillingKeyIssueRequest {

    @NotBlank(message = "authKey는 필수입니다")
    private String authKey;

    private boolean setAsDefault = false;
}
