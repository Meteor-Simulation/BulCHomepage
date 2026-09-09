package com.bulc.homepage.licensing.controller;

import com.bulc.homepage.licensing.service.JwkThumbprint;
import com.bulc.homepage.licensing.service.SigningKeyProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Map;

/**
 * 진단·CI 대조용 공개키 엔드포인트 (MDP-788 ㉮ · 계약 v1.2.0 §2).
 *
 * <p>현재 토큰 서명에 사용 중인 RSA 공개키를 평문으로 노출합니다.
 * 소비자는 CI 키 정합 대조(내장 상수 ↔ 운영 공개키)와 운영 공개키 식별이며,
 * <b>클라이언트의 토큰 검증 신뢰 경로가 아닙니다</b> — 검증 키는 클라이언트에
 * 내장(고정)하는 것이 정본이다 (원 스펙 bulc_auth_module_spec_v0.4.md §8.1,
 * 런타임 키 로드는 키 스왑 우회 사유로 금지).</p>
 *
 * <p>공개키는 비밀이 아니므로 인증 없이 접근 가능합니다.</p>
 */
@RestController
@RequestMapping("/api/v1/licensing")
@RequiredArgsConstructor
public class LicenseKeyController {

    private final SigningKeyProvider keyProvider;

    /**
     * 현재 서명 키의 공개키 조회.
     *
     * GET /api/v1/licensing/public-key
     *
     * 응답 (200):
     * - kid: 토큰 header.kid 와 동일한 키 식별자 (프로덕션 = RFC 7638 JWK thumbprint)
     * - alg / kty / use: RS256 / RSA / sig
     * - n / e: JWK 형식 (RFC 7518 Base64urlUInt) — 내장 modulus 상수와의 대조용
     * - pem: X.509 SubjectPublicKeyInfo PEM — openssl 등 표준 도구 검사용
     *
     * 응답 (503): 서명 키 미설정 (dev 환경에서 키 없이 기동한 경우)
     */
    @GetMapping("/public-key")
    public ResponseEntity<?> publicKey() {
        if (!keyProvider.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "error", "KEY_NOT_CONFIGURED",
                            "message", "RS256 서명 키가 설정되지 않아 공개키를 제공할 수 없습니다"
                    ));
        }

        RSAPublicKey publicKey = (RSAPublicKey) keyProvider.verifyKey();
        return ResponseEntity.ok(Map.of(
                "kid", keyProvider.keyId(),
                "alg", "RS256",
                "kty", "RSA",
                "use", "sig",
                "n", JwkThumbprint.base64UrlUInt(publicKey.getModulus()),
                "e", JwkThumbprint.base64UrlUInt(publicKey.getPublicExponent()),
                "pem", toPem(publicKey)
        ));
    }

    /**
     * X.509 SubjectPublicKeyInfo PEM 인코딩.
     */
    private String toPem(RSAPublicKey publicKey) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----\n";
    }
}
