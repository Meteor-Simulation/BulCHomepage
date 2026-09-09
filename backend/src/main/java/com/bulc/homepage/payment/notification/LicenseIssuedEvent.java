package com.bulc.homepage.payment.notification;

import java.time.Instant;
import java.util.UUID;

/**
 * 라이선스가 실제로 발급된 뒤 발행되는 이벤트 (MDP-833).
 *
 * <p>수신 이메일 주소를 담지 않는 이유: 발송 시점에 {@code users} 를 조회해야 한다.
 * 결제 레코드의 {@code payment.user_email} 은 웹훅 경로에서 UUID 문자열이 저장되어 있어
 * 메일 주소로 쓸 수 없다.
 *
 * @param recovered 재시도 큐를 통해 뒤늦게 복구 발급된 건인지 여부.
 *                  결제 직후 실패했다가 나중에 발급된 경우라 안내 문구가 달라진다.
 */
public record LicenseIssuedEvent(
        UUID userId,
        UUID licenseId,
        String licenseKey,
        Instant validUntil,
        UUID sourceOrderId,
        boolean recovered
) {
}
