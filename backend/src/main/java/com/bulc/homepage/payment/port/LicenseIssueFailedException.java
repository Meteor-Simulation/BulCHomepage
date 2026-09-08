package com.bulc.homepage.payment.port;

/**
 * 라이선스 발급자 측 사유로 구매 검사 또는 발급이 실패한 경우.
 */
public class LicenseIssueFailedException extends RuntimeException {

    public LicenseIssueFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
