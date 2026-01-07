package com.bulc.homepage.licensing.config;

import com.bulc.homepage.licensing.service.SigningKeyProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 테스트용 SigningKeyProvider 설정.
 *
 * @SpringBootTest에서 @Import(TestKeyConfig.class)로 사용합니다.
 * @Primary로 DefaultSigningKeyProvider를 오버라이드합니다.
 *
 * 보안 이점:
 * - 테스트마다 런타임에 키 생성
 * - 레포지토리에 키 커밋 불필요
 * - keyId가 "test-"로 시작하여 프로덕션과 명확히 구분
 */
@TestConfiguration
public class TestKeyConfig {

    @Bean
    @Primary
    public SigningKeyProvider testSigningKeyProvider() {
        return new TestSigningKeyProvider();
    }
}
