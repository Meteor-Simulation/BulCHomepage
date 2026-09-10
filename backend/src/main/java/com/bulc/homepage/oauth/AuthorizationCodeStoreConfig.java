package com.bulc.homepage.oauth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Authorization Code 저장소 빈 선택 (MDP-793 리뷰 반영).
 *
 * <p>종전 두 구현을 각각 {@code @ConditionalOnProperty}(memory matchIfMissing / redis)로 두면,
 * {@code bulc.oauth.code-store} 에 알 수 없는 값(오타 {@code "Redis"}, 후행 공백 등)이 오면
 * <b>두 조건 모두 false → 빈 0개 → 컨텍스트 기동 실패</b>가 됐다. 게다가 application.yml 이
 * 프로퍼티를 항상 실체화하므로 {@code matchIfMissing} 은 사실상 발동하지 않아, 환경변수 오타가
 * 곧바로 기동 실패로 이어졌다.</p>
 *
 * <p>본 팩토리는 값을 명시적으로 해석해 <b>정확히 하나의 빈</b>을 만든다:
 * {@code redis}(대소문자·공백 무관)면 Redis, 그 외 모든 값은 인메모리로 <b>안전 폴백</b>한다
 * (알 수 없는 값은 WARN 후 memory). {@code @Bean} 반환 인스턴스도 스프링 빈이라
 * 인메모리 구현의 {@code @Scheduled} 정리가 정상 동작한다.</p>
 */
@Slf4j
@Configuration
public class AuthorizationCodeStoreConfig {

    @Bean
    public AuthorizationCodeStore authorizationCodeStore(
            @Value("${bulc.oauth.code-store:memory}") String codeStore,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider) {

        String kind = codeStore == null ? "memory" : codeStore.trim().toLowerCase();

        if ("redis".equals(kind)) {
            StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
            if (redisTemplate == null) {
                throw new IllegalStateException(
                        "bulc.oauth.code-store=redis 이지만 StringRedisTemplate 빈이 없습니다. "
                                + "spring.data.redis 설정을 확인하세요.");
            }
            return new RedisAuthorizationCodeStore(redisTemplate);
        }

        if (!"memory".equals(kind)) {
            log.warn("알 수 없는 bulc.oauth.code-store 값 '{}' — 인메모리 구현으로 폴백합니다 (허용: memory|redis).",
                    codeStore);
        }
        return new InMemoryAuthorizationCodeStore();
    }
}
