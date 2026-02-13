package com.bulc.homepage.licensing.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice(basePackages = "com.bulc.homepage.licensing")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LicenseExceptionHandler {

    @ExceptionHandler(LicenseException.class)
    public ResponseEntity<Map<String, Object>> handleLicenseException(LicenseException ex) {
        HttpStatus status = mapErrorCodeToStatus(ex.getErrorCode());

        Map<String, Object> body = Map.of(
                "error", ex.getErrorCode().name(),
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        );

        return ResponseEntity.status(status).body(body);
    }

    private HttpStatus mapErrorCodeToStatus(LicenseException.ErrorCode errorCode) {
        return switch (errorCode) {
            case LICENSE_NOT_FOUND, ACTIVATION_NOT_FOUND, PLAN_NOT_FOUND,
                 LICENSE_NOT_FOUND_FOR_PRODUCT -> HttpStatus.NOT_FOUND;
            case LICENSE_ALREADY_EXISTS, PLAN_CODE_DUPLICATE -> HttpStatus.CONFLICT;
            case ALL_LICENSES_FULL -> HttpStatus.CONFLICT;
            case LICENSE_EXPIRED, LICENSE_SUSPENDED, LICENSE_REVOKED,
                 ACTIVATION_LIMIT_EXCEEDED, ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case SESSION_DEACTIVATED, ACTIVATION_DEACTIVATED -> HttpStatus.FORBIDDEN;
            case INVALID_LICENSE_STATE, INVALID_ACTIVATION_STATE, PLAN_NOT_AVAILABLE,
                 INVALID_REQUEST, INVALID_ACTIVATION_OWNERSHIP -> HttpStatus.BAD_REQUEST;
            // Redeem 관련
            case REDEEM_CODE_INVALID -> HttpStatus.BAD_REQUEST;
            case REDEEM_CODE_NOT_FOUND, REDEEM_CAMPAIGN_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case REDEEM_CODE_EXPIRED, REDEEM_CODE_DISABLED -> HttpStatus.GONE;
            case REDEEM_CODE_DEPLETED, REDEEM_CAMPAIGN_FULL,
                 REDEEM_USER_LIMIT_EXCEEDED, REDEEM_CODE_HASH_DUPLICATE -> HttpStatus.CONFLICT;
            case REDEEM_CAMPAIGN_NOT_ACTIVE -> HttpStatus.BAD_REQUEST;
            case REDEEM_RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
        };
    }
}
