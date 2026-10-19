package com.bulc.homepage.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 토스페이먼츠 키 구성 기동 점검 (MDP-937).
 *
 * <p>직전까지 {@code application.yml} 이 테스트 키를 기본값으로 들고 있었다. 환경변수가 누락돼도
 * 기동이 되고 결제창도 열리고 승인도 성공했다 — <b>다만 돈이 들어오지 않았다.</b>
 * 아무것도 고장난 것처럼 보이지 않는 종류의 사고라, 기동 단계에서 잡아야 한다.
 */
@DisplayName("토스 키 기동 점검")
class TossPaymentsStartupCheckTest {

    private TossPaymentsStartupCheck check(String client, String secret, String... profiles) {
        TossPaymentsConfig config = new TossPaymentsConfig();
        config.setClientKey(client);
        config.setSecretKey(secret);
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return new TossPaymentsStartupCheck(config, env);
    }

    /** 결제위젯(gck/gsk) 세트만 세팅한다 — ck/sk 와 독립적으로 검사된다. */
    private TossPaymentsStartupCheck widget(String client, String secret, String... profiles) {
        TossPaymentsConfig config = new TossPaymentsConfig();
        config.setWidgetClientKey(client);
        config.setWidgetSecretKey(secret);
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return new TossPaymentsStartupCheck(config, env);
    }

    /** 두 세트를 모두 세팅한다 — 실제 기동 경로({@code check()})를 재현한다. */
    private TossPaymentsStartupCheck both(String client, String secret,
                                          String widgetClient, String widgetSecret,
                                          String... profiles) {
        TossPaymentsConfig config = new TossPaymentsConfig();
        config.setClientKey(client);
        config.setSecretKey(secret);
        config.setWidgetClientKey(widgetClient);
        config.setWidgetSecretKey(widgetSecret);
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return new TossPaymentsStartupCheck(config, env);
    }

    @Nested
    @DisplayName("운영(prod)")
    class Prod {

        /** 결제를 받을 수 없는 상태로 서비스가 뜨는 것보다 뜨지 않는 것이 낫다. */
        @Test
        @DisplayName("키가 둘 다 없으면 기동을 중단한다")
        void bothMissingFailsStartup() {
            assertThatThrownBy(() -> check("", "", "prod").checkIntegrationKeys())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TOSS_CLIENT_KEY")
                    .hasMessageContaining("TOSS_SECRET_KEY");
        }

        @Test
        @DisplayName("시크릿 키만 없어도 기동을 중단한다")
        void missingSecretFailsStartup() {
            assertThatThrownBy(() -> check("live_ck_x", null, "prod").checkIntegrationKeys())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TOSS_SECRET_KEY");
        }

        @Test
        @DisplayName("공백만 있는 값도 누락으로 본다")
        void blankIsTreatedAsMissing() {
            assertThatThrownBy(() -> check("   ", "   ", "prod").checkIntegrationKeys())
                    .isInstanceOf(IllegalStateException.class);
        }

        /**
         * 지금 운영이 의도적으로 테스트 키로 돌고 있다(토스 심사 통과 후 전환 대기).
         * 여기서 막으면 배포 자체가 불가능해지므로 경고만 한다.
         */
        @Test
        @DisplayName("테스트 키는 기동을 막지 않는다 (전환 대기 중이라 경고만)")
        void testKeyIsAllowedForNow() {
            assertThatCode(() -> check("test_ck_x", "test_sk_x", "prod").checkIntegrationKeys())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("라이브 키는 정상 통과한다")
        void liveKeyPasses() {
            assertThatCode(() -> check("live_ck_x", "live_sk_x", "prod").checkIntegrationKeys())
                    .doesNotThrowAnyException();
        }

        /**
         * 클라이언트 키와 시크릿 키는 같은 연동 키 세트에서 복사해야 한다. 섞이면 토스가
         * 승인을 거부하는데, 그 시점은 고객이 카드 인증을 마친 뒤다 — 기동 때 잡는 게 낫다.
         */
        @Test
        @DisplayName("클라이언트는 라이브인데 시크릿이 테스트면 기동을 중단한다")
        void mixedKeyPairFailsStartup() {
            assertThatThrownBy(() -> check("live_ck_x", "test_sk_x", "prod").checkIntegrationKeys())
                    .isInstanceOf(IllegalStateException.class);
        }

        /**
         * 형식 불명은 "종류가 다르다" 보다 앞서 잡는다 — 더 정확한 진단이기 때문이다.
         * live/test 중 어느 쪽도 아닌 값은 키를 잘못 붙여넣은 것이고, 그 사실을 알려주는 편이
         * "두 키의 종류가 다르다" 보다 고치기 쉽다.
         */
        @Test
        @DisplayName("형식 불명인 키는 기동을 중단한다 (종류 비교보다 먼저)")
        void unknownPrefixFailsStartup() {
            assertThatThrownBy(() -> check("garbage", "live_sk_x", "prod").checkIntegrationKeys())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("형식이 올바르지 않습니다");
        }
    }

    @Nested
    @DisplayName("개발(dev)")
    class Dev {

