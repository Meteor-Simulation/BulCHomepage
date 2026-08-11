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
 * 소셜 로그인으로 신규 사용자를 만들 때 provider 가 내려준 name/mobile 값을
 * 그대로 저장하지 않는지 검증한다.
 *
 * <p>이 경로는 입력 폼을 거치지 않아 {@code @ValidPhone}/{@code @ValidName} 이
 * 적용되지 않으며, 과거 phone 컬럼에 이메일이 저장된 사례가 있었다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("소셜 로그인 사용자 정보 저장")
class OAuth2PhoneSanitizeTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomOAuth2UserService service;

    private User createWithMobile(String mobile) {
        return create("홍길동", mobile);
    }

    private User create(String name, String mobile) {
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
        ReflectionTestUtils.invokeMethod(service, "createNewUser", "user@example.com", name, mobile);
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

    // ---- 이름 ----------------------------------------------------------

    @Test
    @DisplayName("정상 이름은 그대로 저장한다")
    void keepsValidName() {
        assertThat(create("홍길동", null).getName()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("앞뒤 공백은 제거한다")
    void trimsName() {
        assertThat(create("  홍길동  ", null).getName()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("1자 이름은 저장하지 않는다")
    void rejectsTooShortName() {
        assertThat(create("홍", null).getName()).isNull();
    }

    @Test
    @DisplayName("50자를 넘는 이름은 잘라서 저장한다")
    void truncatesTooLongName() {
        String longName = "가".repeat(80);
        assertThat(create(longName, null).getName()).hasSize(50);
    }

    @Test
    @DisplayName("공백만 있는 이름은 null 로 둔다")
    void nullifiesBlankName() {
        assertThat(create("   ", null).getName()).isNull();
    }

    @Test
    @DisplayName("이름이 null 이면 그대로 null 이다")
    void nullifiesNullName() {
        assertThat(create(null, null).getName()).isNull();
    }
}
