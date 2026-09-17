package com.bulc.homepage.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 구독 자동 갱신 도메인 규칙 (MDP-880).
 *
 * <p>종전에는 이 규칙을 확인할 방법이 운영 엔드포인트({@code TestController})로 구독 날짜를
 * 조작해 보는 것뿐이었다. 1년 주기라 실제 갱신을 기다릴 수 없었기 때문이다.
 * 여기서 날짜를 직접 구성해 같은 시나리오를 검증하므로 그 엔드포인트가 없어도 된다.
 *
 * <p>운영 확인(2026-09-17): subscription_payments 0건 — 갱신 경로는 실데이터로 한 번도
 * 실행된 적이 없고, 가장 이른 도래가 2027-07-24 다. 그 사이 발급 경로가 계속 바뀌므로
 * (MDP-831 LicenseIssuePort · MDP-832 재시도 큐) 자동 검증이 필요하다.
 */
@DisplayName("Subscription 자동 갱신")
class SubscriptionRenewalTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 4, 9, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2027, 4, 9, 0, 0);

    private Subscription yearly() {
        return Subscription.builder()
                .id(1L)
                .startDate(START)
                .endDate(END)
                .status("A")
                .billingCycle("YEARLY")
                .build();
    }

    @Nested
    @DisplayName("다음 결제일 계산")
    class NextBillingDate {

        @Test
        @DisplayName("종료일 7일 전으로 잡힌다")
        void sevenDaysBeforeEnd() {
            Subscription s = yearly();
            s.enableAutoRenew(10L, "YEARLY");

            assertThat(s.getNextBillingDate()).isEqualTo(END.minusDays(7));
        }

        @Test
        @DisplayName("자동 갱신이 꺼져 있으면 null 이다")
        void nullWhenAutoRenewOff() {
            Subscription s = yearly();
            s.enableAutoRenew(10L, "YEARLY");

            s.disableAutoRenew();

            assertThat(s.getAutoRenew()).isFalse();
            assertThat(s.getNextBillingDate()).isNull();
        }

        @Test
        @DisplayName("종료일이 없으면 null 이다")
        void nullWhenNoEndDate() {
            Subscription s = Subscription.builder().autoRenew(true).build();

            s.calculateNextBillingDate();

            assertThat(s.getNextBillingDate()).isNull();
        }
    }

    @Nested
    @DisplayName("갱신 대상 판정")
    class DueForRenewal {

        @Test
        @DisplayName("결제일이 지났으면 대상이다")
        void dueWhenPast() {
            Subscription s = yearly();
            s.setAutoRenew(true);
            s.setNextBillingDate(LocalDateTime.now().minusMinutes(1));

            assertThat(s.isDueForRenewal()).isTrue();
        }

        @Test
        @DisplayName("결제일이 아직이면 대상이 아니다")
        void notDueWhenFuture() {
            Subscription s = yearly();
            s.setAutoRenew(true);
            s.setNextBillingDate(LocalDateTime.now().plusDays(1));

            assertThat(s.isDueForRenewal()).isFalse();
        }

        @Test
        @DisplayName("자동 갱신이 꺼져 있으면 결제일이 지났어도 대상이 아니다")
        void notDueWhenAutoRenewOff() {
            Subscription s = yearly();
            s.setAutoRenew(false);
            s.setNextBillingDate(LocalDateTime.now().minusDays(1));

            assertThat(s.isDueForRenewal()).isFalse();
        }

        @Test
        @DisplayName("결제일이 없으면 대상이 아니다")
        void notDueWhenNoBillingDate() {
            Subscription s = yearly();
            s.setAutoRenew(true);
            s.setNextBillingDate(null);

            assertThat(s.isDueForRenewal()).isFalse();
        }
    }

    @Nested
    @DisplayName("갱신 (결제 성공 후)")
    class Renew {

        @Test
        @DisplayName("연간: 기존 종료일이 새 시작일이 되고 1년 연장된다")
        void yearlyExtendsOneYear() {
            Subscription s = yearly();
            s.enableAutoRenew(10L, "YEARLY");

            s.renew();

            assertThat(s.getStartDate()).isEqualTo(END);
            assertThat(s.getEndDate()).isEqualTo(END.plusYears(1));
            assertThat(s.getStatus()).isEqualTo("A");
        }

        @Test
        @DisplayName("월간: 1개월 연장된다")
        void monthlyExtendsOneMonth() {
            Subscription s = yearly();
            s.enableAutoRenew(10L, "MONTHLY");

            s.renew();

            assertThat(s.getEndDate()).isEqualTo(END.plusMonths(1));
        }

        @Test
        @DisplayName("분기: 3개월 연장된다")
        void quarterlyExtendsThreeMonths() {
            Subscription s = yearly();
            s.enableAutoRenew(10L, "QUARTERLY");

            s.renew();

            assertThat(s.getEndDate()).isEqualTo(END.plusMonths(3));
        }

        @Test
        @DisplayName("갱신 후 다음 결제일이 새 종료일 기준으로 다시 잡힌다")
        void recalculatesNextBillingDate() {
            Subscription s = yearly();
            s.enableAutoRenew(10L, "YEARLY");

            s.renew();

            assertThat(s.getNextBillingDate()).isEqualTo(END.plusYears(1).minusDays(7));
        }

        /**
         * 기간이 이어붙는지 확인한다. 갱신 시작일을 '오늘'로 잡으면 남은 기간이 증발하므로
         * 반드시 기존 종료일이어야 한다.
         */
        @Test
        @DisplayName("연속 갱신해도 기간이 끊기지 않는다")
        void consecutiveRenewalsAreContiguous() {
            Subscription s = yearly();
            s.enableAutoRenew(10L, "YEARLY");

            s.renew();
            LocalDateTime firstEnd = s.getEndDate();
            s.renew();

            assertThat(s.getStartDate()).isEqualTo(firstEnd);
            assertThat(s.getEndDate()).isEqualTo(END.plusYears(2));
        }
    }

    @Nested
    @DisplayName("상태 전이")
    class StatusTransition {

        @Test
        @DisplayName("취소하면 자동 갱신과 결제일이 함께 해제된다")
        void cancelClearsAutoRenew() {
            Subscription s = yearly();
            s.enableAutoRenew(10L, "YEARLY");

            s.cancel();

            assertThat(s.getStatus()).isEqualTo("C");
            assertThat(s.getAutoRenew()).isFalse();
            assertThat(s.getNextBillingDate()).isNull();
            assertThat(s.isActive()).isFalse();
        }

        @Test
        @DisplayName("만료하면 상태만 바뀐다")
        void expireChangesStatusOnly() {
            Subscription s = yearly();

            s.expire();

            assertThat(s.getStatus()).isEqualTo("E");
            assertThat(s.isActive()).isFalse();
        }
    }
}
