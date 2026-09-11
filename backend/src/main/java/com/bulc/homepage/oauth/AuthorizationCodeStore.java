package com.bulc.homepage.oauth;

import java.util.Optional;

/**
 * Authorization Code 저장소 (MDP-793 · 계약 v1.2.0 §4).
 *
 * <p>종전 단일 인메모리 {@code ConcurrentHashMap} 구현은 서버 재기동·다중 인스턴스에서
 * 로그인 진행 중 code 가 유실됐다 (CLI 자체 로그인 도입으로 로그인 빈도가 늘면 발화 확률 상승).
 * 이를 Redis 기반으로 이관하되, <b>프로필 게이트 + 인메모리 fallback</b> 으로 구성하여
 * 머지가 Redis 인프라를 강제하지 않도록 한다 (병행 개발 조정 규약).</p>
 *
 * <p>구현 선택은 설정 {@code bulc.oauth.code-store} 로 한다:</p>
 * <ul>
 *   <li>{@code memory} (기본) → {@link InMemoryAuthorizationCodeStore}</li>
 *   <li>{@code redis} → {@link RedisAuthorizationCodeStore} (Redis 설정 필요)</li>
 * </ul>
 */
public interface AuthorizationCodeStore {

    /**
     * Authorization Code 생성 및 저장 (TTL 10분).
     *
     * @return 생성된 Authorization Code 문자열
     */
    String createAndStore(String userEmail, String clientId, String redirectUri,
                          String codeChallenge, String codeChallengeMethod);

    /**
     * Authorization Code 조회 및 유효성 검증 (소비하지 않음).
     */
    Optional<AuthorizationCode> findValidCode(String code);

    /**
     * Authorization Code 소비 (1회용 — 조회와 동시에 제거).
     * 다중 인스턴스에서도 원자적으로 1회만 성공해야 한다.
     */
    Optional<AuthorizationCode> consumeCode(String code);
}
