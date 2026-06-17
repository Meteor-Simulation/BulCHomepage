package com.bulc.homepage.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 빌링키 AES-256-GCM 암복호화 단위 테스트.
 */
class BillingKeyCipherTest {

    private BillingKeyCipher cipher;

    @BeforeEach
    void setUp() {
        cipher = new BillingKeyCipher();
        // base64 32바이트가 아니면 SHA-256으로 키 파생 → 어떤 문자열이든 OK
        ReflectionTestUtils.setField(cipher, "configuredKey", "unit-test-billing-encryption-key");
        ReflectionTestUtils.invokeMethod(cipher, "init");
    }

    @Test
    @DisplayName("암호화 → 복호화 라운드트립")
    void encryptThenDecrypt_roundTrips() {
        String plain = "billing_OmthZ9l0_xqSabc123XYZ";

        String enc = cipher.encrypt(plain);

        assertThat(enc).startsWith("v1:");
        assertThat(enc).isNotEqualTo(plain);
        assertThat(cipher.decrypt(enc)).isEqualTo(plain);
        assertThat(cipher.isEncrypted(enc)).isTrue();
    }

    @Test
    @DisplayName("레거시 평문(prefix 없음)은 복호화 시 그대로 통과")
    void decrypt_legacyPlaintext_passesThrough() {
        String legacy = "raw_plaintext_billing_key";

        assertThat(cipher.decrypt(legacy)).isEqualTo(legacy);
        assertThat(cipher.isEncrypted(legacy)).isFalse();
    }

    @Test
    @DisplayName("매 암호화는 랜덤 IV로 서로 다른 ciphertext 생성 (복호화는 동일)")
    void encrypt_usesRandomIv() {
        String plain = "same-billing-key";

        String a = cipher.encrypt(plain);
        String b = cipher.encrypt(plain);

        assertThat(a).isNotEqualTo(b);
        assertThat(cipher.decrypt(a)).isEqualTo(plain);
        assertThat(cipher.decrypt(b)).isEqualTo(plain);
    }

    @Test
    @DisplayName("null 안전")
    void nullSafe() {
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
    }
}
