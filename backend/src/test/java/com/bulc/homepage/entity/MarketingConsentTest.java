package com.bulc.homepage.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 수신 상태 코드 판정 (MDP-772).
 *
 * <p>이 값은 광고성 메일 발송 대상을 가르므로, 거절(N)이 발송 대상에 섞이지 않는 것과
 * 거절이 "답한 상태"로 인정되는 것(팝업 재노출 방지)이 핵심이다.
 */
class MarketingConsentTest {

    @Nested
    @DisplayName("발송 대상 판정")
    class CanReceive {

        @Test
        @DisplayName("동의(Y)만 발송 대상이다")
        void onlyAgreedCanReceive() {
            assertThat(MarketingConsent.canReceive(MarketingConsent.AGREED)).isTrue();
            assertThat(MarketingConsent.canReceive(MarketingConsent.DECLINED)).isFalse();
            assertThat(MarketingConsent.canReceive(MarketingConsent.PENDING)).isFalse();
        }

        @Test
        @DisplayName("null·미지의 값은 발송 대상이 아니다 (기본 거절)")
        void unknownIsNotReceivable() {
            assertThat(MarketingConsent.canReceive(null)).isFalse();
            assertThat(MarketingConsent.canReceive("")).isFalse();
            assertThat(MarketingConsent.canReceive("A")).isFalse();
            assertThat(MarketingConsent.canReceive("y")).isFalse();  // 소문자도 대상 아님
        }
    }

    @Nested
    @DisplayName("응답 여부 판정 — 동의 팝업 재노출 방지")
    class IsDecided {

        @Test
        @DisplayName("거절(N)도 답한 것으로 본다")
        void declinedCountsAsDecided() {
            // 이 한 줄이 MDP-772 의 핵심: 거절이 미선택으로 취급되면 팝업이 계속 뜬다
            assertThat(MarketingConsent.isDecided(MarketingConsent.DECLINED)).isTrue();
        }

        @Test
        @DisplayName("동의(Y)도 답한 것이다")
        void agreedCountsAsDecided() {
            assertThat(MarketingConsent.isDecided(MarketingConsent.AGREED)).isTrue();
        }

        @Test
        @DisplayName("미선택(P)만 아직 답하지 않은 상태다")
        void onlyPendingIsUndecided() {
            assertThat(MarketingConsent.isDecided(MarketingConsent.PENDING)).isFalse();
            assertThat(MarketingConsent.isDecided(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("boolean 변환")
    class From {

        @Test
        @DisplayName("동의 여부를 Y/N 으로 바꾼다 — 거절이 P 로 떨어지지 않는다")
        void mapsBooleanToCode() {
            assertThat(MarketingConsent.from(true)).isEqualTo(MarketingConsent.AGREED);
            assertThat(MarketingConsent.from(false)).isEqualTo(MarketingConsent.DECLINED);
            assertThat(MarketingConsent.from(false)).isNotEqualTo(MarketingConsent.PENDING);
        }
    }

    @Test
    @DisplayName("세 코드는 서로 다른 한 글자다 (컬럼이 CHAR(1))")
    void codesAreDistinctSingleChars() {
        assertThat(MarketingConsent.AGREED).hasSize(1);
        assertThat(MarketingConsent.DECLINED).hasSize(1);
        assertThat(MarketingConsent.PENDING).hasSize(1);
        assertThat(MarketingConsent.AGREED)
                .isNotEqualTo(MarketingConsent.DECLINED)
                .isNotEqualTo(MarketingConsent.PENDING);
        assertThat(MarketingConsent.DECLINED).isNotEqualTo(MarketingConsent.PENDING);
    }

    @Test
    @DisplayName("신규 회원 기본값은 미선택(P) — 팝업으로 한 번 물어본다")
    void newUserDefaultsToPending() {
        User user = User.builder().email("new@example.com").build();
        assertThat(user.getMarketingConsent()).isEqualTo(MarketingConsent.PENDING);
        assertThat(MarketingConsent.isDecided(user.getMarketingConsent())).isFalse();
    }
}
