package com.bulc.homepage.validation;

import com.bulc.homepage.config.ValidationConfig;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 전화번호 유효성 검증 구현체
 * ValidationConfig의 설정에 따라 전화번호를 검증합니다.
 *
 * 전화번호는 선택 항목입니다.
 *  - null 또는 공백  : 유효 (미입력 허용)
 *  - 값이 있는 경우  : 패턴(숫자/하이픈) + 길이(MIN~MAX) 충족 필요
 *
 * 과거 "010"처럼 앞자리만 입력된 불완전 값이 그대로 저장되던 문제를 막기 위해
 * 최소 길이(기본 10) 미만 값은 거부한다.
 */
public class PhoneValidator implements ConstraintValidator<ValidPhone, String> {

    @Override
    public void initialize(ValidPhone constraintAnnotation) {
        // 초기화 로직 (필요시)
    }

    @Override
    public boolean isValid(String phone, ConstraintValidatorContext context) {
        // 선택 항목: 미입력은 허용
        if (phone == null || phone.isBlank()) {
            return true;
        }

        String value = phone.trim();

        // 형식(숫자/하이픈) 검증
        if (!value.matches(ValidationConfig.PHONE_PATTERN)) {
            setMessage(context, "전화번호는 숫자와 하이픈(-)만 사용할 수 있습니다");
            return false;
        }

        // 길이 검증
        if (value.length() < ValidationConfig.PHONE_MIN_LENGTH) {
            setMessage(context, "전화번호는 " + ValidationConfig.PHONE_MIN_LENGTH + "자 이상이어야 합니다");
            return false;
        }

        if (value.length() > ValidationConfig.PHONE_MAX_LENGTH) {
            setMessage(context, "전화번호는 " + ValidationConfig.PHONE_MAX_LENGTH + "자 이하여야 합니다");
            return false;
        }

        return true;
    }

    private void setMessage(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }
}
