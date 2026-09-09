package com.bulc.homepage.licensing.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * entitlement 어휘 레지스트리 (MDP-789 · 계약 v1.2.0 §2).
 *
 * <p>종전 {@code license_plan_entitlements.entitlement_key} 는 자유 문자열(VARCHAR 100)로
 * 서버 검증·제품 스코프가 0이었다 — 오타가 곧 권한 오지급이었다. 본 레지스트리는
 * <b>제품 코드별로 허용되는 정본 entitlement 키 집합</b>을 정의하고, 플랜 등록 시 검증한다.</p>
 *
 * <p><b>정본 어휘 = 서버 어휘</b> (사용자 결정 2026-09-09). 클라이언트(Unity 등)는
 * 자기 쪽 표기(core-sim 등)로의 매핑을 클라이언트에서 유지한다 — 서버는 서버 어휘만 발행한다.
 * 배포된 토큰·시드가 깨지지 않는 additive 방향이다.</p>
 *
 * <p>설정 예 (application.yml):</p>
 * <pre>
 * bulc:
 *   licensing:
 *     entitlements:
 *       by-product:
 *         "001":
 *           - core-simulation
 *           - export-csv
 *           - advanced-visualization
 * </pre>
 *
 * <p>설정이 비어 있으면({@link #isEmpty()}) 레지스트리 미구성으로 간주하여 검증을 생략한다
 * (개발 편의 · 운영은 반드시 설정).</p>
 */
@Component
@ConfigurationProperties(prefix = "bulc.licensing.entitlements")
@Getter
@Setter
public class EntitlementRegistry {

    /**
     * 제품 코드 → 허용 entitlement 키 목록.
     * key: products.code (예: "001"), value: 해당 제품에서 유효한 정본 키 목록.
     */
    private Map<String, List<String>> byProduct = new HashMap<>();

    /**
     * 레지스트리가 비어 있는지 (미구성) 여부.
     */
    public boolean isEmpty() {
        return byProduct.isEmpty();
    }

    /**
     * 특정 제품 코드에 대해 해당 entitlement 키가 정본 어휘인지 확인.
     *
     * @param productCode   제품 코드 (products.code)
     * @param entitlementKey 검증할 entitlement 키
     * @return 등록된 정본 키이면 true
     */
    public boolean isAllowed(String productCode, String entitlementKey) {
        if (productCode == null || entitlementKey == null) {
            return false;
        }
        List<String> allowed = byProduct.get(productCode);
        return allowed != null && allowed.contains(entitlementKey);
    }

    /**
     * 특정 제품 코드의 허용 키 집합 (없으면 빈 집합).
     */
    public Set<String> allowedKeys(String productCode) {
        List<String> allowed = byProduct.get(productCode);
        return allowed != null ? new LinkedHashSet<>(allowed) : Set.of();
    }
}
