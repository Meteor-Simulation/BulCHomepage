package com.bulc.homepage.validation;

import com.bulc.homepage.config.ValidationConfig;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 이름 유효성 검증 구현체.
 * ValidationConfig의 설정에 따라 이름을 검증합니다.
 *
 * 이름은 선택 항목입니다.
 *  - null 또는 공백  : 유효 (미입력 = 변경하지 않음)
 *  - 값이 있는 경우  : 길이(MIN~MAX) 충족 필요
 *
 * 회원가입(SignupRequest)에는 길이 제한이 있었으나 내 정보 수정에는 없어
 * 1자 또는 과도하게 긴 이름이 저장될 수 있었다. 두 경로의 기준을 통일한다.
 */
public class NameValidator implements ConstraintValidator<ValidName, String> {

    @Override
    public boolean isValid(String name, ConstraintValidatorContext context) {
        // 선택 항목: 미입력은 허용
        if (name == null || name.isBlank()) {
            return true;
        }

        String value = name.trim();

        if (value.length() < ValidationConfig.NAME_MIN_LENGTH) {
            setMessage(context, "이름은 " + ValidationConfig.NAME_MIN_LENGTH + "자 이상이어야 합니다");
            return false;
        }

        if (value.length() > ValidationConfig.NAME_MAX_LENGTH) {
            setMessage(context, "이름은 " + ValidationConfig.NAME_MAX_LENGTH + "자 이하여야 합니다");
            return false;
        }

        return true;
    }

    private void setMessage(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }
}