        /** 결제를 쓰지 않는 로컬 개발이 키 때문에 막히면 안 된다. */
        @Test
        @DisplayName("키가 없어도 기동한다 (경고만)")
        void missingKeyOnlyWarns() {
            assertThatCode(() -> check("", "", "dev").checkIntegrationKeys()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("프로파일이 비어 있어도 기동한다")
        void noProfileOnlyWarns() {
            assertThatCode(() -> check(null, null).checkIntegrationKeys()).doesNotThrowAnyException();
        }

        /**
         * 키쌍 불일치는 프로파일과 무관하게 막는다 — 개발에서도 반드시 실패하는 구성이고,
         * 여기서 잡히면 운영에 올라가지 않는다.
         */
        @Test
        @DisplayName("키쌍이 섞이면 dev 에서도 기동을 중단한다")
        void mixedPairFailsEvenInDev() {
            assertThatThrownBy(() -> check("live_ck_x", "test_sk_x", "dev").checkIntegrationKeys())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("종류가 다릅니다");
        }

        @Test
        @DisplayName("형식 불명도 dev 에서 기동을 중단한다")
        void unknownFormatFailsEvenInDev() {
            assertThatThrownBy(() -> check("live_ck_x", "garbage", "dev").checkIntegrationKeys())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("형식이 올바르지 않습니다");
        }
    }

    /**
     * 결제위젯 키 세트 (gck/gsk).
     *
     * <p>ck/sk 와 다른 세트이고, 섞으면 토스가 {@code INVALID_API_KEY} 로 거부한다.
     * 계좌이체·가상계좌·간편결제는 위젯 세트에만 열려 있어서, 여기가 틀리면 그 수단들이
     * 통째로 막힌다 — 그것도 고객이 결제를 마친 뒤에 실패한다.
     */
    @Nested
    @DisplayName("결제위젯 키(gck/gsk)")
    class Widget {

        @Test
        @DisplayName("prod 에서 위젯 키가 비면 기동을 중단한다")
        void missingWidgetKeysFailStartupInProd() {
            assertThatThrownBy(() -> widget("", "", "prod").checkWidgetKeys())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TOSS_WIDGET_CLIENT_KEY")
                    .hasMessageContaining("TOSS_WIDGET_SECRET_KEY");
        }

        @Test
        @DisplayName("dev 에서 위젯 키가 비면 경고만 한다")
        void missingWidgetKeysOnlyWarnInDev() {
            assertThatCode(() -> widget("", "", "dev").checkWidgetKeys())
                    .doesNotThrowAnyException();
        }

        /** 가장 흔한 사고 — 위젯 자리에 API 개별 연동 키를 넣는다. */
        @Test
        @DisplayName("위젯 자리에 ck 를 넣으면 기동을 중단한다")
        void integrationClientKeyInWidgetSlotFails() {
            assertThatThrownBy(() -> widget("live_ck_x", "live_gsk_x", "prod").checkWidgetKeys())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("결제위젯 키가 아닙니다");
        }

        @Test
        @DisplayName("위젯 자리에 sk 를 넣으면 기동을 중단한다")
        void integrationSecretKeyInWidgetSlotFails() {
            assertThatThrownBy(() -> widget("live_gck_x", "live_sk_x", "prod").checkWidgetKeys())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("결제위젯 키가 아닙니다");
        }

        @Test
        @DisplayName("live 와 test 를 섞으면 기동을 중단한다")
        void mixedWidgetPairFails() {
            assertThatThrownBy(() -> widget("live_gck_x", "test_gsk_x", "prod").checkWidgetKeys())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("종류가 다릅니다");
        }

        @Test
        @DisplayName("올바른 위젯 라이브 키 쌍은 통과한다")
        void validWidgetLivePairPasses() {
            assertThatCode(() -> widget("live_gck_x", "live_gsk_x", "prod").checkWidgetKeys())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("위젯 테스트 키는 prod 에서도 경고만 한다")
        void widgetTestKeyOnlyWarnsInProd() {
            assertThatCode(() -> widget("test_gck_x", "test_gsk_x", "prod").checkWidgetKeys())
                    .doesNotThrowAnyException();
        }
    }

    /**
     * 실제 기동 경로. {@code @PostConstruct} 는 {@code check()} 하나뿐이므로,
     * 두 세트가 모두 여기서 검사되는지 고정해 둔다 — 한쪽을 빠뜨리면 그 수단이 조용히 죽는다.
     */
    @Nested
    @DisplayName("기동 경로(check)")
    class Entry {

        @Test
        @DisplayName("두 세트가 모두 올바르면 통과한다")
        void bothSetsValid() {
            assertThatCode(() -> both("live_ck_x", "live_sk_x", "live_gck_x", "live_gsk_x", "prod").check())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("ck/sk 는 맞아도 위젯 키가 비면 기동을 중단한다")
        void widgetMissingStillFails() {
            assertThatThrownBy(() -> both("live_ck_x", "live_sk_x", "", "", "prod").check())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("결제위젯 키가 prod 프로파일에서 비어 있습니다");
        }

        @Test
        @DisplayName("위젯 키는 맞아도 ck/sk 가 비면 기동을 중단한다")
        void integrationMissingStillFails() {
            assertThatThrownBy(() -> both("", "", "live_gck_x", "live_gsk_x", "prod").check())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("토스페이먼츠 키가 prod 프로파일에서 비어 있습니다");
        }
    }
}
