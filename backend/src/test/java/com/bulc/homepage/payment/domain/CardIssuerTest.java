package com.bulc.homepage.payment.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 토스 카드사 코드 변환 단위 테스트.
 *
 * <p>이 표가 틀리면 마이페이지에 엉뚱한 카드사가 찍히거나 빈칸이 된다. 실제로
 * 없어진 필드(card.company)를 읽다가 카드 4건 전부 카드사명이 비었던 사고가 있었다.
 */
class CardIssuerTest {

    @ParameterizedTest
    @CsvSource({
            "61, 현대",
            "41, 신한",
            "11, 국민",
            "51, 삼성",
            "31, BC",
            "71, 롯데",
            "21, 하나",
            "91, 농협",
            "15, 카카오뱅크",
            "24, 토스뱅크",
            "3A, 케이뱅크",
            "3K, 기업비씨",
            "4V, 비자",
            "4M, 마스터",
            "7A, AMEX"
    })
    @DisplayName("카드사 코드를 이름으로 바꾼다")
    void mapsKnownIssuerCodes(String code, String expected) {
        assertThat(CardIssuer.nameOf(code)).isEqualTo(expected);
    }

    @Test
    @DisplayName("우리카드는 매입사에 따라 코드가 둘이지만 이름은 같다")
    void bothWooriCodesResolve() {
        assertThat(CardIssuer.nameOf("33")).isEqualTo("우리");
        assertThat(CardIssuer.nameOf("W1")).isEqualTo("우리");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ZZ", "99", "0", "현대"})
    @DisplayName("표에 없는 코드는 이름을 만들어 내지 않고 null 을 준다")
    void unknownCodeYieldsNull(String code) {
        assertThat(CardIssuer.nameOf(code)).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("코드가 없으면 null 을 준다 (토스가 issuerCode 를 안 줄 때)")
    void missingCodeYieldsNull(String code) {
        assertThat(CardIssuer.nameOf(code)).isNull();
    }

    @Test
    @DisplayName("소문자·공백이 섞인 코드도 받아 준다")
    void normalizesInput() {
        assertThat(CardIssuer.nameOf(" 3a ")).isEqualTo("케이뱅크");
        assertThat(CardIssuer.nameOf("w1")).isEqualTo("우리");
    }

    @Test
    @DisplayName("코드가 중복되면 표가 깨진다 — 전 항목 유일성 검사")
    void codesAreUnique() {
        var codes = Arrays.stream(CardIssuer.values())
                .map(CardIssuer::getCode)
                .collect(Collectors.toList());

        assertThat(codes).doesNotHaveDuplicates();
        assertThat(codes).allSatisfy(code ->
                assertThat(code).matches("[0-9A-Z]{2}"));
    }

    @Test
    @DisplayName("모든 항목은 표시할 이름을 갖는다")
    void everyIssuerHasDisplayName() {
        assertThat(Arrays.stream(CardIssuer.values()).toList())
                .allSatisfy(issuer -> assertThat(issuer.getDisplayName()).isNotBlank());
    }
}
