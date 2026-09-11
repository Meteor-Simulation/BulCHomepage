package com.bulc.homepage.licensing.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * entitlement 어휘 레지스트리 검증 (MDP-789).
 */
class EntitlementRegistryTest {

    private EntitlementRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new EntitlementRegistry();
        registry.setByProduct(Map.of(
                "001", List.of("core-simulation", "export-csv", "advanced-visualization")
        ));
    }

    @Test
    @DisplayName("등록된 제품 스코프의 정본 키는 허용")
    void shouldAllowRegisteredKeyForProduct() {
        assertThat(registry.isAllowed("001", "core-simulation")).isTrue();
        assertThat(registry.isAllowed("001", "export-csv")).isTrue();
    }

    @Test
    @DisplayName("미등록 키는 거부 (오타 = 권한 오지급 방지)")
    void shouldRejectUnregisteredKey() {
        // 클라이언트 어휘(core-sim)는 서버 정본이 아니다 — 문자 일치 0쌍
        assertThat(registry.isAllowed("001", "core-sim")).isFalse();
        assertThat(registry.isAllowed("001", "report-auto")).isFalse();
        assertThat(registry.isAllowed("001", "typo-key")).isFalse();
    }

    @Test
    @DisplayName("다른 제품 스코프의 키는 해당 제품에서 거부")
    void shouldScopeKeysByProduct() {
        assertThat(registry.isAllowed("999", "core-simulation")).isFalse();
    }

    @Test
    @DisplayName("null 입력은 거부")
    void shouldRejectNullInput() {
        assertThat(registry.isAllowed(null, "core-simulation")).isFalse();
        assertThat(registry.isAllowed("001", null)).isFalse();
    }

    @Test
    @DisplayName("allowedKeys 는 제품의 허용 집합 반환, 미등록 제품은 빈 집합")
    void shouldReturnAllowedKeys() {
        assertThat(registry.allowedKeys("001"))
                .containsExactlyInAnyOrder("core-simulation", "export-csv", "advanced-visualization");
        assertThat(registry.allowedKeys("999")).isEmpty();
    }

    @Test
    @DisplayName("설정이 없으면 isEmpty (검증 생략 신호)")
    void shouldReportEmptyWhenUnconfigured() {
        assertThat(new EntitlementRegistry().isEmpty()).isTrue();
        assertThat(registry.isEmpty()).isFalse();
    }
}
