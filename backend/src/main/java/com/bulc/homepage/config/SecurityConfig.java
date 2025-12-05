package com.bulc.homepage.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 개발 환경용 Security 설정.
     * 모든 요청 허용, CSRF 비활성화.
     */
    @Bean
    @Profile("dev")
    public SecurityFilterChain devSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                );

        return http.build();
    }

    /**
     * 운영 환경용 Security 설정.
     * 추후 JWT 인증 등 추가 예정.
     */
    @Bean
    @Profile("prod")
    public SecurityFilterChain prodSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers("/api/licenses/*/validate").permitAll()  // 클라이언트 검증 허용
                        .anyRequest().authenticated()
                );

        // TODO: JWT 필터 추가

        return http.build();
    }

    /**
     * 기본 프로필 (dev, prod 둘 다 아닐 때).
     */
    @Bean
    @Profile("default")
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        return devSecurityFilterChain(http);
    }
}
