package com.bulc.homepage.oauth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * AuthorizationCodeStore 빈 선택/폴백 검증 (MDP-793 리뷰 반영).
 * 미설정/memory/redis/알 수 없는 값 모두에서 정확히 1개 빈이 활성화되는지 확인.
 */
class AuthorizationCodeStoreConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AuthorizationCodeStoreConfig.class);

    @Test
    @DisplayName("미설정 → 인메모리 폴백, 빈 정확히 1개")
    void shouldDefaultToInMemoryWhenUnset() {
        runner.run(ctx -> {
            assertThat(ctx.getBeansOfType(AuthorizationCodeStore.class)).hasSize(1);
            assertThat(ctx.getBean(AuthorizationCodeStore.class))
                    .isInstanceOf(InMemoryAuthorizationCodeStore.class);
        });
    }

    @Test
    @DisplayName("code-store=memory → 인메모리")
    void shouldUseInMemoryWhenMemory() {
        runner.withPropertyValues("bulc.oauth.code-store=memory").run(ctx ->
                assertThat(ctx.getBean(AuthorizationCodeStore.class))
                        .isInstanceOf(InMemoryAuthorizationCodeStore.class));
    }

    @Test
    @DisplayName("code-store=redis (+StringRedisTemplate) → Redis")
    void shouldUseRedisWhenRedis() {
        runner.withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withPropertyValues("bulc.oauth.code-store=redis")
                .run(ctx -> {
                    assertThat(ctx.getBeansOfType(AuthorizationCodeStore.class)).hasSize(1);
                    assertThat(ctx.getBean(AuthorizationCodeStore.class))
                            .isInstanceOf(RedisAuthorizationCodeStore.class);
                });
    }

    @Test
    @DisplayName("code-store=redis 인데 StringRedisTemplate 없음 → 기동 실패(명확한 메시지)")
    void shouldFailWhenRedisSelectedButNoTemplate() {
        runner.withPropertyValues("bulc.oauth.code-store=redis").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasMessageContaining("StringRedisTemplate");
        });
    }

    @Test
    @DisplayName("알 수 없는 값(오타) → 인메모리 안전 폴백 (빈 0개/기동실패 아님)")
    void shouldFallBackToInMemoryOnUnknownValue() {
        runner.withPropertyValues("bulc.oauth.code-store=bogus").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBeansOfType(AuthorizationCodeStore.class)).hasSize(1);
            assertThat(ctx.getBean(AuthorizationCodeStore.class))
                    .isInstanceOf(InMemoryAuthorizationCodeStore.class);
        });
    }

    @Test
    @DisplayName("대소문자·공백 정규화: 'Redis ' → redis 로 취급 (+템플릿 있으면 Redis)")
    void shouldNormalizeRedisCasingAndWhitespace() {
        runner.withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withPropertyValues("bulc.oauth.code-store=Redis ")
                .run(ctx -> assertThat(ctx.getBean(AuthorizationCodeStore.class))
                        .isInstanceOf(RedisAuthorizationCodeStore.class));
    }
}
