package com.bulc.homepage.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * 전화번호 유효성 검증 어노테이션
 * ValidationConfig의 설정(PHONE_PATTERN, PHONE_MIN_LENGTH, PHONE_MAX_LENGTH)에 따라 검증합니다.
 * 전화번호는 선택 항목이므로 null/공백은 유효로 처리하고, 값이 있을 때만 형식·길이를 검증합니다.
 */
@Documented
@Constraint(validatedBy = PhoneValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPhone {

    String message() default "전화번호 형식이 올바르지 않습니다";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
