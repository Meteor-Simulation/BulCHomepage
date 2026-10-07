package com.bulc.homepage.payment.domain;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 토스페이먼츠 카드사 코드(issuerCode) → 카드사명.
 *
 * <p>예전 응답에는 카드사 한글명이 최상위 {@code cardCompany} 로 바로 담겨 왔다. 그런데
 * <b>API 버전 2024-06-01 부터 그 필드가 응답에서 삭제</b>되고 두 자리 코드
 * {@code card.issuerCode} 만 내려온다. 그래서 코드→이름 표는 우리가 들고 있어야 한다.
 *
 * <p>출처: <a href="https://docs.tosspayments.com/reference/codes">토스페이먼츠 코드 모음 — 카드사 코드</a>
 *
 * <p>모르는 코드가 오면 {@link #nameOf(String)} 가 {@code null} 을 돌려준다. 이름을 억지로
 * 만들어 내지 않는 이유는, 화면에 엉뚱한 카드사가 찍히는 것보다 비어 있는 편이 낫고
 * 원본 코드는 {@code card_issuer_code} 컬럼에 그대로 남아 추적할 수 있기 때문이다.
 */
public enum CardIssuer {

    // 국내
    IBK_BC("3K", "기업비씨"),
    GWANGJU("46", "광주"),
    LOTTE("71", "롯데"),
    KDB("30", "산업"),
    BC("31", "BC"),
    SAMSUNG("51", "삼성"),
    SAEMAUL("38", "새마을"),
    SHINHAN("41", "신한"),
    SHINHYEOP("62", "신협"),
    CITI("36", "씨티"),
    /** 우리BC — 매입사가 BC카드다. */
    WOORI_BC("33", "우리"),
    /** 우리 — 매입사가 우리카드다. */
    WOORI("W1", "우리"),
    POST("37", "우체국"),
    SAVINGBANK("39", "저축"),
    JEONBUK("35", "전북"),
    JEJU("42", "제주"),
    KAKAOBANK("15", "카카오뱅크"),
    KBANK("3A", "케이뱅크"),
    TOSSBANK("24", "토스뱅크"),
    HANA("21", "하나"),
    HYUNDAI("61", "현대"),
    KOOKMIN("11", "국민"),
    NONGHYEOP("91", "농협"),
    SUHYEOP("34", "수협"),

    // 해외
    DINERS("6D", "다이너스"),
    MASTER("4M", "마스터"),
    UNIONPAY("3C", "유니온페이"),
    AMEX("7A", "AMEX"),
    JCB("4J", "JCB"),
    VISA("4V", "비자");

    private static final Map<String, CardIssuer> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(CardIssuer::getCode, Function.identity()));

    private final String code;
    private final String displayName;

    CardIssuer(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 카드사 코드로 카드사명을 찾는다.
     *
     * @param issuerCode 토스가 준 {@code card.issuerCode} (null 허용)
     * @return 카드사명. 코드가 없거나 표에 없으면 {@code null}
     */
    public static String nameOf(String issuerCode) {
        if (issuerCode == null || issuerCode.isBlank()) {
            return null;
        }
        CardIssuer issuer = BY_CODE.get(issuerCode.trim().toUpperCase());
        return issuer != null ? issuer.displayName : null;
    }
}
