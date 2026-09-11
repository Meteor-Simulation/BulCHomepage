package com.bulc.homepage.licensing.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * entitlement 레지스트리 기동 점검 (MDP-789 리뷰 반영 — fail-open 방지).
 */
class EntitlementRegistryStartupCheckTest {

    private Environment envWithProfiles(String... profiles) {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(profiles);
        return env;
    }

    private EntitlementRegistry emptyRegistry() {
        return new EntitlementRegistry();
    }

    private EntitlementRegistry configuredRegistry() {
        EntitlementRegistry r = new EntitlementRegistry();
        r.setByProduct(Map.of("001", List.of("core-simulation")));
        return r;
    }

    @Test
    @DisplayName("prod + 미구성 → fail-fast(기동 중단)")
    void shouldFailFastOnProdWhenEmpty() {
        var check = new EntitlementRegistryStartupCheck(emptyRegistry(), envWithProfiles("prod"));
        assertThatThrownBy(check::check)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("by-product");
    }

    @Test
    @DisplayName("dev + 미구성 → WARN 만 (기동 허용)")
    void shouldWarnOnNonProdWhenEmpty() {
        var check = new EntitlementRegistryStartupCheck(emptyRegistry(), envWithProfiles("dev"));
        assertThatCode(check::check).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("prod + 구성됨 → 통과")
    void shouldPassOnProdWhenConfigured() {
        var check = new EntitlementRegistryStartupCheck(configuredRegistry(), envWithProfiles("prod"));
        assertThatCode(check::check).doesNotThrowAnyException();
    }
}
