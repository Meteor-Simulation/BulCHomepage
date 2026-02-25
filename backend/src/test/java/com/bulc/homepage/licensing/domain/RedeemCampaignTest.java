package com.bulc.homepage.licensing.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class RedeemCampaignTest {

    private RedeemCampaign createCampaign() {
        return RedeemCampaign.builder()
                .name("Test Campaign")
                .productId(UUID.randomUUID())
                .licensePlanId(UUID.randomUUID())
                .usageCategory(UsageCategory.COMMERCIAL)
                .seatLimit(100)
                .perUserLimit(1)
                .build();
    }

    @Nested
    @DisplayName("isAvailable()")
    class IsAvailable {

        @Test
        @DisplayName("ACTIVE 상태이면 true")
        void activeStatus_returnsTrue() {
            RedeemCampaign campaign = createCampaign();
            assertThat(campaign.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("PAUSED 상태이면 false")
        void pausedStatus_returnsFalse() {
            RedeemCampaign campaign = createCampaign();
            campaign.pause();
            assertThat(campaign.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("ENDED 상태이면 false")
        void endedStatus_returnsFalse() {
            RedeemCampaign campaign = createCampaign();
            campaign.end();
            assertThat(campaign.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("validFrom이 미래이면 false")
        void futureValidFrom_returnsFalse() {
            RedeemCampaign campaign = RedeemCampaign.builder()
                    .name("Future Campaign")
                    .productId(UUID.randomUUID())
                    .licensePlanId(UUID.randomUUID())
                    .validFrom(Instant.now().plus(1, ChronoUnit.DAYS))
                    .build();
            assertThat(campaign.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("validUntil이 과거이면 false")
        void pastValidUntil_returnsFalse() {
            RedeemCampaign campaign = RedeemCampaign.builder()
                    .name("Expired Campaign")
                    .productId(UUID.randomUUID())
                    .licensePlanId(UUID.randomUUID())
                    .validUntil(Instant.now().minus(1, ChronoUnit.DAYS))
                    .build();
            assertThat(campaign.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("seatLimit이 null이면 무제한")
        void nullSeatLimit_isUnlimited() {
            RedeemCampaign campaign = RedeemCampaign.builder()
                    .name("Unlimited Campaign")
                    .productId(UUID.randomUUID())
                    .licensePlanId(UUID.randomUUID())
                    .seatLimit(null)
                    .build();
            assertThat(campaign.isAvailable()).isTrue();
        }
    }

    @Nested
    @DisplayName("상태 전이")
    class StatusTransitions {

        @Test
        @DisplayName("ACTIVE → PAUSED")
        void pause_fromActive() {
            RedeemCampaign campaign = createCampaign();
            campaign.pause();
            assertThat(campaign.getStatus()).isEqualTo(RedeemCampaignStatus.PAUSED);
        }

        @Test
        @DisplayName("PAUSED → ACTIVE (resume)")
        void resume_fromPaused() {
            RedeemCampaign campaign = createCampaign();
            campaign.pause();
            campaign.resume();
            assertThat(campaign.getStatus()).isEqualTo(RedeemCampaignStatus.ACTIVE);
        }

        @Test
        @DisplayName("ACTIVE → ENDED")
        void end_fromActive() {
            RedeemCampaign campaign = createCampaign();
            campaign.end();
            assertThat(campaign.getStatus()).isEqualTo(RedeemCampaignStatus.ENDED);
        }

        @Test
        @DisplayName("PAUSED에서 pause() 불가")
        void pause_fromPaused_throwsException() {
            RedeemCampaign campaign = createCampaign();
            campaign.pause();
            assertThatThrownBy(campaign::pause)
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("ACTIVE에서 resume() 불가")
        void resume_fromActive_throwsException() {
            RedeemCampaign campaign = createCampaign();
            assertThatThrownBy(campaign::resume)
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("ENDED에서 end() 불가")
        void end_fromEnded_throwsException() {
            RedeemCampaign campaign = createCampaign();
            campaign.end();
            assertThatThrownBy(campaign::end)
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
