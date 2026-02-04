package com.bulc.homepage.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/language")
public class LanguageController {

    /**
     * IP 기반 언어 감지 (인증 불필요)
     * Cloudflare CF-IPCountry 헤더 또는 개발용 X-Country-Code 헤더를 읽어서
     * KR이면 'ko', 그 외 'en'을 반환한다.
     */
    @GetMapping("/detect")
    public ResponseEntity<Map<String, String>> detectLanguage(
            @RequestHeader(value = "CF-IPCountry", required = false) String cfCountry,
            @RequestHeader(value = "X-Country-Code", required = false) String devCountry) {

        String country = cfCountry;
        if (country == null || country.isBlank()) {
            country = devCountry;
        }
        if (country == null || country.isBlank()) {
            country = "KR";
        }

        String language = "KR".equalsIgnoreCase(country) ? "ko" : "en";

        return ResponseEntity.ok(Map.of("language", language, "country", country.toUpperCase()));
    }
}
