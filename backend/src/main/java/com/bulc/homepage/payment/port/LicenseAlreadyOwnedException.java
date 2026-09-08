package com.bulc.homepage.payment.port;

/**
 * 동일 product 라이선스를 이미 보유해 구매할 수 없는 경우.
 */
public class LicenseAlreadyOwnedException extends RuntimeException {

    public LicenseAlreadyOwnedException(String message) {
        super(message);
    }
}
