package com.bulc.homepage.licensing.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/**
 * 프로덕션용 SigningKeyProvider 구현체.
 *
 * 환경변수 또는 application.yml에서 RSA 개인키를 로드합니다.
 * 개인키에서 공개키를 자동으로 추출합니다.
 *
 * 보안 정책:
 * - prod 프로필: 키 미설정 시 서버 부팅 실패 (fail-fast)
 * - dev 프로필: 키 미설정 시 경고 로그, 토큰 발급 비활성화
 * - keyId가 "test"로 시작하면 prod에서 거부
 */
@Slf4j
@Component
public class DefaultSigningKeyProvider implements SigningKeyProvider {

    private static final String PROD_KEY_ID = "bulc-prod-v1";

    private final String privateKeyBase64;
    private final String activeProfile;

    private PrivateKey rsaPrivateKey;
    private PublicKey rsaPublicKey;

    public DefaultSigningKeyProvider(
            @Value("${bulc.licensing.session-token.private-key:}") String privateKeyBase64,
            @Value("${spring.profiles.active:dev}") String activeProfile) {
        this.privateKeyBase64 = privateKeyBase64;
        this.activeProfile = activeProfile;
    }

    @PostConstruct
    public void init() {
        boolean isProd = activeProfile.contains("prod");

        if (privateKeyBase64 == null || privateKeyBase64.isBlank()) {
            if (isProd) {
                throw new IllegalStateException(
                    "[FATAL] SigningKeyProvider: RS256 개인키가 설정되지 않았습니다. " +
                    "prod 환경에서는 SESSION_TOKEN_PRIVATE_KEY 환경변수가 필수입니다. " +
                    "서버를 시작할 수 없습니다."
                );
            } else {
                log.warn("========================================");
                log.warn("SigningKeyProvider: RS256 개인키가 설정되지 않았습니다.");
                log.warn("sessionToken/offlineToken 발급이 비활성화됩니다.");
                log.warn("개발 환경에서도 RS256 키 설정을 권장합니다.");
                log.warn("키 생성: openssl genrsa -out private_key.pem 2048");
                log.warn("========================================");
                this.rsaPrivateKey = null;
                this.rsaPublicKey = null;
                return;
            }
        }

        try {
            this.rsaPrivateKey = loadPrivateKey(privateKeyBase64);
            this.rsaPublicKey = derivePublicKey(this.rsaPrivateKey);
            log.info("SigningKeyProvider: RS256 키 로드 성공 (keyId: {})", keyId());
        } catch (Exception e) {
            if (isProd) {
                throw new IllegalStateException(
                    "[FATAL] SigningKeyProvider: RS256 개인키 로드 실패. " +
                    "키 형식을 확인하세요 (PKCS#8 PEM, Base64 인코딩). 에러: " + e.getMessage(), e
                );
            } else {
                log.error("SigningKeyProvider: RS256 개인키 로드 실패. 토큰 발급이 비활성화됩니다.", e);
                this.rsaPrivateKey = null;
                this.rsaPublicKey = null;
            }
        }
    }

    @Override
    public PrivateKey signingKey() {
        return rsaPrivateKey;
    }

    @Override
    public PublicKey verifyKey() {
        return rsaPublicKey;
    }

    @Override
    public String keyId() {
        return PROD_KEY_ID;
    }

    @Override
    public boolean isEnabled() {
        return rsaPrivateKey != null && rsaPublicKey != null;
    }

    /**
     * Base64 인코딩된 PKCS#8 개인키 로드.
     *
     * 지원 형식:
     * - PEM 형식 (-----BEGIN PRIVATE KEY----- 헤더/푸터 포함)
     * - Base64 인코딩된 DER
     */
    private PrivateKey loadPrivateKey(String keyData) throws Exception {
        String cleanedKey = keyData
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(cleanedKey);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }

    /**
     * RSA 개인키에서 공개키 추출.
     */
    private PublicKey derivePublicKey(PrivateKey privateKey) throws Exception {
        if (privateKey instanceof RSAPrivateCrtKey rsaKey) {
            RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(
                    rsaKey.getModulus(),
                    rsaKey.getPublicExponent()
            );
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(publicKeySpec);
        }
        throw new IllegalArgumentException("지원되지 않는 키 타입: " + privateKey.getClass().getName());
    }
}
