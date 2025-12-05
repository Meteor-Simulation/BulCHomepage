package com.bulc.homepage.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityLogRequest {

    @NotBlank(message = "action은 필수입니다")
    private String action;

    private String resourcePath;

    private String httpMethod;

    private String referrer;

    private String metadata;
}
