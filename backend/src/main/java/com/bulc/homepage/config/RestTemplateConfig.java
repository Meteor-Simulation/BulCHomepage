package com.bulc.homepage.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

@Configuration
public class RestTemplateConfig {

    /**
     * 외부 API 호출용 RestTemplate.
     *
     * <p><b>문자셋을 UTF-8 로 못박는 이유</b> — 기본 {@link StringHttpMessageConverter} 는
     * 응답 Content-Type 에 charset 이 없으면 본문을 <b>ISO-8859-1</b> 로 해석한다. 토스페이먼츠는
     * charset 없는 {@code application/json} 으로 응답하므로, 한글 필드(카드 종류 "신용",
     * 소유자 "개인", 간편결제 제공자, 가상계좌 입금자명, 에러 메시지)가 한 글자씩 깨진 뒤
     * UTF-8 로 다시 저장되어 DB 에 이중 인코딩된 값이 들어간다.
     * (실제로 {@code billing_keys.card_type} 에 "신용" 대신 6글자 깨진 값이 저장됐다 — 2026-10-06)
     *
     * <p>UTF-8 변환기를 목록 맨 앞에 끼워 기본 변환기보다 먼저 선택되게 한다.
     * {@code setWriteAcceptCharset(false)} 는 요청마다 붙는 장황한 Accept-Charset 헤더를 막는다.
     */
    @Bean
    public RestTemplate restTemplate() {
        StringHttpMessageConverter utf8Converter = new StringHttpMessageConverter(StandardCharsets.UTF_8);
        utf8Converter.setWriteAcceptCharset(false);

        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getMessageConverters().add(0, utf8Converter);
        return restTemplate;
    }
}
