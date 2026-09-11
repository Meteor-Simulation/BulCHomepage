package com.bulc.homepage.licensing.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * license_plans.grace_period_features 동작 (MDP-791 · 계약 §4).
 */
class LicensePlanGracePeriodTest {

    private LicensePlan.LicensePlanBuilder baseBuilder() {
        return LicensePlan.builder()
                .productId(UUID.randomUUID())
                .code("PLAN-A")
                .name("Plan A")
                .licenseType(LicenseType.SUBSCRIPTION)
                .durationDays(365)
                .graceDays(7)
                .maxActivations(3)
                .maxConcurrentSessions(1)
                .allowOfflineDays(30);
    }

    @Test
    @DisplayName("gracePeriodFeatures 미지정 시 v1 기본값 full")
    void shouldDefaultToFullWhenNull() {
        LicensePlan plan = baseBuilder().build();
        assertThat(plan.getGracePeriodFeatures()).isEqualTo("full");
    }

    @Test
    @DisplayName("gracePeriodFeatures 지정 시 그 값으로 설정")
    void shouldUseProvidedValue() {
        LicensePlan plan = baseBuilder().gracePeriodFeatures("limited").build();
        assertThat(plan.getGracePeriodFeatures()).isEqualTo("limited");
    }

    @Test
    @DisplayName("update 에 null 전달 시 기존 값 유지")
    void shouldKeepExistingOnNullUpdate() {
        LicensePlan plan = baseBuilder().gracePeriodFeatures("limited").build();
        plan.update("PLAN-A", "Plan A", "d", LicenseType.SUBSCRIPTION,
                365, 7, 3, 1, 30, null);
        assertThat(plan.getGracePeriodFeatures()).isEqualTo("limited");
    }

    @Test
    @DisplayName("update 에 값 전달 시 갱신")
    void shouldUpdateWhenProvided() {
        LicensePlan plan = baseBuilder().build();
        plan.update("PLAN-A", "Plan A", "d", LicenseType.SUBSCRIPTION,
                365, 7, 3, 1, 30, "full");
        assertThat(plan.getGracePeriodFeatures()).isEqualTo("full");
    }
}
