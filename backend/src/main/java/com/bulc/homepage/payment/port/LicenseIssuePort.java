package com.bulc.homepage.payment.port;

import java.time.Instant;
import java.util.UUID;

/**
 * 결제 모듈이 라이선스 발급자에게 요구하는 최소 계약.
 *
 * <p>결제는 licensing 모듈의 구현·도메인 타입을 직접 알지 않는다.
 * 구현은 licensing 쪽 어댑터가 제공한다.
 */
public interface LicenseIssuePort {

    /**
     * 해당 요금제를 구매할 수 있는 상태인지 검사한다. 토스 API 호출 전에 실행한다.
     *
     * @throws LicenseAlreadyOwnedException 동일 product 라이선스를 이미 보유 중인 경우
     * @throws LicenseIssueFailedException  그 외 발급자 측 사유로 구매가 불가한 경우
     */
    void requirePurchasable(UUID userId, UUID licensePlanId);

    /**
     * 결제 완료 후 라이선스를 발급한다.
     *
     * @param sourceOrderId 주문 식별자. 동일 값으로 재호출해도 중복 발급되지 않는다.
     * @throws LicenseIssueFailedException 발급에 실패한 경우
     */
    IssuedLicense issue(UUID userId, UUID licensePlanId, UUID sourceOrderId);

    /**
     * 구독 갱신 결제 후 라이선스 유효기간을 연장한다.
     * 연장할 라이선스가 없으면 발급자 판단에 따라 신규 발급으로 폴백된다.
     *
     * @param sourceOrderId  갱신 회차별 멱등 키
     * @param newValidUntil  연장 후 만료 시각
     * @throws LicenseIssueFailedException 연장에 실패한 경우
     */
    void renew(UUID userId, UUID licensePlanId, UUID sourceOrderId, Instant newValidUntil);
}
