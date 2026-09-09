package com.bulc.homepage.payment.notification;

import com.bulc.homepage.entity.User;
import com.bulc.homepage.repository.UserRepository;
import com.bulc.homepage.service.OperationalMailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
    private OperationalMailService operationalMailService;

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

    @Nested
    @DisplayName("발송")
    class Send {

        @Test
        @DisplayName("users 에서 조회한 이메일로 발송한다")
        void sendsToEmailResolvedFromUsers() {
            givenUserWithEmail("buyer@example.com");

            listener.onLicenseIssued(event(false));

            verify(operationalMailService)
                    .sendLicenseIssuedNotice("buyer@example.com", LICENSE_KEY, VALID_UNTIL, false);
        }

        @Test
        @DisplayName("이메일 앞뒤 공백은 제거하고 발송한다")
        void trimsEmail() {
            givenUserWithEmail("  buyer@example.com  ");

            listener.onLicenseIssued(event(false));

            verify(operationalMailService)
                    .sendLicenseIssuedNotice("buyer@example.com", LICENSE_KEY, VALID_UNTIL, false);
        }

        @Test
        @DisplayName("재시도 큐로 복구된 발급이면 recovered=true 로 전달한다")
        void passesRecoveredFlag() {
            givenUserWithEmail("buyer@example.com");

            listener.onLicenseIssued(event(true));

            verify(operationalMailService)
                    .sendLicenseIssuedNotice("buyer@example.com", LICENSE_KEY, VALID_UNTIL, true);
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

            verifyNoInteractions(operationalMailService);
        }

        @Test
        @DisplayName("이메일이 비어 있으면 발송하지 않는다")
        void skipsWhenEmailBlank() {
            givenUserWithEmail("   ");

            listener.onLicenseIssued(event(false));

            verify(operationalMailService, never())
                    .sendLicenseIssuedNotice(anyString(), anyString(), any(), anyBoolean());
        }

        @Test
        @DisplayName("이메일이 null 이면 발송하지 않는다")
        void skipsWhenEmailNull() {
            givenUserWithEmail(null);

            listener.onLicenseIssued(event(false));

            verify(operationalMailService, never())
                    .sendLicenseIssuedNotice(anyString(), anyString(), any(), anyBoolean());
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
                    .when(operationalMailService)
                    .sendLicenseIssuedNotice(anyString(), anyString(), any(), anyBoolean());

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
