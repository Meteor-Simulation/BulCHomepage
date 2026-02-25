package com.bulc.homepage.licensing.service;

import com.bulc.homepage.licensing.exception.LicenseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class RedeemCodeHashServiceTest {

    private RedeemCodeHashService hashService;

    @BeforeEach
    void setUp() {
        hashService = new RedeemCodeHashService("test-pepper");
    }

    @Nested
    @DisplayName("normalize()")
    class Normalize {

        @Test
        @DisplayName("공백, 하이픈, 언더스코어 제거")
        void removesWhitespaceHyphensUnderscores() {
            String result = hashService.normalize("ABCD-EFGH-1234-5678");
            assertThat(result).isEqualTo("ABCDEFGH12345678");
        }

        @Test
        @DisplayName("소문자를 대문자로 변환")
        void convertsToUpperCase() {
            String result = hashService.normalize("abcd1234");
            assertThat(result).isEqualTo("ABCD1234");
        }

        @Test
        @DisplayName("앞뒤 공백 제거")
        void trimsWhitespace() {
            String result = hashService.normalize("  ABCD1234  ");
            assertThat(result).isEqualTo("ABCD1234");
        }

        @Test
        @DisplayName("빈 문자열은 예외 발생")
        void emptyString_throwsException() {
            assertThatThrownBy(() -> hashService.normalize(""))
                    .isInstanceOf(LicenseException.class);
        }

        @Test
        @DisplayName("null은 예외 발생")
        void nullString_throwsException() {
            assertThatThrownBy(() -> hashService.normalize(null))
                    .isInstanceOf(LicenseException.class);
        }
    }

    @Nested
    @DisplayName("validate()")
    class Validate {

        @Test
        @DisplayName("유효한 코드 통과")
        void validCode_passes() {
            assertThatCode(() -> hashService.validate("ABCDEFGH12345678"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("8자 미만이면 예외 발생")
        void tooShort_throwsException() {
            assertThatThrownBy(() -> hashService.validate("ABCD123"))
                    .isInstanceOf(LicenseException.class);
        }

        @Test
        @DisplayName("64자 초과이면 예외 발생")
        void tooLong_throwsException() {
            String longCode = "A".repeat(65);
            assertThatThrownBy(() -> hashService.validate(longCode))
                    .isInstanceOf(LicenseException.class);
        }

        @Test
        @DisplayName("특수문자 포함시 예외 발생")
        void specialChars_throwsException() {
            assertThatThrownBy(() -> hashService.validate("ABCD!@#$1234"))
                    .isInstanceOf(LicenseException.class);
        }
    }

    @Nested
    @DisplayName("hash()")
    class Hash {

        @Test
        @DisplayName("동일 입력은 동일 해시")
        void sameInput_sameHash() {
            String hash1 = hashService.hash("ABCDEFGH12345678");
            String hash2 = hashService.hash("ABCDEFGH12345678");
            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        @DisplayName("다른 입력은 다른 해시")
        void differentInput_differentHash() {
            String hash1 = hashService.hash("ABCDEFGH12345678");
            String hash2 = hashService.hash("ABCDEFGH12345679");
            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("SHA-256 해시는 64자 hex")
        void hash_is64CharHex() {
            String hash = hashService.hash("ABCDEFGH12345678");
            assertThat(hash).hasSize(64);
            assertThat(hash).matches("^[0-9a-f]{64}$");
        }

        @Test
        @DisplayName("다른 pepper는 다른 해시")
        void differentPepper_differentHash() {
            RedeemCodeHashService otherService = new RedeemCodeHashService("other-pepper");
            String hash1 = hashService.hash("ABCDEFGH12345678");
            String hash2 = otherService.hash("ABCDEFGH12345678");
            assertThat(hash1).isNotEqualTo(hash2);
        }
    }

    @Nested
    @DisplayName("generateRandomCode()")
    class GenerateRandomCode {

        @Test
        @DisplayName("16자 영숫자 코드 생성")
        void generates16CharAlphanumericCode() {
            String code = hashService.generateRandomCode();
            assertThat(code).hasSize(16);
            assertThat(code).matches("^[A-Z0-9]{16}$");
        }

        @Test
        @DisplayName("매번 다른 코드 생성")
        void generatesDifferentCodes() {
            String code1 = hashService.generateRandomCode();
            String code2 = hashService.generateRandomCode();
            assertThat(code1).isNotEqualTo(code2);
        }
    }

    @Nested
    @DisplayName("formatForDisplay()")
    class FormatForDisplay {

        @Test
        @DisplayName("16자 코드를 XXXX-XXXX-XXXX-XXXX 포맷으로")
        void formats16CharCode() {
            String formatted = hashService.formatForDisplay("ABCDEFGH12345678");
            assertThat(formatted).isEqualTo("ABCD-EFGH-1234-5678");
        }

        @Test
        @DisplayName("16자가 아닌 코드는 그대로 반환")
        void nonSixteenCharCode_returnsAsIs() {
            String formatted = hashService.formatForDisplay("CUSTOM123");
            assertThat(formatted).isEqualTo("CUSTOM123");
        }
    }
}
