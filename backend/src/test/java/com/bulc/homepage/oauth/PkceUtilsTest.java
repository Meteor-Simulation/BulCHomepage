package com.bulc.homepage.oauth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * PKCE 유틸리티 단위 테스트.
 *
 * RFC 7636 스펙 준수 여부를 검증합니다.
 */
class PkceUtilsTest {

    @Nested
    @DisplayName("generateCodeChallenge()")
    class GenerateCodeChallenge {

        @Test
        @DisplayName("code_verifier에서 SHA-256 code_challenge 생성")
        void shouldGenerateValidCodeChallenge() {
            // given - RFC 7636 Appendix B 예제 값
            String codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";

            // when
            String codeChallenge = PkceUtils.generateCodeChallenge(codeVerifier);

            // then - RFC 7636 예상 값
            assertThat(codeChallenge).isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
        }

        @Test
        @DisplayName("같은 code_verifier는 항상 같은 code_challenge 생성")
        void shouldGenerateConsistentCodeChallenge() {
            // given
            String codeVerifier = PkceUtils.generateCodeVerifier();

            // when
            String challenge1 = PkceUtils.generateCodeChallenge(codeVerifier);
            String challenge2 = PkceUtils.generateCodeChallenge(codeVerifier);

            // then
            assertThat(challenge1).isEqualTo(challenge2);
        }

        @Test
        @DisplayName("다른 code_verifier는 다른 code_challenge 생성")
        void shouldGenerateDifferentChallengesForDifferentVerifiers() {
            // given
            String verifier1 = PkceUtils.generateCodeVerifier();
            String verifier2 = PkceUtils.generateCodeVerifier();

            // when
            String challenge1 = PkceUtils.generateCodeChallenge(verifier1);
            String challenge2 = PkceUtils.generateCodeChallenge(verifier2);

            // then
            assertThat(challenge1).isNotEqualTo(challenge2);
        }

        @Test
        @DisplayName("code_challenge는 Base64 URL-safe 인코딩 (패딩 없음)")
        void shouldGenerateBase64UrlSafeWithoutPadding() {
            // given
            String codeVerifier = PkceUtils.generateCodeVerifier();

            // when
            String codeChallenge = PkceUtils.generateCodeChallenge(codeVerifier);

            // then
            assertThat(codeChallenge)
                    .doesNotContain("=")  // 패딩 없음
                    .doesNotContain("+")  // URL-safe (+ 대신 -)
                    .doesNotContain("/"); // URL-safe (/ 대신 _)
        }
    }

    @Nested
    @DisplayName("verifyCodeChallenge()")
    class VerifyCodeChallenge {

        @Test
        @DisplayName("S256 방식으로 올바른 code_verifier 검증 성공")
        void shouldVerifyValidCodeVerifierWithS256() {
            // given
            String codeVerifier = PkceUtils.generateCodeVerifier();
            String codeChallenge = PkceUtils.generateCodeChallenge(codeVerifier);

            // when
            boolean result = PkceUtils.verifyCodeChallenge(codeVerifier, codeChallenge, "S256");

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("S256 방식으로 잘못된 code_verifier 검증 실패")
        void shouldRejectInvalidCodeVerifierWithS256() {
            // given
            String codeVerifier = PkceUtils.generateCodeVerifier();
            String codeChallenge = PkceUtils.generateCodeChallenge(codeVerifier);
            String wrongVerifier = PkceUtils.generateCodeVerifier();

            // when
            boolean result = PkceUtils.verifyCodeChallenge(wrongVerifier, codeChallenge, "S256");

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("plain 방식으로 올바른 code_verifier 검증 성공")
        void shouldVerifyValidCodeVerifierWithPlain() {
            // given
            String codeVerifier = "my-plain-code-verifier-1234567890123456789012345";

            // when - plain 방식에서는 code_verifier == code_challenge
            boolean result = PkceUtils.verifyCodeChallenge(codeVerifier, codeVerifier, "plain");

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("plain 방식으로 잘못된 code_verifier 검증 실패")
        void shouldRejectInvalidCodeVerifierWithPlain() {
            // given
            String codeVerifier = "my-plain-code-verifier-1234567890123456789012345";
            String wrongVerifier = "wrong-code-verifier-1234567890123456789012345678";

            // when
            boolean result = PkceUtils.verifyCodeChallenge(wrongVerifier, codeVerifier, "plain");

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("null code_verifier는 검증 실패")
        void shouldRejectNullCodeVerifier() {
            // given
            String codeChallenge = PkceUtils.generateCodeChallenge("some-verifier-1234567890123456789012345");

            // when
            boolean result = PkceUtils.verifyCodeChallenge(null, codeChallenge, "S256");

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("null code_challenge는 검증 실패")
        void shouldRejectNullCodeChallenge() {
            // given
            String codeVerifier = PkceUtils.generateCodeVerifier();

            // when
            boolean result = PkceUtils.verifyCodeChallenge(codeVerifier, null, "S256");

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("generateCodeVerifier()")
    class GenerateCodeVerifier {

        @Test
        @DisplayName("생성된 code_verifier는 43자 이상")
        void shouldGenerateVerifierWithMinimumLength() {
            // when
            String codeVerifier = PkceUtils.generateCodeVerifier();

            // then - RFC 7636 최소 길이 43자
            assertThat(codeVerifier.length()).isGreaterThanOrEqualTo(43);
        }

        @Test
        @DisplayName("생성된 code_verifier는 128자 이하")
        void shouldGenerateVerifierWithMaximumLength() {
            // when
            String codeVerifier = PkceUtils.generateCodeVerifier();

            // then - RFC 7636 최대 길이 128자
            assertThat(codeVerifier.length()).isLessThanOrEqualTo(128);
        }

        @Test
        @DisplayName("생성된 code_verifier는 URL-safe 문자만 포함")
        void shouldGenerateUrlSafeVerifier() {
            // when
            String codeVerifier = PkceUtils.generateCodeVerifier();

            // then - Base64 URL-safe 문자만 포함
            assertThat(codeVerifier).matches("[A-Za-z0-9_-]+");
        }

        @Test
        @DisplayName("매번 다른 code_verifier 생성")
        void shouldGenerateUniqueVerifiers() {
            // when
            String verifier1 = PkceUtils.generateCodeVerifier();
            String verifier2 = PkceUtils.generateCodeVerifier();

            // then
            assertThat(verifier1).isNotEqualTo(verifier2);
        }
    }

    @Nested
    @DisplayName("isSupportedMethod()")
    class IsSupportedMethod {

        @Test
        @DisplayName("S256 지원")
        void shouldSupportS256() {
            assertThat(PkceUtils.isSupportedMethod("S256")).isTrue();
            assertThat(PkceUtils.isSupportedMethod("s256")).isTrue();
        }

        @Test
        @DisplayName("plain 지원")
        void shouldSupportPlain() {
            assertThat(PkceUtils.isSupportedMethod("plain")).isTrue();
            assertThat(PkceUtils.isSupportedMethod("PLAIN")).isTrue();
        }

        @Test
        @DisplayName("지원하지 않는 메서드")
        void shouldRejectUnsupportedMethods() {
            assertThat(PkceUtils.isSupportedMethod("SHA256")).isFalse();
            assertThat(PkceUtils.isSupportedMethod("unknown")).isFalse();
            assertThat(PkceUtils.isSupportedMethod(null)).isFalse();
        }
    }
}
