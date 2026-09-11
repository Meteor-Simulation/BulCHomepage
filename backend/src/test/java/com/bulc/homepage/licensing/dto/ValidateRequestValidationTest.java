package com.bulc.homepage.licensing.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ValidateRequest.deviceDisplayName @Size(100) 검증 (MDP-791 · 계약 §4).
 * DB 컬럼은 VARCHAR(100)인데 DTO 검증이 없어 서버가 방어하지 못하던 결손 보완.
 */
class ValidateRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private ValidateRequest withDisplayName(String name) {
        return new ValidateRequest(
                "001", null, null, "fp-123",
                "1.0", "Windows", name, null
        );
    }

    @Test
    @DisplayName("deviceDisplayName 100자는 허용")
    void shouldAcceptExactly100Chars() {
        String name = "a".repeat(100);
        assertThat(validator.validate(withDisplayName(name))).isEmpty();
    }

    @Test
    @DisplayName("deviceDisplayName 101자는 거부")
    void shouldRejectOver100Chars() {
        String name = "a".repeat(101);
        assertThat(validator.validate(withDisplayName(name)))
                .anyMatch(v -> v.getPropertyPath().toString().equals("deviceDisplayName"));
    }

    @Test
    @DisplayName("deviceDisplayName null 은 허용 (선택 필드)")
    void shouldAcceptNull() {
        assertThat(validator.validate(withDisplayName(null))).isEmpty();
    }
}
