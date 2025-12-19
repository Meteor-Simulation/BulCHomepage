package com.bulc.homepage.licensing.query.view;

import com.bulc.homepage.licensing.domain.Activation;
import com.bulc.homepage.licensing.domain.ActivationStatus;

import java.time.Instant;
import java.util.UUID;

public record ActivationView(
        UUID id,
        String deviceFingerprint,
        ActivationStatus status,
        Instant activatedAt,
        Instant lastSeenAt,
        String clientVersion,
        String clientOs
) {
    public static ActivationView from(Activation activation) {
        return new ActivationView(
                activation.getId(),
                activation.getDeviceFingerprint(),
                activation.getStatus(),
                activation.getActivatedAt(),
                activation.getLastSeenAt(),
                activation.getClientVersion(),
                activation.getClientOs()
        );
    }
}
