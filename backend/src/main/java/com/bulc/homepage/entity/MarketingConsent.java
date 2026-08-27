package com.bulc.homepage.entity;

/**
 * 광고성 메일 수신 상태 코드 (MDP-772).
 *
 * <p>기존에는 {@code marketing_agreed} boolean 하나만 있어 "거절함"과 "아직 안 물어봄"이
 * 같은 값(false)이었다. 그래서 거절해도 로그인할 때마다 동의 팝업이 다시 떴다.
 *
 * <p>한 글자 코드는 {@code payments.status}(P/C/F/R) 등 기존 관례를 따른다.
 * A/U/N 대신 Y/N/P 를 쓴 이유: U(Unsubscribed)가 Undecided 로, N(None)이 No 로 읽혀
 * 의미가 정확히 뒤집힐 수 있기 때문이다. 발송 대상을 가르는 값이라 오독 여지를 없앤다.
 */
public final class MarketingConsent {

    /** 동의 — 팝업 노출 안 함, 광고성 메일 발송 대상 */
    public static final String AGREED = "Y";

    /** 거절 — 팝업 노출 안 함, 발송 제외 */
    public static final String DECLINED = "N";

    /** 미선택 — 팝업 노출, 발송 제외 (기본값) */
    public static final String PENDING = "P";

    private MarketingConsent() {
    }

    /** 광고성 메일을 보내도 되는 상태인지. */
    public static boolean canReceive(String value) {
        return AGREED.equals(value);
    }

    /** 사용자가 이미 답한 상태인지 (동의든 거절이든). 팝업 재노출 판단에 쓴다. */
    public static boolean isDecided(String value) {
        return AGREED.equals(value) || DECLINED.equals(value);
    }

    /** boolean 동의 여부를 상태 코드로 변환. */
    public static String from(boolean agreed) {
        return agreed ? AGREED : DECLINED;
    }
}
