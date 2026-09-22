package com.bulc.homepage.payment.notification;

import com.bulc.homepage.entity.User;
import com.bulc.homepage.mail.api.EmailCategory;
import com.bulc.homepage.mail.api.MailPort;
import com.bulc.homepage.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 라이선스 발급 완료 통지 (MDP-833 · MDP-876 문구 이관 반영).
 *
 * <p>MDP-876 에서 발급 안내의 문구·템플릿 변수를 이 리스너가 소유하도록 옮겼다.
 * 그래서 검증 대상이 {@code OperationalMailService.sendLicenseIssuedNotice} 호출에서
 * {@code MailPort.sendByTemplate} 호출 + 템플릿 변수 내용으로 바뀌었다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LicenseIssuedMailListener")
class LicenseIssuedMailListenerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID LICENSE_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final String LICENSE_KEY = "BULC-TEST-0000-0000";
    private static final Instant VALID_UNTIL = Instant.parse("2027-09-09T00:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private MailPort mailPort;

    @InjectMocks
    private LicenseIssuedMailListener listener;

    private LicenseIssuedEvent event(boolean recovered) {
        return new LicenseIssuedEvent(USER_ID, LICENSE_ID, LICENSE_KEY, VALID_UNTIL, ORDER_ID, recovered);
    }

    private void givenUserWithEmail(String email) {
        User user = mock(User.class);
        when(user.getEmail()).thenReturn(email);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> capturedVars() {
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mailPort).sendByTemplate(
                eq(EmailCategory.OPERATIONAL), anyString(), eq("license_issued"),
                anyString(), captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("발송")
    class Send {

        @Test
        @DisplayName("users 에서 조회한 이메일로 OPERATIONAL 발송한다")
        void sendsToEmailResolvedFromUsers() {
            givenUserWithEmail("buyer@example.com");

            listener.onLicenseIssued(event(false));

            verify(mailPort).sendByTemplate(
                    eq(EmailCategory.OPERATIONAL), eq("buyer@example.com"), eq("license_issued"),
                    eq("[BulC] 라이선스 발급 완료 안내"), any());
        }

        @Test
        @DisplayName("이메일 앞뒤 공백은 제거하고 발송한다")
        void trimsEmail() {
            givenUserWithEmail("  buyer@example.com  ");

            listener.onLicenseIssued(event(false));

            verify(mailPort).sendByTemplate(
                    any(), eq("buyer@example.com"), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("라이선스 키와 만료일이 템플릿 변수로 전달된다")
        void passesLicenseKeyAndExpiry() {
            givenUserWithEmail("buyer@example.com");

            listener.onLicenseIssued(event(false));

            Map<String, String> vars = capturedVars();
            assertThat(vars.get("license_key")).isEqualTo(LICENSE_KEY);
            assertThat(vars.get("valid_until")).isEqualTo("2027-09-09");
            assertThat(vars.get("mypage_url")).endsWith("/mypage");
        }

        @Test
        @DisplayName("복구 발급이면 안내 문구가 달라진다")
        void differentIntroWhenRecovered() {
            givenUserWithEmail("buyer@example.com");

            listener.onLicenseIssued(event(true));

            assertThat(capturedVars().get("intro")).contains("지연되었던");
        }

        @Test
        @DisplayName("일반 발급이면 지연 문구가 없다")
        void plainIntroWhenNotRecovered() {
            givenUserWithEmail("buyer@example.com");

            listener.onLicenseIssued(event(false));

            assertThat(capturedVars().get("intro")).doesNotContain("지연되었던");
        }

        @Test
        @DisplayName("라이선스 키가 없으면 '-' 로 대체한다")
        void dashWhenKeyMissing() {
            givenUserWithEmail("buyer@example.com");

            listener.onLicenseIssued(
                    new LicenseIssuedEvent(USER_ID, LICENSE_ID, null, null, ORDER_ID, false));

            Map<String, String> vars = capturedVars();
            assertThat(vars.get("license_key")).isEqualTo("-");
            assertThat(vars.get("valid_until")).isEqualTo("-");
        }
    }

    @Nested
    @DisplayName("발송하지 않는 경우")
    class Skip {

        @Test
        @DisplayName("사용자를 찾지 못하면 발송하지 않는다")
        void skipsWhenUserMissing() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            listener.onLicenseIssued(event(false));

            verifyNoInteractions(mailPort);
        }

        @Test
        @DisplayName("이메일이 비어 있으면 발송하지 않는다")
        void skipsWhenEmailBlank() {
            givenUserWithEmail("   ");

            listener.onLicenseIssued(event(false));

            verify(mailPort, never()).sendByTemplate(any(), anyString(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("이메일이 null 이면 발송하지 않는다")
        void skipsWhenEmailNull() {
            givenUserWithEmail(null);

            listener.onLicenseIssued(event(false));

            verify(mailPort, never()).sendByTemplate(any(), anyString(), anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("실패 격리")
    class FailureIsolation {

        /**
         * 라이선스는 발급 즉시 ACTIVE 이고 메일은 통지 수단일 뿐이다.
         * 발송 실패가 호출부로 전파되면 안 된다.
         */
        @Test
        @DisplayName("메일 발송이 실패해도 예외를 전파하지 않는다")
        void swallowsSendFailure() {
            givenUserWithEmail("buyer@example.com");
            doThrow(new RuntimeException("Graph API 500"))
                    .when(mailPort)
                    .sendByTemplate(any(), anyString(), anyString(), anyString(), any());

            assertThatCode(() -> listener.onLicenseIssued(event(false)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("사용자 조회가 실패해도 예외를 전파하지 않는다")
        void swallowsLookupFailure() {
            when(userRepository.findById(USER_ID)).thenThrow(new RuntimeException("DB down"));

            assertThatCode(() -> listener.onLicenseIssued(event(false)))
                    .doesNotThrowAnyException();
        }
    }
}
