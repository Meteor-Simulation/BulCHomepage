package com.bulc.homepage.oauth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Authorization Code Store 단위 테스트.
 */
class AuthorizationCodeStoreTest {

    private AuthorizationCodeStore codeStore;

    @BeforeEach
    void setUp() {
        codeStore = new AuthorizationCodeStore();
    }

    @Nested
    @DisplayName("createAndStore()")
    class CreateAndStore {

        @Test
        @DisplayName("Authorization Code 생성 및 저장")
        void shouldCreateAndStoreAuthorizationCode() {
            // given
            String userEmail = "test@example.com";
            String clientId = "test-client";
            String redirectUri = "myapp://callback";
            String codeChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
            String codeChallengeMethod = "S256";

            // when
            String code = codeStore.createAndStore(userEmail, clientId, redirectUri, codeChallenge, codeChallengeMethod);

            // then
            assertThat(code).isNotNull();
            assertThat(code.length()).isGreaterThanOrEqualTo(32);
        }

        @Test
        @DisplayName("생성된 코드로 조회 가능")
        void shouldBeRetrievableAfterCreation() {
            // given
            String userEmail = "test@example.com";
            String code = codeStore.createAndStore(userEmail, "client", "myapp://callback",
                    "challenge123456789012345678901234567890123", "S256");

            // when
            Optional<AuthorizationCode> authCode = codeStore.findValidCode(code);

            // then
            assertThat(authCode).isPresent();
            assertThat(authCode.get().getUserEmail()).isEqualTo(userEmail);
        }

        @Test
        @DisplayName("매번 다른 코드 생성")
        void shouldGenerateUniqueCodesEachTime() {
            // when
            String code1 = codeStore.createAndStore("user1@test.com", "client", "myapp://callback",
                    "challenge123456789012345678901234567890123", "S256");
            String code2 = codeStore.createAndStore("user2@test.com", "client", "myapp://callback",
                    "challenge123456789012345678901234567890123", "S256");

            // then
            assertThat(code1).isNotEqualTo(code2);
        }

        @Test
        @DisplayName("codeChallengeMethod가 null이면 기본값 S256")
        void shouldUseDefaultMethodWhenNull() {
            // given
            String code = codeStore.createAndStore("test@example.com", "client", "myapp://callback",
                    "challenge123456789012345678901234567890123", null);

            // when
            Optional<AuthorizationCode> authCode = codeStore.findValidCode(code);

            // then
            assertThat(authCode).isPresent();
            assertThat(authCode.get().getCodeChallengeMethod()).isEqualTo("S256");
        }
    }

    @Nested
    @DisplayName("findValidCode()")
    class FindValidCode {

        @Test
        @DisplayName("유효한 코드 조회 성공")
        void shouldFindValidCode() {
            // given
            String code = codeStore.createAndStore("test@example.com", "client", "myapp://callback",
                    "challenge123456789012345678901234567890123", "S256");

            // when
            Optional<AuthorizationCode> result = codeStore.findValidCode(code);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getCode()).isEqualTo(code);
            assertThat(result.get().isValid()).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 코드 조회 실패")
        void shouldReturnEmptyForNonExistentCode() {
            // when
            Optional<AuthorizationCode> result = codeStore.findValidCode("non-existent-code");

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("이미 사용된 코드 조회 실패")
        void shouldReturnEmptyForUsedCode() {
            // given
            String code = codeStore.createAndStore("test@example.com", "client", "myapp://callback",
                    "challenge123456789012345678901234567890123", "S256");

            // 코드 소비
            codeStore.consumeCode(code);

            // when
            Optional<AuthorizationCode> result = codeStore.findValidCode(code);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("consumeCode()")
    class ConsumeCode {

        @Test
        @DisplayName("유효한 코드 소비 성공")
        void shouldConsumeValidCode() {
            // given
            String code = codeStore.createAndStore("test@example.com", "client", "myapp://callback",
                    "challenge123456789012345678901234567890123", "S256");

            // when
            Optional<AuthorizationCode> result = codeStore.consumeCode(code);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getUserEmail()).isEqualTo("test@example.com");
            assertThat(result.get().isUsed()).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 코드 소비 실패")
        void shouldReturnEmptyForNonExistentCode() {
            // when
            Optional<AuthorizationCode> result = codeStore.consumeCode("non-existent-code");

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("한 번 소비된 코드는 다시 소비 불가")
        void shouldNotConsumeCodeTwice() {
            // given
            String code = codeStore.createAndStore("test@example.com", "client", "myapp://callback",
                    "challenge123456789012345678901234567890123", "S256");

            // 첫 번째 소비
            codeStore.consumeCode(code);

            // when - 두 번째 소비 시도
            Optional<AuthorizationCode> secondAttempt = codeStore.consumeCode(code);

            // then
            assertThat(secondAttempt).isEmpty();
        }

        @Test
        @DisplayName("소비된 코드는 저장소에서 제거됨")
        void shouldRemoveCodeAfterConsumption() {
            // given
            String code = codeStore.createAndStore("test@example.com", "client", "myapp://callback",
                    "challenge123456789012345678901234567890123", "S256");

            // when
            codeStore.consumeCode(code);

            // then - 저장소에서 제거되어 조회 불가
            Optional<AuthorizationCode> findResult = codeStore.findValidCode(code);
            assertThat(findResult).isEmpty();
        }
    }

    @Nested
    @DisplayName("Authorization Code 속성")
    class AuthorizationCodeProperties {

        @Test
        @DisplayName("생성된 코드에 모든 속성이 저장됨")
        void shouldStoreAllProperties() {
            // given
            String userEmail = "test@example.com";
            String clientId = "meteor-app";
            String redirectUri = "meteor://oauth/callback";
            String codeChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
            String codeChallengeMethod = "S256";

            // when
            String code = codeStore.createAndStore(userEmail, clientId, redirectUri, codeChallenge, codeChallengeMethod);
            Optional<AuthorizationCode> authCode = codeStore.findValidCode(code);

            // then
            assertThat(authCode).isPresent();
            AuthorizationCode ac = authCode.get();
            assertThat(ac.getUserEmail()).isEqualTo(userEmail);
            assertThat(ac.getClientId()).isEqualTo(clientId);
            assertThat(ac.getRedirectUri()).isEqualTo(redirectUri);
            assertThat(ac.getCodeChallenge()).isEqualTo(codeChallenge);
            assertThat(ac.getCodeChallengeMethod()).isEqualTo(codeChallengeMethod);
            assertThat(ac.getIssuedAt()).isNotNull();
            assertThat(ac.getExpiresAt()).isNotNull();
            assertThat(ac.getExpiresAt()).isAfter(ac.getIssuedAt());
        }

        @Test
        @DisplayName("새로 생성된 코드는 사용되지 않은 상태")
        void newCodeShouldNotBeUsed() {
            // given
            String code = codeStore.createAndStore("test@example.com", "client", "myapp://callback",
                    "challenge123456789012345678901234567890123", "S256");

            // when
            Optional<AuthorizationCode> authCode = codeStore.findValidCode(code);

            // then
            assertThat(authCode).isPresent();
            assertThat(authCode.get().isUsed()).isFalse();
        }
    }
}
