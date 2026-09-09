package com.bulc.homepage.licensing.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RFC 7638 JWK Thumbprint 계산 검증 (MDP-788).
 */
class JwkThumbprintTest {

    /**
     * RFC 7638 §3.1 공식 테스트 벡터.
     * 주어진 RSA 공개키(n, e)의 SHA-256 thumbprint 기대값이 RFC 에 명시되어 있다.
     */
    @Test
    @DisplayName("RFC 7638 §3.1 테스트 벡터와 일치")
    void shouldMatchRfc7638TestVector() throws Exception {
        // given - RFC 7638 §3.1 의 예시 키
        String n = "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw";
        String e = "AQAB";
        RSAPublicKey key = rsaPublicKey(n, e);

        // when
        String kid = JwkThumbprint.of(key);

        // then - RFC 명시 기대값
        assertThat(kid).isEqualTo("NzbLsXh8uDCcd-6MNwXF4W_7noWXFZAfHkxZsRGC9Xs");
    }

    @Test
    @DisplayName("Base64urlUInt 는 부호 바이트를 제거한 최소 표현 (e=65537 → AQAB)")
    void shouldEncodeMinimalUnsignedOctets() {
        assertThat(JwkThumbprint.base64UrlUInt(BigInteger.valueOf(65537))).isEqualTo("AQAB");
        // 최상위 비트가 1인 값 - toByteArray() 가 선행 0x00 을 붙이는 케이스
        assertThat(JwkThumbprint.base64UrlUInt(BigInteger.valueOf(0x80))).isEqualTo("gA");
    }

    private RSAPublicKey rsaPublicKey(String nB64Url, String eB64Url) throws Exception {
        BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(nB64Url));
        BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(eB64Url));
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(n, e));
    }
}
