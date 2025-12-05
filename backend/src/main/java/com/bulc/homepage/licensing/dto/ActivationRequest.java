package com.bulc.homepage.licensing.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 기기 활성화 요청 DTO.
 */
public record ActivationRequest(
        @NotBlank String deviceFingerprint,
        String clientVersion,
        String clientOs,
        String clientIp
) {}
