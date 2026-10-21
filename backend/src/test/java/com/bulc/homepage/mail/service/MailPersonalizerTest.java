package com.bulc.homepage.mail.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 본문 자리표시자 치환 (MDP-1020).
 *
 * <p>관리자가 {@code {name}님 안녕하세요} 라고 쓰면 수신자마다 이름이 바뀌어 발송된다.
 */
@DisplayName("메일 본문 개인화")
class MailPersonalizerTest {

    @Nested
    @DisplayName("치환")
    class Replace {

        @Test
        @DisplayName("이름을 수신자 이름으로 바꾼다")
        void replacesName() {
            assertThat(MailPersonalizer.apply("<p>{name}님 안녕하세요.</p>", "강주원", null))
                    .isEqualTo("<p>강주원님 안녕하세요.</p>");
        }

        @Test
        @DisplayName("소속도 바꾼다")
        void replacesCompany() {
            assertThat(MailPersonalizer.apply("{company} {name}님", "강주원", "메테오시뮬레이션"))
                    .isEqualTo("메테오시뮬레이션 강주원님");
        }

        @Test
        @DisplayName("같은 자리표시자가 여러 번 나와도 모두 바꾼다")
        void replacesAllOccurrences() {
            assertThat(MailPersonalizer.apply("{name}님, {name}님께 드립니다", "강주원", null))
                    .isEqualTo("강주원님, 강주원님께 드립니다");
        }

        @Test
        @DisplayName("앞뒤 공백은 다듬는다")
        void trims() {
            assertThat(MailPersonalizer.apply("{name}님", "  강주원  ", null)).isEqualTo("강주원님");
        }
    }

    /**
     * 이름이 없는 수신자가 섞여 있다. 비워 두면 "님 안녕하세요" 가 되어 받는 사람이
     * 바로 알아챈다.
     */
    @Nested
    @DisplayName("값이 없을 때")
    class Fallback {

        @Test
        @DisplayName("이름이 없으면 '고객' 으로 대신한다")
        void nameFallback() {
            assertThat(MailPersonalizer.apply("{name}님 안녕하세요", null, null))
                    .isEqualTo("고객님 안녕하세요");
            assertThat(MailPersonalizer.apply("{name}님 안녕하세요", "   ", null))
                    .isEqualTo("고객님 안녕하세요");
        }

        /** 소속은 대신할 말이 없다. 가짜 소속을 지어내는 것보다 비우는 편이 낫다. */
        @Test
        @DisplayName("소속이 없으면 빈칸으로 둔다")
        void companyFallback() {
            assertThat(MailPersonalizer.apply("{company} 담당자님", "강주원", null))
                    .isEqualTo(" 담당자님");
        }
    }

    /**
     * 이름·소속은 QR 폼이나 파일 임포트로 들어온 외부 입력이고, 치환 결과는 HTML 메일
     * 본문에 그대로 박힌다. 이스케이프하지 않으면 이름에 넣은 태그가 수신자 메일함에서
     * 실행된다.
     */
    @Nested
    @DisplayName("이스케이프")
    class Escaping {

        @Test
        @DisplayName("이름에 든 태그는 글자로 바뀐다")
        void escapesTags() {
            String out = MailPersonalizer.apply("{name}님", "<script>alert(1)</script>", null);
            assertThat(out).doesNotContain("<script>");
            assertThat(out).isEqualTo("&lt;script&gt;alert(1)&lt;/script&gt;님");
        }

        @Test
        @DisplayName("따옴표도 바꾼다 — 속성 안에 들어갈 수 있다")
        void escapesQuotes() {
            assertThat(MailPersonalizer.apply("<a title='{name}'>", "a\"b'c", null))
                    .isEqualTo("<a title='a&quot;b&#39;c'>");
        }

        @Test
        @DisplayName("앰퍼샌드를 먼저 바꿔 이중 변환이 생기지 않는다")
        void escapesAmpersandOnce() {
            assertThat(MailPersonalizer.apply("{name}", "A&B", null)).isEqualTo("A&amp;B");
        }

        /** 본문 HTML 은 관리자가 쓴 것이라 그대로 둔다 — 끼워 넣는 값만 이스케이프한다. */
        @Test
        @DisplayName("본문의 태그는 건드리지 않는다")
        void keepsBodyHtml() {
            assertThat(MailPersonalizer.apply("<b>{name}</b>", "강주원", null))
                    .isEqualTo("<b>강주원</b>");
        }
    }

    @Nested
    @DisplayName("자리표시자 유무")
    class HasPlaceholder {

        @Test
        @DisplayName("있으면 true")
        void detects() {
            assertThat(MailPersonalizer.hasPlaceholder("<p>{name}님</p>")).isTrue();
            assertThat(MailPersonalizer.hasPlaceholder("<p>{company}</p>")).isTrue();
        }

        /** 없으면 본문이 모두에게 같으므로 수신자별로 다시 만들 이유가 없다. */
        @Test
        @DisplayName("없으면 false")
        void noPlaceholder() {
            assertThat(MailPersonalizer.hasPlaceholder("<p>안녕하세요</p>")).isFalse();
            assertThat(MailPersonalizer.hasPlaceholder(null)).isFalse();
        }

        @Test
        @DisplayName("모르는 자리표시자는 그대로 둔다")
        void unknownPlaceholderUntouched() {
            assertThat(MailPersonalizer.apply("{email} {name}", "강주원", null))
                    .isEqualTo("{email} 강주원");
        }
    }
}
