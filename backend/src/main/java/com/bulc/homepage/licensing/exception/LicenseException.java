package com.bulc.homepage.licensing.exception;

/**
 * 라이선스 관련 비즈니스 예외.
 */
public class LicenseException extends RuntimeException {

    private final ErrorCode errorCode;

    public LicenseException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public LicenseException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + ": " + detail);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public enum ErrorCode {
        LICENSE_NOT_FOUND("라이선스를 찾을 수 없습니다"),
        LICENSE_EXPIRED("라이선스가 만료되었습니다"),
        LICENSE_SUSPENDED("라이선스가 정지되었습니다"),
        LICENSE_REVOKED("라이선스가 회수되었습니다"),
        LICENSE_ALREADY_EXISTS("이미 해당 제품의 라이선스가 존재합니다"),
        ACTIVATION_LIMIT_EXCEEDED("최대 기기 활성화 수를 초과했습니다"),
        CONCURRENT_SESSION_LIMIT_EXCEEDED("최대 동시 세션 수를 초과했습니다"),
        ACTIVATION_NOT_FOUND("활성화 정보를 찾을 수 없습니다"),
        INVALID_LICENSE_STATE("잘못된 라이선스 상태입니다"),
        INVALID_ACTIVATION_STATE("잘못된 활성화 상태입니다"),
        PLAN_NOT_FOUND("플랜을 찾을 수 없습니다"),
        PLAN_CODE_DUPLICATE("플랜 코드가 중복됩니다"),
        PLAN_NOT_AVAILABLE("사용할 수 없는 플랜입니다"),

        // v1.1 추가
        ACCESS_DENIED("접근 권한이 없습니다"),
        LICENSE_NOT_FOUND_FOR_PRODUCT("해당 제품의 라이선스가 없습니다"),
        LICENSE_SELECTION_REQUIRED("복수의 라이선스가 존재합니다. licenseId를 지정해주세요");

        private final String message;

        ErrorCode(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}
