package com.bulc.homepage.licensing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * v1.1.1: 강제 검증 요청 DTO.
 *
 * 동시 세션 제한 초과 시 클라이언트가 비활성화할 세션을 선택하여 요청.
 * /validate에서 409 CONCURRENT_SESSION_LIMIT_EXCEEDED 응답을 받은 후,
 * 비활성화할 세션 ID 목록과 함께 이 엔드포인트를 호출.
 */
public record ForceValidateRequest(
        @NotNull(message = "라이선스 ID는 필수입니다")
        UUID licenseId,

        @NotBlank(message = "기기 fingerprint는 필수입니다")
        String deviceFingerprint,

        // v1.2.0 (MDP-787): 자기 세션 식별자 (선택) - 보유 시 self 판정이 fingerprint 대신 이 값으로 수행됨
        UUID activationId,

        @NotNull(message = "비활성화할 세션 목록은 필수입니다")
        @Size(min = 1, message = "최소 1개 이상의 세션을 비활성화해야 합니다")
        List<UUID> deactivateActivationIds,

        // 클라이언트 정보 (선택)
        String clientVersion,
        String clientOs,

        // 기기 표시 이름 (선택) - UX용
        String deviceDisplayName
) {}
