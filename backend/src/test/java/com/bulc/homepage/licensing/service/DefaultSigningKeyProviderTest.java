package com.bulc.homepage.licensing.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DefaultSigningKeyProvider kid 도출 검증 (MDP-788 ㉯ 선행).
 *
 * 종전에는 실제 로드된 키와 무관한 상수("bulc-prod-v1")를 반환했다.
 * 이제 kid 는 로드된 키의 RFC 7638 thumbprint 에서 도출되어야 한다.
 */
class DefaultSigningKeyProviderTest {

    @Test
    @DisplayName("kid 는 로드된 키의 RFC 7638 thumbprint 에서 도출됨")
    void shouldDeriveKidFromLoadedKey() throws Exception {
        // given
        KeyPair keyPair = generateRsaKeyPair();
        DefaultSigningKeyProvider provider = providerFor(keyPair);

        // when
        provider.init();

        // then
        assertThat(provider.isEnabled()).isTrue();
        assertThat(provider.keyId())
                .isEqualTo(JwkThumbprint.of((RSAPublicKey) keyPair.getPublic()));
    }

    @Test
    @DisplayName("다른 키를 로드하면 다른 kid — 회전 시 신구 키 구분 성립")
    void shouldProduceDifferentKidForDifferentKeys() throws Exception {
        // given
        DefaultSigningKeyProvider provider1 = providerFor(generateRsaKeyPair());
        DefaultSigningKeyProvider provider2 = providerFor(generateRsaKeyPair());

        // when
        provider1.init();
        provider2.init();

        // then
        assertThat(provider1.keyId()).isNotEqualTo(provider2.keyId());
    }

    @Test
    @DisplayName("dev 환경에서 키 미설정 시 kid 는 null (상수 반환 금지)")
    void shouldReturnNullKidWhenKeyMissing() {
        // given
        DefaultSigningKeyProvider provider = new DefaultSigningKeyProvider("", "", "dev");

        // when
        provider.init();

        // then
        assertThat(provider.isEnabled()).isFalse();
        assertThat(provider.keyId()).isNull();
    }

    private DefaultSigningKeyProvider providerFor(KeyPair keyPair) {
        // PKCS#8 PEM 텍스트를 base64 인코딩 (LIC_PRIVATE_KEY_BASE64 주입 경로와 동일 형식)
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes())
                        .encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        String base64 = Base64.getEncoder().encodeToString(pem.getBytes());
        return new DefaultSigningKeyProvider(base64, "", "dev");
    }

    private KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
