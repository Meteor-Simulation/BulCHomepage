package com.bulc.homepage.licensing.dto;

import java.time.Instant;
import java.util.UUID;

public record RedeemClaimResponse(
        UUID licenseId,
        String licenseKey,
        String productName,
        String planName,
        Instant validUntil
) {
}
