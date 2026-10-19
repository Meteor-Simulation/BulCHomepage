package com.bulc.homepage.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "toss.payments")
@Getter
@Setter
public class TossPaymentsConfig {

    /**
     * API 개별 연동 키({@code ck}/{@code sk}) — 자동결제(빌링) 전용.
     *
     * <p>{@code clientKey} 는 API 호출에 쓰이지 않는다. {@link TossPaymentsStartupCheck} 가
     * 시크릿 키와 종류(live/test)가 어긋나지 않았는지 기동 때 대조하는 용도다.
     */
    private String clientKey;
    private String secretKey;

    /**
     * 주문서형·결제창형 연동 키({@code gck}/{@code gsk}) — 결제위젯 전용.
     *
     * <p>토스는 연동 방식마다 키 세트를 따로 발급하고, <b>세트가 아닌 키를 섞으면
     * {@code INVALID_API_KEY} 로 거부</b>한다. 위젯으로 시작한 결제의 승인·취소·조회는
     * 전부 {@code gsk} 여야 하고, 빌링키 발급·자동결제는 {@code sk} 여야 한다.
     *
     * <p>계좌이체·가상계좌·간편결제는 위젯에만 열린다 — 상점관리자의 "결제 UI 설정" 이
     * 이 키 세트에 적용되기 때문이다. {@code ck} 로 결제창을 직접 호출하면 계약이 끝나
     * 있어도 "Toss Payments와 계약된 결제수단이 아닙니다" 로 거부된다.
     */
    private String widgetClientKey;
    private String widgetSecretKey;

    private String successUrl;
    private String failUrl;

    // 토스페이먼츠 API URL
    public static final String TOSS_API_URL = "https://api.tosspayments.com/v1/payments";
}
