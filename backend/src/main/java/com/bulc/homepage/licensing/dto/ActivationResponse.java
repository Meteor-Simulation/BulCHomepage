package com.bulc.homepage.licensing.dto;

import com.bulc.homepage.licensing.domain.Activation;
import com.bulc.homepage.licensing.domain.ActivationStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * 활성화 응답 DTO.
 */
public record ActivationResponse(
        UUID id,
        UUID licenseId,
        String deviceFingerprint,
        ActivationStatus status,
        Instant activatedAt,
        Instant lastSeenAt,
        String clientVersion,
        String clientOs,
        String lastIp,
        boolean hasOfflineToken,
        Instant offlineTokenExpiresAt
) {
    public static ActivationResponse from(Activation activation) {
        return new ActivationResponse(
                activation.getId(),
                activation.getLicense().getId(),
                activation.getDeviceFingerprint(),
                activation.getStatus(),
                activation.getActivatedAt(),
                activation.getLastSeenAt(),
                activation.getClientVersion(),
                activation.getClientOs(),
                activation.getLastIp(),
                activation.getOfflineToken() != null,
                activation.getOfflineTokenExpiresAt()
        );
    }
}
