package com.bulc.homepage.oauth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * RedisAuthorizationCodeStore 단위 테스트 (MDP-793).
 * StringRedisTemplate 을 mock 하여 TTL 저장·원자적 소비(getAndDelete)·왕복 직렬화를 검증.
 */
@ExtendWith(MockitoExtension.class)
class RedisAuthorizationCodeStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private RedisAuthorizationCodeStore store;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        store = new RedisAuthorizationCodeStore(redisTemplate);
    }

    @Test
    @DisplayName("createAndStore 는 oauth:authcode: 키에 10분 TTL 로 저장")
    void shouldStoreWithTtl() {
        String code = store.createAndStore("user@x.com", "bulc-cli",
                "http://127.0.0.1:5000/oauth/callback", "challenge", "S256");

        assertThat(code).isNotBlank();

        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCap = ArgumentCaptor.forClass(Duration.class);
        verify(valueOps).set(keyCap.capture(), valCap.capture(), ttlCap.capture());

        assertThat(keyCap.getValue()).isEqualTo("oauth:authcode:" + code);
        assertThat(ttlCap.getValue()).isEqualTo(Duration.ofSeconds(600));
        assertThat(valCap.getValue()).contains("user@x.com").contains("bulc-cli");
    }

    @Test
    @DisplayName("findValidCode 는 저장된 JSON 을 AuthorizationCode 로 복원")
    void shouldFindAndDeserialize() {
        // given - createAndStore 가 만든 JSON 을 가로채 findValidCode 응답으로 돌려준다
        store.createAndStore("user@x.com", "bulc-cli", "http://127.0.0.1:5000/oauth/callback",
                "challenge-value-xyz", "S256");
        ArgumentCaptor<String> valCap = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(anyString(), valCap.capture(), any(Duration.class));
        String storedJson = valCap.getValue();

        given(valueOps.get(anyString())).willReturn(storedJson);

        Optional<AuthorizationCode> found = store.findValidCode("some-code");

        assertThat(found).isPresent();
        assertThat(found.get().getUserEmail()).isEqualTo("user@x.com");
        assertThat(found.get().getClientId()).isEqualTo("bulc-cli");
        assertThat(found.get().getCodeChallenge()).isEqualTo("challenge-value-xyz");
        assertThat(found.get().isValid()).isTrue();
    }

    @Test
    @DisplayName("consumeCode 는 getAndDelete 로 원자적 소비 (1회용)")
    void shouldConsumeAtomically() {
        store.createAndStore("user@x.com", "bulc-cli", "http://127.0.0.1:5000/oauth/callback",
                "challenge", "S256");
        ArgumentCaptor<String> valCap = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(anyString(), valCap.capture(), any(Duration.class));
        String storedJson = valCap.getValue();

        given(valueOps.getAndDelete(anyString())).willReturn(storedJson);

        Optional<AuthorizationCode> consumed = store.consumeCode("some-code");

        assertThat(consumed).isPresent();
        assertThat(consumed.get().getUserEmail()).isEqualTo("user@x.com");
        // getAndDelete 로 조회+삭제가 한 번에 — 별도 delete 호출 없음
        verify(valueOps).getAndDelete(eq("oauth:authcode:some-code"));
    }

    @Test
    @DisplayName("없는 code 조회/소비는 empty")
    void shouldReturnEmptyWhenAbsent() {
        given(valueOps.get(anyString())).willReturn(null);
        given(valueOps.getAndDelete(anyString())).willReturn(null);

        assertThat(store.findValidCode("nope")).isEmpty();
        assertThat(store.consumeCode("nope")).isEmpty();
    }
}
