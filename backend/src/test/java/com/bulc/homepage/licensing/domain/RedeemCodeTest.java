package com.bulc.homepage.licensing.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class RedeemCodeTest {

    @Test
    @DisplayName("새로 생성된 코드는 redeemable")
    void newCode_isRedeemable() {
        RedeemCode code = RedeemCode.builder()
                .campaignId(UUID.randomUUID())
                .codeHash("abc123")
                .codeType(RedeemCodeType.RANDOM)
                .maxRedemptions(1)
                .build();
        assertThat(code.isRedeemable()).isTrue();
    }

    @Test
    @DisplayName("비활성화된 코드는 redeemable이 아님")
    void deactivatedCode_isNotRedeemable() {
        RedeemCode code = RedeemCode.builder()
                .campaignId(UUID.randomUUID())
                .codeHash("abc123")
                .maxRedemptions(1)
                .build();
        code.deactivate();
        assertThat(code.isRedeemable()).isFalse();
    }

    @Test
    @DisplayName("만료된 코드는 redeemable이 아님")
    void expiredCode_isNotRedeemable() {
        RedeemCode code = RedeemCode.builder()
                .campaignId(UUID.randomUUID())
                .codeHash("abc123")
                .maxRedemptions(1)
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();
        assertThat(code.isRedeemable()).isFalse();
    }

    @Test
    @DisplayName("사용횟수 소진된 코드는 redeemable이 아님")
    void depletedCode_isNotRedeemable() {
        RedeemCode code = RedeemCode.builder()
                .campaignId(UUID.randomUUID())
                .codeHash("abc123")
                .maxRedemptions(1)
                .build();
        code.incrementRedemptions();
        assertThat(code.isRedeemable()).isFalse();
    }

    @Test
    @DisplayName("다중 사용 코드는 한도까지 redeemable")
    void multiUseCode_redeemableUntilLimit() {
        RedeemCode code = RedeemCode.builder()
                .campaignId(UUID.randomUUID())
                .codeHash("abc123")
                .maxRedemptions(3)
                .build();

        assertThat(code.isRedeemable()).isTrue();
        code.incrementRedemptions();
        assertThat(code.isRedeemable()).isTrue();
        code.incrementRedemptions();
        assertThat(code.isRedeemable()).isTrue();
        code.incrementRedemptions();
        assertThat(code.isRedeemable()).isFalse();
    }
}
