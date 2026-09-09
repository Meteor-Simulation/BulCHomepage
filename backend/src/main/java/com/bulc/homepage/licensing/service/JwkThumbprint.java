package com.bulc.homepage.licensing.service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

/**
 * RFC 7638 JWK Thumbprint 계산 유틸리티.
 *
 * <p>RSA 공개키의 필수 JWK 멤버({@code e}, {@code kty}, {@code n})를
 * 사전순으로 나열한 정규 JSON을 SHA-256 해시하여 base64url(패딩 없음)로 인코딩합니다.
 * 결과는 키 자체에서 결정되므로, 클라이언트·CI가 서버와의 조율 없이
 * 공개키만으로 동일한 kid 를 독립 산출할 수 있습니다.</p>
 */
public final class JwkThumbprint {

    private JwkThumbprint() {
    }

    /**
     * RSA 공개키의 RFC 7638 SHA-256 thumbprint.
     *
     * @param key RSA 공개키
     * @return base64url 인코딩된 thumbprint (43자, 패딩 없음)
     */
    public static String of(RSAPublicKey key) {
        String n = base64UrlUInt(key.getModulus());
        String e = base64UrlUInt(key.getPublicExponent());
        // RFC 7638 §3: 필수 멤버만, 사전순, 공백 없는 JSON
        String canonicalJson = "{\"e\":\"" + e + "\",\"kty\":\"RSA\",\"n\":\"" + n + "\"}";
        byte[] hash = sha256(canonicalJson.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    /**
     * RFC 7518 §2 Base64urlUInt: 부호 없는 최소 길이 big-endian 옥텟 표현.
     * BigInteger.toByteArray()의 선행 부호 바이트(0x00)를 제거합니다.
     */
    public static String base64UrlUInt(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] stripped = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, stripped, 0, stripped.length);
            bytes = stripped;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 미지원 JVM", e);
        }
    }
}
