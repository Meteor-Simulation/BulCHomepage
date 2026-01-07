package com.bulc.homepage.licensing.config;

import com.bulc.homepage.licensing.service.SigningKeyProvider;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * 테스트 전용 SigningKeyProvider 구현체.
 *
 * 런타임에 RSA 키 쌍을 생성합니다.
 * 레포지토리에 어떤 키도 커밋하지 않고 테스트할 수 있습니다.
 *
 * 보안 정책:
 * - keyId가 "test-"로 시작하여 프로덕션에서 거부 가능
 * - 매 인스턴스마다 새 키 생성 (키 유출 방지)
 */
public class TestSigningKeyProvider implements SigningKeyProvider {

    private static final String TEST_KEY_ID = "test-runtime-generated";

    private final KeyPair keyPair;

    public TestSigningKeyProvider() {
        this.keyPair = generateKeyPair();
    }

    /**
     * 커스텀 keyId로 생성 (특정 테스트 시나리오용).
     */
    public TestSigningKeyProvider(String customKeyId) {
        this.keyPair = generateKeyPair();
        // customKeyId는 현재 사용하지 않지만 확장 가능성을 위해 유지
    }

    @Override
    public PrivateKey signingKey() {
        return keyPair.getPrivate();
    }

    @Override
    public PublicKey verifyKey() {
        return keyPair.getPublic();
    }

    @Override
    public String keyId() {
        return TEST_KEY_ID;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("RSA 키 생성 실패", e);
        }
    }
}
