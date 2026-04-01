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

    private String clientKey;
    private String secretKey;
    private String successUrl;
    private String failUrl;

    // 토스페이먼츠 API URL
    public static final String TOSS_API_URL = "https://api.tosspayments.com/v1/payments";
}
