package com.bulc.homepage.validation;

import com.bulc.homepage.config.ValidationConfig;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 비밀번호 유효성 검증 구현체
 * ValidationConfig의 설정에 따라 비밀번호를 검증합니다.
 */
public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {

    @Override
    public void initialize(ValidPassword constraintAnnotation) {
        // 초기화 로직 (필요시)
    }

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.isEmpty()) {
            setMessage(context, "비밀번호는 필수입니다");
            return false;
        }

        // 길이 검증
        if (password.length() < ValidationConfig.PASSWORD_MIN_LENGTH) {
            setMessage(context, "비밀번호는 " + ValidationConfig.PASSWORD_MIN_LENGTH + "자 이상이어야 합니다");
            return false;
        }

        if (password.length() > ValidationConfig.PASSWORD_MAX_LENGTH) {
            setMessage(context, "비밀번호는 " + ValidationConfig.PASSWORD_MAX_LENGTH + "자 이하여야 합니다");
            return false;
        }

        // 영문 포함 검증
        if (ValidationConfig.PASSWORD_REQUIRE_LETTER && !containsLetter(password)) {
            setMessage(context, "비밀번호에 영문이 포함되어야 합니다");
            return false;
        }

        // 대문자 포함 검증
        if (ValidationConfig.PASSWORD_REQUIRE_UPPERCASE && !containsUppercase(password)) {
            setMessage(context, "비밀번호에 대문자가 포함되어야 합니다");
            return false;
        }

        // 소문자 포함 검증
        if (ValidationConfig.PASSWORD_REQUIRE_LOWERCASE && !containsLowercase(password)) {
            setMessage(context, "비밀번호에 소문자가 포함되어야 합니다");
            return false;
        }

        // 숫자 포함 검증
        if (ValidationConfig.PASSWORD_REQUIRE_DIGIT && !containsDigit(password)) {
            setMessage(context, "비밀번호에 숫자가 포함되어야 합니다");
            return false;
        }

        // 특수문자 포함 검증
        if (ValidationConfig.PASSWORD_REQUIRE_SPECIAL_CHAR && !containsSpecialChar(password)) {
            setMessage(context, "비밀번호에 특수문자가 포함되어야 합니다");
            return false;
        }

        return true;
    }

    private void setMessage(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }

    private boolean containsLetter(String str) {
        for (char c : str.toCharArray()) {
            if (Character.isLetter(c)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsUppercase(String str) {
        for (char c : str.toCharArray()) {
            if (Character.isUpperCase(c)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsLowercase(String str) {
        for (char c : str.toCharArray()) {
            if (Character.isLowerCase(c)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsDigit(String str) {
        for (char c : str.toCharArray()) {
            if (Character.isDigit(c)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsSpecialChar(String str) {
        for (char c : str.toCharArray()) {
            if (ValidationConfig.PASSWORD_SPECIAL_CHARS.indexOf(c) >= 0) {
                return true;
            }
        }
        return false;
    }
}
