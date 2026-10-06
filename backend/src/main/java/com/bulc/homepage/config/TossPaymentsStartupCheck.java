package com.bulc.homepage.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 토스페이먼츠 키 구성 기동 점검 (MDP-937).
 *
 * <p>직전까지 {@code application.yml} 이 테스트 키를 기본값으로 들고 있었다. 그러면 환경변수가
 * 누락돼도 기동이 되고, 결제창도 열리고, 승인도 성공한다. <b>다만 돈이 들어오지 않는다.</b>
 * 실키 전환 직후에 가장 위험한 형태의 사고다 — 아무것도 고장난 것처럼 보이지 않는다.
 *
 * <p>특히 배포 가이드의 {@code --env-file .env.prod} 경로가 함정이었다. 그 예시 파일에
 * {@code TOSS_*} 항목이 없어서, 가이드대로 재배포하면 키가 비고 테스트 키 기본값으로 떨어졌다.
 *
 * <p>그래서 기본값을 지우고 이 점검을 둔다. 판단 기준은 두 단계다:
 * <ol>
 *   <li><b>키 누락 → prod 에서 기동 중단.</b> 결제를 받을 수 없는 상태로 서비스가 뜨는 것보다
 *       뜨지 않는 것이 낫다. 헬스체크 실패 시 {@code deploy.sh} 가 이전 JAR 로 자동 롤백한다</li>
 *   <li><b>테스트 키 → 경고만.</b> 지금 운영이 의도적으로 테스트 키로 돌고 있다(토스 심사 통과
 *       후 전환 대기). 여기서 기동을 막으면 배포 자체가 불가능해진다. 실키 전환이 끝나면
 *       {@link #failOnTestKeyInProd} 를 켜서 되돌림을 막을 수 있다</li>
 * </ol>
 */
@Slf4j
@Component
public class TossPaymentsStartupCheck {

    /** 라이브 키 접두사. 토스는 {@code live_sk_} · {@code live_ck_} 를 쓴다. */
    private static final String LIVE_PREFIX = "live_";
    private static final String TEST_PREFIX = "test_";

    /**
     * 실키 전환이 끝난 뒤 {@code true} 로 바꾸면, 테스트 키로 되돌아가는 배포를 기동 단계에서 막는다.
     *
     * <p>지금 켜면 현재 운영(테스트 키)이 뜨지 못하므로 전환 완료 후에 켠다.
     */
    private static final boolean failOnTestKeyInProd = false;

    private final TossPaymentsConfig config;
    private final Environment environment;

    public TossPaymentsStartupCheck(TossPaymentsConfig config, Environment environment) {
        this.config = config;
        this.environment = environment;
    }

    @PostConstruct
    public void check() {
        boolean prod = isProd();
        String secret = trimToEmpty(config.getSecretKey());
        String client = trimToEmpty(config.getClientKey());

        // 1) 누락 — prod 에서는 기동 중단
        String missing = describeMissing(client, secret);
        if (missing != null) {
            if (prod) {
                throw new IllegalStateException(
                        "[FATAL] 토스페이먼츠 키가 prod 프로파일에서 비어 있습니다 (" + missing + "). "
                                + "환경변수 TOSS_CLIENT_KEY · TOSS_SECRET_KEY 를 설정하세요. "
                                + "키 없이 기동하면 결제 승인이 전부 실패하므로 서버를 시작하지 않습니다. "
                                + "docker compose 로 띄울 때 --env-file 에 해당 항목이 있는지 확인하세요.");
            }
            log.warn("========================================");
            log.warn("토스페이먼츠 키가 설정되지 않았습니다 ({}). 결제 기능을 쓸 수 없습니다.", missing);
            log.warn("개발 중이라면 무시해도 되지만, 결제를 테스트하려면 TOSS_* 환경변수를 설정하세요.");
            log.warn("========================================");
            return;
        }

        // 2) 형식·키쌍 검사 — 테스트 키 허용보다 **먼저** 본다.
        //
        //    순서가 중요하다. "테스트 키면 통과" 를 먼저 보면 live 클라이언트 + test 시크릿
        //    조합이 "테스트 모드" 로 분류되어 그냥 통과한다. 그 구성은 토스가 승인을 거부하는데,
        //    거부 시점은 고객이 카드 인증을 마친 뒤다. 기동 때 잡아야 한다.
        KeyKind clientKind = kindOf(client);
        KeyKind secretKind = kindOf(secret);

        if (clientKind == KeyKind.UNKNOWN || secretKind == KeyKind.UNKNOWN) {
            throw new IllegalStateException(
                    "[FATAL] 토스페이먼츠 키 형식이 올바르지 않습니다 "
                            + "(client=" + prefixOf(client) + ", secret=" + prefixOf(secret) + "). "
                            + "키는 live_ 또는 test_ 로 시작해야 합니다. "
                            + "토스 대시보드 > API 개별 연동 키 의 값을 그대로 넣으세요.");
        }

        if (clientKind != secretKind) {
            throw new IllegalStateException(
                    "[FATAL] 토스페이먼츠 클라이언트 키와 시크릿 키의 종류가 다릅니다 "
                            + "(client=" + prefixOf(client) + ", secret=" + prefixOf(secret) + "). "
                            + "두 키는 같은 연동 키 세트에서 복사해야 하며, 섞이면 결제 승인이 실패합니다.");
        }

        // 3) 테스트 키 — 실키 전환 전까지는 경고만
        if (clientKind == KeyKind.TEST) {
            if (prod && failOnTestKeyInProd) {
                throw new IllegalStateException(
                        "[FATAL] prod 프로파일에서 토스페이먼츠 테스트 키가 감지되었습니다. "
                                + "실결제가 이루어지지 않으므로 서버를 시작하지 않습니다. live_ 키로 교체하세요.");
            }
            if (prod) {
                log.warn("========================================");
                log.warn("운영 프로파일인데 토스페이먼츠 테스트 키로 동작합니다 — 실제 결제·정산이 일어나지 않습니다.");
                log.warn("고객이 결제해도 대금이 들어오지 않고, 라이선스는 발급됩니다.");
                log.warn("실키 전환은 MDP-547 참고. 전환 후 failOnTestKeyInProd 를 켜 되돌림을 막으세요.");
                log.warn("========================================");
            }
            return;
        }

        log.info("[결제] 토스페이먼츠 라이브 키로 구성되었습니다 (client={}, secret={}).",
                prefixOf(client), prefixOf(secret));
    }

    /** 무엇이 비었는지 사람이 읽을 형태로. 둘 다 비면 둘 다 알려준다. */
    private String describeMissing(String client, String secret) {
        boolean noClient = client.isEmpty();
        boolean noSecret = secret.isEmpty();
        if (noClient && noSecret) return "TOSS_CLIENT_KEY, TOSS_SECRET_KEY";
        if (noSecret) return "TOSS_SECRET_KEY";
        if (noClient) return "TOSS_CLIENT_KEY";
        return null;
    }

    /** 로그에 키 값을 남기지 않는다 — 접두사만 쓴다. */
    private String prefixOf(String key) {
        if (key.startsWith(LIVE_PREFIX)) return "live_***";
        if (key.startsWith(TEST_PREFIX)) return "test_***";
        return "unknown_***";
    }

    /** 키 종류. 섞이면 승인이 실패하므로 둘을 같은 기준으로 분류해 비교한다. */
    private enum KeyKind { LIVE, TEST, UNKNOWN }

    private KeyKind kindOf(String key) {
        if (key.startsWith(LIVE_PREFIX)) return KeyKind.LIVE;
        if (key.startsWith(TEST_PREFIX)) return KeyKind.TEST;
        return KeyKind.UNKNOWN;
    }

    private String trimToEmpty(String v) {
        return v == null ? "" : v.trim();
    }

    private boolean isProd() {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }
}
