package com.bulc.homepage.licensing.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * entitlement 레지스트리 구성 기동 점검 (MDP-789 리뷰 반영).
 *
 * <p>리뷰(#244) 지적: {@link EntitlementRegistry} 가 비어 있으면 검증을 조용히 전부 생략하는
 * fail-open 이라, 운영 배포에서 프로파일 오버라이드·오타·누락으로 {@code by-product} 가 비면
 * MDP-789 가 막으려던 "오타=권한 오지급"이 그대로 재발한다. 문서의 "운영은 반드시 설정"은
 * 강제 수단이 아니었다.</p>
 *
 * <p>이 점검은 기동 시 레지스트리가 비어 있으면 <b>prod 프로파일에서 fail-fast</b>(기동 중단),
 * 그 외 프로파일에서는 WARN 을 남긴다.</p>
 */
@Slf4j
@Component
public class EntitlementRegistryStartupCheck {

    private final EntitlementRegistry registry;
    private final Environment environment;

    public EntitlementRegistryStartupCheck(EntitlementRegistry registry, Environment environment) {
        this.registry = registry;
        this.environment = environment;
    }

    @PostConstruct
    public void check() {
        if (!registry.isEmpty()) {
            return;
        }
        if (isProd()) {
            throw new IllegalStateException(
                    "[FATAL] entitlement 어휘 레지스트리(bulc.licensing.entitlements.by-product)가 "
                            + "prod 프로파일에서 비어 있습니다. 미구성 시 플랜 entitlement 검증이 전부 생략되어 "
                            + "오타=권한 오지급 위험이 있으므로 서버를 시작할 수 없습니다. 제품별 정본 키를 설정하세요.");
        }
        log.warn("========================================");
        log.warn("entitlement 레지스트리(bulc.licensing.entitlements.by-product)가 비어 있습니다.");
        log.warn("플랜 entitlement 검증이 생략됩니다 (개발 편의). 운영 프로파일에서는 반드시 설정하세요.");
        log.warn("========================================");
    }

    private boolean isProd() {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }
}
