package com.bulc.homepage.oauth2;

import com.bulc.homepage.entity.User;
import com.bulc.homepage.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * 소셜 로그인으로 신규 사용자를 만들 때 provider 가 내려준 mobile 값을 그대로
 * phone 컬럼에 넣지 않는지 검증한다.
 *
 * <p>이 경로는 입력 폼을 거치지 않아 {@code @ValidPhone} 이 적용되지 않으며,
 * 과거 phone 컬럼에 이메일이 저장된 사례가 있었다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("소셜 로그인 전화번호 저장")
class OAuth2PhoneSanitizeTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomOAuth2UserService service;

    private User createWithMobile(String mobile) {
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
        ReflectionTestUtils.invokeMethod(service, "createNewUser", "user@example.com", "홍길동", mobile);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("정상 전화번호는 그대로 저장한다")
    void keepsValidPhone() {
        assertThat(createWithMobile("010-2366-6455").getPhone()).isEqualTo("010-2366-6455");
    }

    @Test
    @DisplayName("하이픈 없는 번호도 저장한다")
    void keepsDigitsOnlyPhone() {
        assertThat(createWithMobile("01023666455").getPhone()).isEqualTo("01023666455");
    }

    @Test
    @DisplayName("이메일이 오면 저장하지 않는다")
    void rejectsEmail() {
        assertThat(createWithMobile("wndnjs6455@naver.com").getPhone()).isNull();
    }

    @Test
    @DisplayName("자리수가 모자란 값은 저장하지 않는다")
    void rejectsTooShort() {
        assertThat(createWithMobile("010").getPhone()).isNull();
    }

    @Test
    @DisplayName("공백만 있는 값은 null 로 둔다")
    void nullifiesBlank() {
        assertThat(createWithMobile("   ").getPhone()).isNull();
    }

    @Test
    @DisplayName("null 은 그대로 null 이다")
    void nullifiesNull() {
        assertThat(createWithMobile(null).getPhone()).isNull();
    }
}
