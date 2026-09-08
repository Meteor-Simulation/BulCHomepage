package com.bulc.homepage.payment.port;

import java.time.Instant;
import java.util.UUID;

/**
 * 발급된 라이선스 중 결제 응답에 필요한 정보만 담는다.
 */
public record IssuedLicense(
        UUID id,
        String licenseKey,
        Instant validUntil
) {
}
