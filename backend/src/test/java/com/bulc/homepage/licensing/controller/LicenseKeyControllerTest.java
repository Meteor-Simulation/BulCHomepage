package com.bulc.homepage.licensing.controller;

import com.bulc.homepage.licensing.config.TestKeyConfig;
import com.bulc.homepage.licensing.service.JwkThumbprint;
import com.bulc.homepage.licensing.service.SigningKeyProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 진단·CI 대조용 공개키 엔드포인트 테스트 (MDP-788 ㉮).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestKeyConfig.class)
class LicenseKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SigningKeyProvider keyProvider;

    @Test
    @DisplayName("인증 없이 공개키 조회 - kid/n/e 가 현재 서명 키와 일치")
    void shouldExposeCurrentSigningPublicKeyWithoutAuth() throws Exception {
        RSAPublicKey expected = (RSAPublicKey) keyProvider.verifyKey();

        mockMvc.perform(get("/api/v1/licensing/public-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kid").value(keyProvider.keyId()))
                .andExpect(jsonPath("$.alg").value("RS256"))
                .andExpect(jsonPath("$.kty").value("RSA"))
                .andExpect(jsonPath("$.use").value("sig"))
                .andExpect(jsonPath("$.n").value(JwkThumbprint.base64UrlUInt(expected.getModulus())))
                .andExpect(jsonPath("$.e").value(JwkThumbprint.base64UrlUInt(expected.getPublicExponent())))
                .andExpect(jsonPath("$.pem").value(containsString("-----BEGIN PUBLIC KEY-----")));
    }

    @Test
    @DisplayName("서명 키 미설정 시 503 KEY_NOT_CONFIGURED")
    void shouldReturn503WhenKeyNotConfigured() {
        LicenseKeyController controller = new LicenseKeyController(new SigningKeyProvider() {
            @Override public PrivateKey signingKey() { return null; }
            @Override public PublicKey verifyKey() { return null; }
            @Override public String keyId() { return null; }
        });

        ResponseEntity<?> response = controller.publicKey();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().toString()).contains("KEY_NOT_CONFIGURED");
    }
}
