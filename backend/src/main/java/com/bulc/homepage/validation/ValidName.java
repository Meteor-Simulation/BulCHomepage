package com.bulc.homepage.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * 이름 유효성 검증 어노테이션.
 * ValidationConfig의 설정(NAME_MIN_LENGTH, NAME_MAX_LENGTH)에 따라 검증합니다.
 *
 * <p>{@link ValidPhone} 과 같이 null/공백은 유효로 처리한다. 내 정보 수정에서
 * 미입력은 "변경하지 않음"을 의미하고, 소셜 로그인은 provider가 이름을 주지 않는
 * 경우가 있기 때문이다. 값이 있을 때만 길이를 검증한다.
 */
@Documented
@Constraint(validatedBy = NameValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidName {

    String message() default "이름 형식이 올바르지 않습니다";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
