package com.bulc.homepage.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 내 정보 수정에 이름 길이 검증이 없어 1자 또는 과도하게 긴 이름이 저장될 수 있던 문제를
 * 막는 검증기. 회원가입(SignupRequest)의 @Size 규칙과 기준을 맞추되,
 * 미입력은 "변경하지 않음"이므로 허용한다.
 */
@DisplayName("NameValidator")
class NameValidatorTest {

    private NameValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new NameValidator();
        context = mock(ConstraintValidatorContext.class);
        ConstraintValidatorContext.ConstraintViolationBuilder builder =
                mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
    }

    @Test
    @DisplayName("미입력(null/공백)은 변경하지 않음을 뜻하므로 허용한다")
    void allowsBlank() {
        assertThat(validator.isValid(null, context)).isTrue();
        assertThat(validator.isValid("", context)).isTrue();
        assertThat(validator.isValid("   ", context)).isTrue();
    }

    @Test
    @DisplayName("2자 이상 50자 이하는 허용한다")
    void allowsValidLength() {
        assertThat(validator.isValid("홍길동", context)).isTrue();
        assertThat(validator.isValid("김자", context)).isTrue();
        assertThat(validator.isValid("가".repeat(50), context)).isTrue();
    }

    @Test
    @DisplayName("1자 이름은 거부한다")
    void rejectsTooShort() {
        assertThat(validator.isValid("홍", context)).isFalse();
    }

    @Test
    @DisplayName("50자를 넘으면 거부한다")
    void rejectsTooLong() {
        assertThat(validator.isValid("가".repeat(51), context)).isFalse();
    }

    @Test
    @DisplayName("길이는 앞뒤 공백을 제외하고 센다")
    void trimsBeforeLengthCheck() {
        assertThat(validator.isValid("  홍  ", context)).isFalse();
        assertThat(validator.isValid("  홍길동  ", context)).isTrue();
    }
}
