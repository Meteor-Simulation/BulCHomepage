package com.bulc.homepage.licensing.service;

import com.bulc.homepage.licensing.exception.LicenseException;
import com.bulc.homepage.licensing.exception.LicenseException.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.HexFormat;

@Service
public class RedeemCodeHashService {

    private static final String ALLOWED_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int RANDOM_CODE_LENGTH = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final String pepper;

    public RedeemCodeHashService(@Value("${bulc.redeem.code-pepper:dev-redeem-pepper-2024}") String pepper) {
        this.pepper = pepper;
    }

    public String normalize(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new LicenseException(ErrorCode.REDEEM_CODE_INVALID);
        }
        String trimmed = rawCode.trim();
        String nfkc = Normalizer.normalize(trimmed, Normalizer.Form.NFKC);
        String upper = nfkc.toUpperCase();
        return upper.replaceAll("[\\s\\-_]", "");
    }

    public void validate(String normalizedCode) {
        if (normalizedCode.length() < 8 || normalizedCode.length() > 64) {
            throw new LicenseException(ErrorCode.REDEEM_CODE_INVALID,
                    "코드는 8~64자 사이여야 합니다");
        }
        if (!normalizedCode.matches("^[A-Z0-9]+$")) {
            throw new LicenseException(ErrorCode.REDEEM_CODE_INVALID,
                    "코드는 영문 대문자와 숫자만 포함해야 합니다");
        }
    }

    public String hash(String normalizedCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = pepper + ":" + normalizedCode;
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 알고리즘을 사용할 수 없습니다", e);
        }
    }

    public String generateRandomCode() {
        StringBuilder sb = new StringBuilder(RANDOM_CODE_LENGTH);
        for (int i = 0; i < RANDOM_CODE_LENGTH; i++) {
            sb.append(ALLOWED_CHARS.charAt(SECURE_RANDOM.nextInt(ALLOWED_CHARS.length())));
        }
        return sb.toString();
    }

    public String formatForDisplay(String rawCode) {
        if (rawCode.length() != RANDOM_CODE_LENGTH) {
            return rawCode;
        }
        return String.format("%s-%s-%s-%s",
                rawCode.substring(0, 4),
                rawCode.substring(4, 8),
                rawCode.substring(8, 12),
                rawCode.substring(12, 16));
    }
}
