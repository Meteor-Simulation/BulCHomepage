package com.bulc.homepage.auth;

import com.bulc.homepage.dto.request.SignupRequest;
import com.bulc.homepage.entity.User;
import com.bulc.homepage.licensing.config.TestKeyConfig;
import com.bulc.homepage.licensing.service.LicenseService;
import com.bulc.homepage.repository.UserRepository;
import com.bulc.homepage.service.AuthService;
import com.bulc.homepage.service.SignupTicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MDP-722: 이메일 회원가입 시 이름/전화번호가 실제로 저장되는지 검증한다.
 *
 * <p>신규 가입 빌더에 {@code .name()}/{@code .phone()} 이 빠져 있어 요청으로 값을 보내도
 * 저장되지 않았고, 재활성화 경로는 {@code setName(null)} 로 덮어쓰고 있었다.
 * 그 결과 이메일 가입자 27명 중 18명의 이름이 비어 있었다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestKeyConfig.class)
@Transactional
@DisplayName("이메일 회원가입 이름 저장")
class SignupNamePersistenceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private SignupTicketService signupTicketService;

    @Autowired
    private UserRepository userRepository;

    /**
     * 체험 라이선스 발급은 이 테스트의 관심사가 아니다.
     * H2 테스트 DB에는 라이선스 플랜 시드가 없어 실제 호출 시 예외가 나고
     * (AuthService 가 catch 하지만) Hibernate 세션이 오염되어 커밋이 실패한다.
     */
    @MockBean
    private LicenseService licenseService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedRole() {
        // H2(create-drop)에는 init.sql 시드가 없어 users.roles_code FK 가 깨진다
        jdbcTemplate.update("MERGE INTO user_roles (code, role) KEY(code) VALUES (?, ?)", "002", "USER");
    }

    private SignupRequest request(String email, String name, String phone) {
        UUID ticket = signupTicketService.createTicket(email);
        return SignupRequest.builder()
                .signupTicket(ticket)
                .name(name)
                .phoneNumber(phone)
                .password("Test1234!")
                .marketingAgreed(false)
                .language("ko")
                .build();
    }

    @Test
    @DisplayName("신규 가입 시 이름과 전화번호가 저장된다")
    void persistsNameAndPhoneOnSignup() {
        String email = "name-persist-" + UUID.randomUUID() + "@example.com";

        authService.signup(request(email, "홍길동", "010-1234-5678"));

        User saved = userRepository.findByEmail(email).orElseThrow();
        assertThat(saved.getName()).isEqualTo("홍길동");
        assertThat(saved.getPhone()).isEqualTo("010-1234-5678");
    }

    @Test
    @DisplayName("이름 앞뒤 공백은 제거하고 저장한다")
    void trimsName() {
        String email = "name-trim-" + UUID.randomUUID() + "@example.com";

        authService.signup(request(email, "  홍길동  ", null));

        assertThat(userRepository.findByEmail(email).orElseThrow().getName()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("전화번호 미입력은 빈 문자열이 아니라 null 로 저장한다")
    void storesNullPhoneWhenOmitted() {
        String email = "name-nophone-" + UUID.randomUUID() + "@example.com";

        authService.signup(request(email, "홍길동", "   "));

        assertThat(userRepository.findByEmail(email).orElseThrow().getPhone()).isNull();
    }
}
