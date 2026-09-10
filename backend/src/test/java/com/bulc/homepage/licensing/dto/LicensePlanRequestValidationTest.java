package com.bulc.homepage.licensing.dto;

import com.bulc.homepage.licensing.domain.LicenseType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LicensePlanRequest.gracePeriodFeatures @Size(32) 검증 (MDP-791 리뷰 #246 반영).
 * DB 컬럼 VARCHAR(32) 방어 — deviceDisplayName 과 동일 논리.
 */
class LicensePlanRequestValidationTest {

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

    private LicensePlanRequest withGrace(String grace) {
        return new LicensePlanRequest(
                UUID.randomUUID(), "PLAN-A", "Plan A", "desc",
                LicenseType.SUBSCRIPTION, 365, 7, 3, 1, 30,
                List.of("core-simulation"), grace
        );
    }

    @Test
    @DisplayName("gracePeriodFeatures 32자는 허용")
    void shouldAccept32Chars() {
        assertThat(validator.validate(withGrace("a".repeat(32)))).isEmpty();
    }

    @Test
    @DisplayName("gracePeriodFeatures 33자는 거부")
    void shouldReject33Chars() {
        assertThat(validator.validate(withGrace("a".repeat(33))))
                .anyMatch(v -> v.getPropertyPath().toString().equals("gracePeriodFeatures"));
    }

    @Test
    @DisplayName("gracePeriodFeatures null 은 허용 (기본 full 로 처리)")
    void shouldAcceptNull() {
        assertThat(validator.validate(withGrace(null))).isEmpty();
    }
}
