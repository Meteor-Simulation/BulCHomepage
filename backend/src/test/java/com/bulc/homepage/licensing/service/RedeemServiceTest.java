package com.bulc.homepage.licensing.service;

import com.bulc.homepage.entity.Product;
import com.bulc.homepage.licensing.domain.*;
import com.bulc.homepage.licensing.dto.RedeemClaimResponse;
import com.bulc.homepage.licensing.exception.LicenseException;
import com.bulc.homepage.licensing.exception.LicenseException.ErrorCode;
import com.bulc.homepage.licensing.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedeemServiceTest {

    @Mock private RedeemCodeRepository codeRepository;
    @Mock private RedeemCampaignRepository campaignRepository;
    @Mock private RedeemRedemptionRepository redemptionRepository;
    @Mock private RedeemUserCampaignCounterRepository counterRepository;
    @Mock private LicenseService licenseService;
    @Mock private LicensePlanRepository planRepository;
    @Mock private ProductRepository productRepository;
    @Mock private RedeemCodeHashService hashService;
    @Mock private RedeemRateLimiter rateLimiter;

    private RedeemService redeemService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CAMPAIGN_ID = UUID.randomUUID();
    private static final UUID CODE_ID = UUID.randomUUID();
    private static final UUID LICENSE_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        redeemService = new RedeemService(
                codeRepository, campaignRepository, redemptionRepository,
                counterRepository, licenseService, planRepository,
                productRepository, hashService, rateLimiter
        );

        // 기본 mock 설정
        given(rateLimiter.isAllowed(any())).willReturn(true);
        given(hashService.normalize(any())).willReturn("ABCDEFGH12345678");
        given(hashService.hash(any())).willReturn("somehash");
    }

    @Test
    @DisplayName("정상 Claim 성공")
    void claim_success() {
        // given
        RedeemCode code = createCode();
        RedeemCampaign campaign = createCampaign();
        License license = createLicense();

        given(codeRepository.findByCodeHash(any())).willReturn(Optional.of(code));
        given(campaignRepository.findById(any())).willReturn(Optional.of(campaign));
        given(codeRepository.incrementRedemptionsAtomically(any())).willReturn(1);
        given(campaignRepository.incrementSeatsUsedAtomically(any())).willReturn(1);
        given(counterRepository.findByUserIdAndCampaignId(any(), any())).willReturn(Optional.empty());
        given(counterRepository.save(any())).willReturn(null);
        given(licenseService.issueLicenseForRedeem(any(), any(), any())).willReturn(license);
        given(redemptionRepository.save(any())).willReturn(null);
        given(productRepository.findById(any())).willReturn(Optional.of(createProduct()));
        given(planRepository.findById(any())).willReturn(Optional.of(createPlan()));

        // when
        RedeemClaimResponse response = redeemService.claim(USER_ID, "ABCD-EFGH-1234-5678", "127.0.0.1", "test-agent");

        // then
        assertThat(response).isNotNull();
        assertThat(response.licenseId()).isEqualTo(LICENSE_ID);
        verify(licenseService).issueLicenseForRedeem(eq(USER_ID), eq(PLAN_ID), eq(UsageCategory.COMMERCIAL));
    }

    @Test
    @DisplayName("존재하지 않는 코드 → REDEEM_CODE_NOT_FOUND")
    void claim_invalidCode() {
        given(codeRepository.findByCodeHash(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> redeemService.claim(USER_ID, "INVALID", "127.0.0.1", null))
                .isInstanceOf(LicenseException.class)
                .matches(e -> ((LicenseException) e).getErrorCode() == ErrorCode.REDEEM_CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("비활성 코드 → REDEEM_CODE_DISABLED")
    void claim_disabledCode() {
        RedeemCode code = createCode();
        code.deactivate();
        given(codeRepository.findByCodeHash(any())).willReturn(Optional.of(code));

        assertThatThrownBy(() -> redeemService.claim(USER_ID, "CODE", "127.0.0.1", null))
                .isInstanceOf(LicenseException.class)
                .matches(e -> ((LicenseException) e).getErrorCode() == ErrorCode.REDEEM_CODE_DISABLED);
    }

    @Test
    @DisplayName("만료된 코드 → REDEEM_CODE_EXPIRED")
    void claim_expiredCode() {
        RedeemCode code = RedeemCode.builder()
                .campaignId(CAMPAIGN_ID)
                .codeHash("hash")
                .maxRedemptions(1)
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();
        ReflectionTestUtils.setField(code, "id", CODE_ID);
        ReflectionTestUtils.setField(code, "active", true);
        given(codeRepository.findByCodeHash(any())).willReturn(Optional.of(code));

        assertThatThrownBy(() -> redeemService.claim(USER_ID, "CODE", "127.0.0.1", null))
                .isInstanceOf(LicenseException.class)
                .matches(e -> ((LicenseException) e).getErrorCode() == ErrorCode.REDEEM_CODE_EXPIRED);
    }

    @Test
    @DisplayName("코드 사용횟수 소진 → REDEEM_CODE_DEPLETED")
    void claim_depletedCode() {
        RedeemCode code = createCode();
        RedeemCampaign campaign = createCampaign();

        given(codeRepository.findByCodeHash(any())).willReturn(Optional.of(code));
        given(campaignRepository.findById(any())).willReturn(Optional.of(campaign));
        given(codeRepository.incrementRedemptionsAtomically(any())).willReturn(0); // 실패

        assertThatThrownBy(() -> redeemService.claim(USER_ID, "CODE", "127.0.0.1", null))
                .isInstanceOf(LicenseException.class)
                .matches(e -> ((LicenseException) e).getErrorCode() == ErrorCode.REDEEM_CODE_DEPLETED);
    }

    @Test
    @DisplayName("캠페인 좌석 소진 → REDEEM_CAMPAIGN_FULL")
    void claim_campaignFull() {
        RedeemCode code = createCode();
        RedeemCampaign campaign = createCampaign();

        given(codeRepository.findByCodeHash(any())).willReturn(Optional.of(code));
        given(campaignRepository.findById(any())).willReturn(Optional.of(campaign));
        given(codeRepository.incrementRedemptionsAtomically(any())).willReturn(1);
        given(campaignRepository.incrementSeatsUsedAtomically(any())).willReturn(0); // 실패

        assertThatThrownBy(() -> redeemService.claim(USER_ID, "CODE", "127.0.0.1", null))
                .isInstanceOf(LicenseException.class)
                .matches(e -> ((LicenseException) e).getErrorCode() == ErrorCode.REDEEM_CAMPAIGN_FULL);
    }

    @Test
    @DisplayName("사용자별 한도 초과 → REDEEM_USER_LIMIT_EXCEEDED")
    void claim_userLimitExceeded() {
        RedeemCode code = createCode();
        RedeemCampaign campaign = createCampaign();
        RedeemUserCampaignCounter counter = new RedeemUserCampaignCounter(USER_ID, CAMPAIGN_ID);
        counter.increment(); // count = 1, perUserLimit = 1

        given(codeRepository.findByCodeHash(any())).willReturn(Optional.of(code));
        given(campaignRepository.findById(any())).willReturn(Optional.of(campaign));
        given(codeRepository.incrementRedemptionsAtomically(any())).willReturn(1);
        given(campaignRepository.incrementSeatsUsedAtomically(any())).willReturn(1);
        given(counterRepository.findByUserIdAndCampaignId(any(), any())).willReturn(Optional.of(counter));

        assertThatThrownBy(() -> redeemService.claim(USER_ID, "CODE", "127.0.0.1", null))
                .isInstanceOf(LicenseException.class)
                .matches(e -> ((LicenseException) e).getErrorCode() == ErrorCode.REDEEM_USER_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("Rate limit 초과 → REDEEM_RATE_LIMITED")
    void claim_rateLimited() {
        given(rateLimiter.isAllowed(USER_ID)).willReturn(false);

        assertThatThrownBy(() -> redeemService.claim(USER_ID, "CODE", "127.0.0.1", null))
                .isInstanceOf(LicenseException.class)
                .matches(e -> ((LicenseException) e).getErrorCode() == ErrorCode.REDEEM_RATE_LIMITED);
    }

    // === Helper methods ===

    private RedeemCode createCode() {
        RedeemCode code = RedeemCode.builder()
                .campaignId(CAMPAIGN_ID)
                .codeHash("somehash")
                .codeType(RedeemCodeType.RANDOM)
                .maxRedemptions(1)
                .build();
        ReflectionTestUtils.setField(code, "id", CODE_ID);
        return code;
    }

    private RedeemCampaign createCampaign() {
        RedeemCampaign campaign = RedeemCampaign.builder()
                .name("Test Campaign")
                .productId(PRODUCT_ID)
                .licensePlanId(PLAN_ID)
                .usageCategory(UsageCategory.COMMERCIAL)
                .seatLimit(100)
                .perUserLimit(1)
                .build();
        ReflectionTestUtils.setField(campaign, "id", CAMPAIGN_ID);
        return campaign;
    }

    private License createLicense() {
        License license = License.builder()
                .ownerType(OwnerType.USER)
                .ownerId(USER_ID)
                .productId(PRODUCT_ID)
                .planId(PLAN_ID)
                .licenseType(LicenseType.SUBSCRIPTION)
                .usageCategory(UsageCategory.COMMERCIAL)
                .validUntil(Instant.now().plus(30, ChronoUnit.DAYS))
                .licenseKey("TEST-KEY-1234")
                .sourceType(LicenseSourceType.REDEEM)
                .build();
        ReflectionTestUtils.setField(license, "id", LICENSE_ID);
        return license;
    }

    private Product createProduct() {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
        ReflectionTestUtils.setField(product, "name", "Test Product");
        return product;
    }

    private LicensePlan createPlan() {
        LicensePlan plan = LicensePlan.builder()
                .productId(PRODUCT_ID)
                .code("TEST-PLAN")
                .name("Test Plan")
                .licenseType(LicenseType.SUBSCRIPTION)
                .durationDays(30)
                .graceDays(7)
                .maxActivations(3)
                .maxConcurrentSessions(2)
                .allowOfflineDays(30)
                .build();
        ReflectionTestUtils.setField(plan, "id", PLAN_ID);
        return plan;
    }
}
