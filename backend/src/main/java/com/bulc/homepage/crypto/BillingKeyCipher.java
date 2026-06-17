package com.bulc.homepage.crypto;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 빌링키 at-rest 암호화 (AES-256-GCM).
 *
 * - 저장 시 암호화 / 로드 시 복호화 ({@link BillingKeyCryptoConverter}가 호출).
 * - 출력 포맷: "v1:" + base64(IV(12) || ciphertext || GCM-tag(16))  — 키 버전 prefix로 로테이션 대비.
 * - 키: 환경변수 BILLING_ENC_KEY (base64 32바이트 권장). 코드/깃에 두지 않는다.
 *   미설정 시 개발용 폴백 키 사용(운영 금지 — WARN). 운영은 반드시 BILLING_ENC_KEY 설정.
 *
 * 컨버터(JPA가 인스턴스화)에서 Spring 빈 주입이 안 되므로, startup에 static 인스턴스를 노출한다.
 */
@Slf4j
@Component
public class BillingKeyCipher {

    public static final String PREFIX = "v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;          // GCM 권장 96-bit
    private static final int TAG_LENGTH_BITS = 128;

    @Value("${billing.encryption.key:}")
    private String configuredKey;

    private SecretKeySpec keySpec;
    private final SecureRandom random = new SecureRandom();

    private static BillingKeyCipher instance;

    @PostConstruct
    void init() {
        byte[] key;
        if (configuredKey != null && !configuredKey.isBlank()) {
            key = normalizeKey(configuredKey);
        } else {
            log.warn("[보안] billing.encryption.key(BILLING_ENC_KEY) 미설정 — 개발용 폴백 키 사용 중. "
                    + "운영에서는 반드시 BILLING_ENC_KEY 환경변수를 설정해야 합니다!");
            key = sha256("dev-only-insecure-billing-key-do-not-use-in-prod");
        }
        this.keySpec = new SecretKeySpec(key, "AES");
        instance = this;
    }

    public static BillingKeyCipher getInstance() {
        return instance;
    }

    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("빌링키 암호화 실패", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        // 레거시 평문(마이그레이션 전) 또는 비암호화 값은 그대로 반환
        if (!stored.startsWith(PREFIX)) {
            return stored;
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(all, 0, iv, 0, IV_LENGTH);
            byte[] ct = new byte[all.length - IV_LENGTH];
            System.arraycopy(all, IV_LENGTH, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("빌링키 복호화 실패", e);
        }
    }

    public boolean isEncrypted(String v) {
        return v != null && v.startsWith(PREFIX);
    }

    /** base64 32바이트 키를 우선 사용, 형식이 안 맞으면 SHA-256으로 32바이트 파생. */
    private byte[] normalizeKey(String k) {
        try {
            byte[] decoded = Base64.getDecoder().decode(k.trim());
            if (decoded.length == 32) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // base64 아님 → 아래 SHA-256 파생
        }
        return sha256(k);
    }

    private byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("키 파생 실패", e);
        }
    }
}
