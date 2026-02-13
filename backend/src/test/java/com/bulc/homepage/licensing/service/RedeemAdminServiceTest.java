package com.bulc.homepage.licensing.service;

import com.bulc.homepage.entity.Product;
import com.bulc.homepage.licensing.domain.*;
import com.bulc.homepage.licensing.dto.*;
import com.bulc.homepage.licensing.exception.LicenseException;
import com.bulc.homepage.licensing.exception.LicenseException.ErrorCode;
import com.bulc.homepage.licensing.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedeemAdminServiceTest {

    @Mock private RedeemCampaignRepository campaignRepository;
    @Mock private RedeemCodeRepository codeRepository;
    @Mock private LicensePlanRepository planRepository;
    @Mock private ProductRepository productRepository;
    @Mock private RedeemCodeHashService hashService;

    private RedeemAdminService adminService;

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();
    private static final UUID CAMPAIGN_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adminService = new RedeemAdminService(
                campaignRepository, codeRepository, planRepository,
                productRepository, hashService
        );

        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
        ReflectionTestUtils.setField(product, "name", "Test Product");
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

        LicensePlan plan = LicensePlan.builder()
                .productId(PRODUCT_ID).code("PLAN").name("Test Plan")
                .licenseType(LicenseType.SUBSCRIPTION)
                .durationDays(30).graceDays(7).maxActivations(3)
                .maxConcurrentSessions(2).allowOfflineDays(30).build();
        ReflectionTestUtils.setField(plan, "id", PLAN_ID);
        given(planRepository.findById(PLAN_ID)).willReturn(Optional.of(plan));
    }

    @Nested
    @DisplayName("캠페인 CRUD")
    class CampaignCrud {

        @Test
        @DisplayName("캠페인 생성 성공")
        void createCampaign_success() {
            RedeemCampaignRequest request = new RedeemCampaignRequest(
                    "Test Campaign", "desc", PRODUCT_ID, PLAN_ID,
                    UsageCategory.COMMERCIAL, 100, 1, null, null
            );

            given(campaignRepository.save(any())).willAnswer(inv -> {
                RedeemCampaign c = inv.getArgument(0);
                ReflectionTestUtils.setField(c, "id", CAMPAIGN_ID);
                return c;
            });
            given(codeRepository.countByCampaignId(any())).willReturn(0L);

            RedeemCampaignResponse response = adminService.createCampaign(request, ADMIN_ID);
            assertThat(response.name()).isEqualTo("Test Campaign");
            assertThat(response.productName()).isEqualTo("Test Product");
        }

        @Test
        @DisplayName("존재하지 않는 플랜 → PLAN_NOT_FOUND")
        void createCampaign_planNotFound() {
            RedeemCampaignRequest request = new RedeemCampaignRequest(
                    "Test", null, PRODUCT_ID, UUID.randomUUID(),
                    null, null, 1, null, null
            );

            assertThatThrownBy(() -> adminService.createCampaign(request, ADMIN_ID))
                    .isInstanceOf(LicenseException.class)
                    .matches(e -> ((LicenseException) e).getErrorCode() == ErrorCode.PLAN_NOT_FOUND);
        }

        @Test
        @DisplayName("캠페인 목록 조회")
        void listCampaigns() {
            RedeemCampaign campaign = createCampaign();
            Page<RedeemCampaign> page = new PageImpl<>(List.of(campaign));
            given(campaignRepository.findAll(any(PageRequest.class))).willReturn(page);
            given(codeRepository.countByCampaignId(any())).willReturn(5L);

            Page<RedeemCampaignResponse> result = adminService.listCampaigns(PageRequest.of(0, 10), null);
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).codeCount()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("코드 생성")
    class CodeGeneration {

        @Test
        @DisplayName("랜덤 코드 N개 생성")
        void generateRandomCodes() {
            RedeemCampaign campaign = createCampaign();
            given(campaignRepository.findById(CAMPAIGN_ID)).willReturn(Optional.of(campaign));
            given(hashService.generateRandomCode()).willReturn("ABCDEFGH12345678");
            given(hashService.hash(any())).willReturn("hash" + System.nanoTime()); // 매번 다른 해시
            given(hashService.formatForDisplay(any())).willReturn("ABCD-EFGH-1234-5678");
            given(codeRepository.existsByCodeHash(any())).willReturn(false);
            given(codeRepository.save(any())).willReturn(null);

            RedeemCodeGenerateRequest request = new RedeemCodeGenerateRequest(
                    CAMPAIGN_ID, RedeemCodeType.RANDOM, null, 5, 1, null
            );

            RedeemCodeGenerateResponse response = adminService.generateCodes(request);
            assertThat(response.generatedCount()).isEqualTo(5);
            assertThat(response.codes()).hasSize(5);
            verify(codeRepository, times(5)).save(any());
        }

        @Test
        @DisplayName("커스텀 코드 1개 생성")
        void generateCustomCode() {
            RedeemCampaign campaign = createCampaign();
            given(campaignRepository.findById(CAMPAIGN_ID)).willReturn(Optional.of(campaign));
            given(hashService.normalize("MYCODE12")).willReturn("MYCODE12");
            given(hashService.hash("MYCODE12")).willReturn("customhash");
            given(hashService.formatForDisplay("MYCODE12")).willReturn("MYCODE12");
            given(codeRepository.existsByCodeHash("customhash")).willReturn(false);
            given(codeRepository.save(any())).willReturn(null);

            RedeemCodeGenerateRequest request = new RedeemCodeGenerateRequest(
                    CAMPAIGN_ID, RedeemCodeType.CUSTOM, "MYCODE12", 1, 1, null
            );

            RedeemCodeGenerateResponse response = adminService.generateCodes(request);
            assertThat(response.generatedCount()).isEqualTo(1);
            assertThat(response.codes()).containsExactly("MYCODE12");
        }

        @Test
        @DisplayName("중복 코드 → REDEEM_CODE_HASH_DUPLICATE")
        void generateCustomCode_duplicate() {
            RedeemCampaign campaign = createCampaign();
            given(campaignRepository.findById(CAMPAIGN_ID)).willReturn(Optional.of(campaign));
            given(hashService.normalize(any())).willReturn("MYCODE12");
            given(hashService.hash(any())).willReturn("duphash");
            given(codeRepository.existsByCodeHash("duphash")).willReturn(true);

            RedeemCodeGenerateRequest request = new RedeemCodeGenerateRequest(
                    CAMPAIGN_ID, RedeemCodeType.CUSTOM, "MYCODE12", 1, 1, null
            );

            assertThatThrownBy(() -> adminService.generateCodes(request))
                    .isInstanceOf(LicenseException.class)
                    .matches(e -> ((LicenseException) e).getErrorCode() == ErrorCode.REDEEM_CODE_HASH_DUPLICATE);
        }
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
}
