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
        ACTIVATION_NOT_FOUND("활성화 정보를 찾을 수 없습니다"),
        INVALID_LICENSE_STATE("잘못된 라이선스 상태입니다"),
        INVALID_ACTIVATION_STATE("잘못된 활성화 상태입니다"),
        PLAN_NOT_FOUND("플랜을 찾을 수 없습니다"),
        PLAN_CODE_DUPLICATE("플랜 코드가 중복됩니다"),
        PLAN_NOT_AVAILABLE("사용할 수 없는 플랜입니다"),

        // v1.1 추가
        ACCESS_DENIED("접근 권한이 없습니다"),
        LICENSE_NOT_FOUND_FOR_PRODUCT("해당 제품의 라이선스가 없습니다"),

        // v1.1.1 추가
        SESSION_DEACTIVATED("세션이 다른 기기에서 비활성화되었습니다"),
        INVALID_REQUEST("잘못된 요청입니다"),
        INVALID_ACTIVATION_OWNERSHIP("비활성화 대상 세션이 해당 라이선스에 속하지 않습니다"),

        // v1.1.3 추가
        ACTIVATION_DEACTIVATED("활성화가 다른 기기에 의해 비활성화되었습니다. 재인증이 필요합니다"),

        // v0.3.0: Auto-Resolve 통합 에러 코드
        ALL_LICENSES_FULL("사용 가능한 라이선스가 없습니다. 접속을 위해 종료할 세션을 선택해주세요"),

        // Redeem 관련 에러 코드
        REDEEM_CODE_INVALID("유효하지 않은 리딤 코드 형식입니다"),
        REDEEM_CODE_NOT_FOUND("리딤 코드를 찾을 수 없습니다"),
        REDEEM_CODE_EXPIRED("만료된 리딤 코드입니다"),
        REDEEM_CODE_DISABLED("비활성화된 리딤 코드입니다"),
        REDEEM_CODE_DEPLETED("사용 횟수가 소진된 리딤 코드입니다"),
        REDEEM_CAMPAIGN_FULL("캠페인의 발급 한도에 도달했습니다"),
        REDEEM_CAMPAIGN_NOT_ACTIVE("캠페인이 활성 상태가 아닙니다"),
        REDEEM_USER_LIMIT_EXCEEDED("사용자별 한도를 초과했습니다"),
        REDEEM_CAMPAIGN_NOT_FOUND("캠페인을 찾을 수 없습니다"),
        REDEEM_CODE_HASH_DUPLICATE("동일한 코드가 이미 존재합니다"),
        REDEEM_RATE_LIMITED("요청이 너무 빈번합니다. 잠시 후 다시 시도해주세요");

        private final String message;

        ErrorCode(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}
