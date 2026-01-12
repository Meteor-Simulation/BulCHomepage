package com.bulc.homepage.licensing.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * 파일 경로에서 RSA 개인키를 로드합니다.
 * 개인키에서 공개키를 자동으로 추출합니다.
 *
 * 환경변수:
 * - LIC_PRIVATE_KEY_PATH: RSA 개인키 PEM 파일 경로 (필수)
 *
 * 보안 정책:
 * - prod 프로필: 키 파일 미설정 시 서버 부팅 실패 (fail-fast)
 * - dev 프로필: 키 파일 미설정 시 경고 로그, 토큰 발급 비활성화
 */
@Slf4j
@Component
public class DefaultSigningKeyProvider implements SigningKeyProvider {

    private static final String PROD_KEY_ID = "bulc-prod-v1";

    private final String privateKeyPath;
    private final String activeProfile;

    private PrivateKey rsaPrivateKey;
    private PublicKey rsaPublicKey;

    public DefaultSigningKeyProvider(
            @Value("${bulc.licensing.private-key-path:}") String privateKeyPath,
            @Value("${spring.profiles.active:dev}") String activeProfile) {
        this.privateKeyPath = privateKeyPath;
        this.activeProfile = activeProfile;
    }

    @PostConstruct
    public void init() {
        boolean isProd = activeProfile.contains("prod");

        if (privateKeyPath == null || privateKeyPath.isBlank()) {
            if (isProd) {
                throw new IllegalStateException(
                    "[FATAL] SigningKeyProvider: RS256 개인키 경로가 설정되지 않았습니다. " +
                    "prod 환경에서는 LIC_PRIVATE_KEY_PATH 환경변수가 필수입니다. " +
                    "서버를 시작할 수 없습니다."
                );
            } else {
                log.warn("========================================");
                log.warn("SigningKeyProvider: RS256 개인키 경로가 설정되지 않았습니다.");
                log.warn("sessionToken/offlineToken 발급이 비활성화됩니다.");
                log.warn("개발 환경에서도 RS256 키 설정을 권장합니다.");
                log.warn("키 생성: openssl genpkey -algorithm RSA -out secrets/session_token_private_key.pem -pkeyopt rsa_keygen_bits:2048");
                log.warn("========================================");
                this.rsaPrivateKey = null;
                this.rsaPublicKey = null;
                return;
            }
        }

        try {
            String pemContent = readKeyFile(privateKeyPath);
            this.rsaPrivateKey = loadPrivateKey(pemContent);
            this.rsaPublicKey = derivePublicKey(this.rsaPrivateKey);
            log.info("SigningKeyProvider: RS256 키 로드 성공 (keyId: {}, path: {})", keyId(), privateKeyPath);
        } catch (IOException e) {
            String errorMsg = String.format("키 파일을 읽을 수 없습니다: %s", privateKeyPath);
            if (isProd) {
                throw new IllegalStateException("[FATAL] SigningKeyProvider: " + errorMsg, e);
            } else {
                log.error("SigningKeyProvider: {}. 토큰 발급이 비활성화됩니다.", errorMsg, e);
                this.rsaPrivateKey = null;
                this.rsaPublicKey = null;
            }
        } catch (Exception e) {
            String errorMsg = "RS256 개인키 로드 실패. 키 형식을 확인하세요 (PKCS#8 PEM)";
            if (isProd) {
                throw new IllegalStateException("[FATAL] SigningKeyProvider: " + errorMsg + ". 에러: " + e.getMessage(), e);
            } else {
                log.error("SigningKeyProvider: {}. 토큰 발급이 비활성화됩니다.", errorMsg, e);
                this.rsaPrivateKey = null;
                this.rsaPublicKey = null;
            }
        }
    }

    /**
     * 키 파일에서 PEM 내용 읽기.
     */
    private String readKeyFile(String keyPath) throws IOException {
        Path path = Path.of(keyPath);
        if (!Files.exists(path)) {
            throw new IOException("키 파일이 존재하지 않습니다: " + keyPath);
        }
        return Files.readString(path);
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
