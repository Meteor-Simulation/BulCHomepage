package com.bulc.homepage.licensing.service;

import com.bulc.homepage.licensing.config.TestKeyConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SessionTokenService 통합 테스트.
 *
 * 보안 개선: TestKeyConfig로 런타임 생성 키 사용.
 * 레포지토리에 private key가 커밋되지 않습니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestKeyConfig.class)
class SessionTokenServiceIntegrationTest {

    @Autowired
    private SessionTokenService sessionTokenService;

    @Test
    void testSessionTokenGeneration() {
        // Given
        UUID licenseId = UUID.randomUUID();
        String productCode = "BULC_EVAC";
        String deviceFingerprint = "test-device-123";
        String[] entitlements = {"FEATURE_A", "FEATURE_B"};

        // When
        SessionTokenService.SessionToken sessionToken = sessionTokenService.generateSessionToken(
                licenseId, productCode, deviceFingerprint, Arrays.asList(entitlements)
        );

        // Then
        assertThat(sessionToken).isNotNull();
        assertThat(sessionToken.token()).isNotNull();
        assertThat(sessionToken.token()).isNotEmpty();

        // JWT 형식 검증 (header.payload.signature)
        String[] parts = sessionToken.token().split("\\.");
        assertThat(parts).hasSize(3);

        System.out.println("SessionToken generated successfully!");
        System.out.println("Token length: " + sessionToken.token().length());
        System.out.println("Token preview: " + sessionToken.token().substring(0, Math.min(50, sessionToken.token().length())) + "...");
    }

    @Test
    void testSessionTokenServiceInitialization() {
        // SessionTokenService가 정상적으로 초기화되었는지 확인
        assertThat(sessionTokenService).isNotNull();
        assertThat(sessionTokenService.isEnabled()).isTrue();
        System.out.println("SessionTokenService initialized successfully!");
    }

    @Test
    void testKeyIdIsTestKey() {
        // keyId가 test-로 시작하여 프로덕션 키와 구분됨
        assertThat(sessionTokenService.getKeyId()).startsWith("test-");
        System.out.println("KeyId: " + sessionTokenService.getKeyId());
    }

    @Test
    void testSessionTokenDecoding() {
        // Given
        UUID licenseId = UUID.randomUUID();
        String productCode = "BULC_EVAC";
        String deviceFingerprint = "test-device-456";
        String[] entitlements = {"FEATURE_X", "FEATURE_Y", "FEATURE_Z"};

        // When
        SessionTokenService.SessionToken sessionToken = sessionTokenService.generateSessionToken(
                licenseId, productCode, deviceFingerprint, Arrays.asList(entitlements)
        );

        // Then
        assertThat(sessionToken).isNotNull();
        assertThat(sessionToken.token()).isNotNull();

        // JWT 구조 확인 (header.payload.signature)
        String[] parts = sessionToken.token().split("\\.");
        assertThat(parts).hasSize(3);

        // Header 디코딩 - kid 확인
        String header = new String(Base64.getUrlDecoder().decode(parts[0]));
        assertThat(header).contains("\"kid\":\"test-");

        // Payload 디코딩 (Base64)
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        System.out.println("SessionToken decoded successfully!");
        System.out.println("Header: " + header);
        System.out.println("Payload: " + payload);

        // 주요 클레임 검증
        assertThat(payload).contains("\"iss\":\"bulc-license-server\"");
        assertThat(payload).contains("\"aud\":[\"BULC_EVAC\"]");
        assertThat(payload).contains("\"sub\":\"" + licenseId.toString() + "\"");
        assertThat(payload).contains("\"dfp\":\"test-device-456\"");
        assertThat(payload).contains("\"ent\":[\"FEATURE_X\",\"FEATURE_Y\",\"FEATURE_Z\"]");
        assertThat(payload).contains("\"iat\":");
        assertThat(payload).contains("\"exp\":");

        System.out.println("All JWT claims verified!");
    }
}
