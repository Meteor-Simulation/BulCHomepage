package com.bulc.homepage.licensing.service;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * 토큰 서명/검증에 사용되는 RSA 키 제공자 인터페이스.
 *
 * 구현체:
 * - {@link DefaultSigningKeyProvider}: 환경변수/설정에서 키 로드 (프로덕션용)
 * - TestSigningKeyProvider: 런타임에 키 생성 (테스트용, src/test에 위치)
 *
 * 보안 정책:
 * - prod 환경에서 키 미설정 시 fail-fast (서버 부팅 실패)
 * - 테스트용 키가 프로덕션에서 사용되는 것을 방지하기 위해 keyId 분리
 */
public interface SigningKeyProvider {

    /**
     * 토큰 서명에 사용할 RSA 개인키.
     *
     * @return RSA PrivateKey, 또는 키가 설정되지 않은 경우 null
     */
    PrivateKey signingKey();

    /**
     * 토큰 검증에 사용할 RSA 공개키.
     *
     * @return RSA PublicKey, 또는 키가 설정되지 않은 경우 null
     */
    PublicKey verifyKey();

    /**
     * 키 식별자 (JWT kid 클레임용).
     *
     * prod와 test 키를 구분하는 데 사용됩니다.
     * prod에서는 "test"로 시작하는 keyId를 거부해야 합니다.
     *
     * @return 키 식별자 문자열
     */
    String keyId();

    /**
     * 키가 사용 가능한 상태인지 확인.
     *
     * @return true면 서명/검증 가능
     */
    default boolean isEnabled() {
        return signingKey() != null && verifyKey() != null;
    }
}
