package com.bulc.homepage.mail.service;

/**
 * 메일 본문의 자리표시자를 수신자 정보로 바꾼다 (MDP-1020).
 *
 * <p>관리자가 본문에 {@code {name}님 안녕하세요} 처럼 적으면 수신자마다
 * {@code 강주원님 안녕하세요} 로 치환되어 각각 발송된다.
 *
 * <h3>값이 비었을 때</h3>
 * 이름이 없는 수신자가 섞여 있다. 그대로 비우면 <b>"님 안녕하세요"</b> 가 되어
 * 받는 사람이 바로 알아챈다. 그래서 이름은 {@code 고객} 으로 대신한다 —
 * {@code 고객님 안녕하세요} 는 어색하지 않다.
 *
 * <p>소속은 대신할 말이 없어 빈 문자열로 둔다. {@code "주식회사 담당자님"} 같은
 * 가짜 소속을 지어내는 것보다 {@code " 담당자님"} 이 낫다. 소속을 꼭 써야 하는
 * 메일이라면 애초에 소속이 있는 컨택만 골라 보내야 한다.
 *
 * <h3>이스케이프</h3>
 * 이름·소속은 QR 폼이나 파일 임포트로 들어온 <b>외부 입력</b>이고, 치환 결과는
 * HTML 메일 본문에 그대로 박힌다. 이스케이프하지 않으면 이름에 넣은 태그가
 * 수신자 메일함에서 실행된다. 본문 HTML 자체는 관리자가 쓴 것이라 건드리지 않고,
 * <b>끼워 넣는 값만</b> 이스케이프한다.
 */
final class MailPersonalizer {

    /** 이름이 비었을 때 대신 쓰는 말. */
    private static final String NAME_FALLBACK = "고객";

    private MailPersonalizer() {
    }

    /**
     * @param html    관리자가 작성한 본문 (자리표시자 포함)
     * @param name    수신자 이름 (없으면 {@code null})
     * @param company 수신자 소속 (없으면 {@code null})
     */
    static String apply(String html, String name, String company) {
        if (html == null || html.isEmpty()) return html;
        String safeName = escape(blankTo(name, NAME_FALLBACK));
        String safeCompany = escape(blankTo(company, ""));
        return html
                .replace("{name}", safeName)
                .replace("{company}", safeCompany);
    }

    /** 본문에 치환할 자리표시자가 하나라도 있는지. 없으면 수신자별 렌더를 건너뛸 수 있다. */
    static boolean hasPlaceholder(String html) {
        return html != null && (html.contains("{name}") || html.contains("{company}"));
    }

    private static String blankTo(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    /**
     * HTML 특수문자를 엔티티로 바꾼다.
     *
     * <p>작은따옴표까지 바꾸는 이유: 치환 결과가 {@code <a title='{name}'>} 처럼
     * 속성 안에 들어갈 수 있다. 홑따옴표를 두면 거기서 속성이 끊긴다.
     */
    private static String escape(String value) {
        if (value.isEmpty()) return value;
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
