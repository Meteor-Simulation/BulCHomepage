package com.bulc.homepage.licensing.service;

import com.bulc.homepage.entity.Product;
import com.bulc.homepage.licensing.domain.*;
import com.bulc.homepage.licensing.dto.*;
import com.bulc.homepage.licensing.exception.LicenseException;
import com.bulc.homepage.licensing.exception.LicenseException.ErrorCode;
import com.bulc.homepage.licensing.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
public class RedeemAdminService {

    private final RedeemCampaignRepository campaignRepository;
    private final RedeemCodeRepository codeRepository;
    private final LicensePlanRepository planRepository;
    private final ProductRepository productRepository;
    private final RedeemCodeHashService hashService;

    public RedeemAdminService(RedeemCampaignRepository campaignRepository,
                              RedeemCodeRepository codeRepository,
                              LicensePlanRepository planRepository,
                              ProductRepository productRepository,
                              RedeemCodeHashService hashService) {
        this.campaignRepository = campaignRepository;
        this.codeRepository = codeRepository;
        this.planRepository = planRepository;
        this.productRepository = productRepository;
        this.hashService = hashService;
    }

    public Page<RedeemCampaignResponse> listCampaigns(Pageable pageable, RedeemCampaignStatus status) {
        Page<RedeemCampaign> campaigns = status != null
                ? campaignRepository.findByStatus(status, pageable)
                : campaignRepository.findAll(pageable);

        return campaigns.map(this::toResponse);
    }

    public RedeemCampaignResponse getCampaign(UUID id) {
        RedeemCampaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new LicenseException(ErrorCode.REDEEM_CAMPAIGN_NOT_FOUND));
        return toResponse(campaign);
    }

    @Transactional
    public RedeemCampaignResponse createCampaign(RedeemCampaignRequest request, UUID adminId) {
        // 플랜 존재 확인
        planRepository.findById(request.licensePlanId())
                .orElseThrow(() -> new LicenseException(ErrorCode.PLAN_NOT_FOUND));

        RedeemCampaign campaign = RedeemCampaign.builder()
                .name(request.name())
                .description(request.description())
                .productId(request.productId())
                .licensePlanId(request.licensePlanId())
                .usageCategory(request.usageCategory())
                .seatLimit(request.seatLimit())
                .perUserLimit(request.perUserLimit())
                .validFrom(request.validFrom())
                .validUntil(request.validUntil())
                .createdBy(adminId)
                .build();

        RedeemCampaign saved = campaignRepository.save(campaign);
        return toResponse(saved);
    }

    @Transactional
    public RedeemCampaignResponse updateCampaign(UUID id, RedeemCampaignRequest request) {
        RedeemCampaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new LicenseException(ErrorCode.REDEEM_CAMPAIGN_NOT_FOUND));

        campaign.update(
                request.name(),
                request.description(),
                request.usageCategory(),
                request.seatLimit(),
                request.perUserLimit(),
                request.validFrom(),
                request.validUntil()
        );

        return toResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public void pauseCampaign(UUID id) {
        RedeemCampaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new LicenseException(ErrorCode.REDEEM_CAMPAIGN_NOT_FOUND));
        campaign.pause();
        campaignRepository.save(campaign);
    }

    @Transactional
    public void endCampaign(UUID id) {
        RedeemCampaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new LicenseException(ErrorCode.REDEEM_CAMPAIGN_NOT_FOUND));
        campaign.end();
        campaignRepository.save(campaign);
    }

    @Transactional
    public void resumeCampaign(UUID id) {
        RedeemCampaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new LicenseException(ErrorCode.REDEEM_CAMPAIGN_NOT_FOUND));
        campaign.resume();
        campaignRepository.save(campaign);
    }

    @Transactional
    public RedeemCodeGenerateResponse generateCodes(RedeemCodeGenerateRequest request) {
        RedeemCampaign campaign = campaignRepository.findById(request.campaignId())
                .orElseThrow(() -> new LicenseException(ErrorCode.REDEEM_CAMPAIGN_NOT_FOUND));

        List<String> rawCodes = new ArrayList<>();

        if (request.codeType() == RedeemCodeType.CUSTOM) {
            // CUSTOM: 1개만 생성
            String normalized = hashService.normalize(request.customCode());
            hashService.validate(normalized);
            String codeHash = hashService.hash(normalized);

            if (codeRepository.existsByCodeHash(codeHash)) {
                throw new LicenseException(ErrorCode.REDEEM_CODE_HASH_DUPLICATE);
            }

            RedeemCode code = RedeemCode.builder()
                    .campaignId(campaign.getId())
                    .codeHash(codeHash)
                    .codeType(RedeemCodeType.CUSTOM)
                    .maxRedemptions(request.maxRedemptions())
                    .expiresAt(request.expiresAt())
                    .build();
            codeRepository.save(code);
            rawCodes.add(hashService.formatForDisplay(normalized));

        } else {
            // RANDOM: N개 생성
            int count = Math.max(1, request.count());
            for (int i = 0; i < count; i++) {
                String rawCode;
                String codeHash;
                int retries = 0;
                do {
                    rawCode = hashService.generateRandomCode();
                    codeHash = hashService.hash(rawCode);
                    retries++;
                    if (retries > 100) {
                        throw new RuntimeException("코드 생성에 실패했습니다. 해시 충돌이 반복됩니다.");
                    }
                } while (codeRepository.existsByCodeHash(codeHash));

                RedeemCode code = RedeemCode.builder()
                        .campaignId(campaign.getId())
                        .codeHash(codeHash)
                        .codeType(RedeemCodeType.RANDOM)
                        .maxRedemptions(request.maxRedemptions())
                        .expiresAt(request.expiresAt())
                        .build();
                codeRepository.save(code);
                rawCodes.add(hashService.formatForDisplay(rawCode));
            }
        }

        log.info("리딤 코드 생성 완료: campaignId={}, type={}, count={}",
                campaign.getId(), request.codeType(), rawCodes.size());

        return new RedeemCodeGenerateResponse(rawCodes.size(), rawCodes);
    }

    public Page<RedeemCodeResponse> listCodes(UUID campaignId, Pageable pageable) {
        return codeRepository.findByCampaignId(campaignId, pageable)
                .map(RedeemCodeResponse::fromEntity);
    }

    @Transactional
    public void deactivateCode(UUID codeId) {
        RedeemCode code = codeRepository.findById(codeId)
                .orElseThrow(() -> new LicenseException(ErrorCode.REDEEM_CODE_NOT_FOUND));
        code.deactivate();
        codeRepository.save(code);
    }

    private RedeemCampaignResponse toResponse(RedeemCampaign campaign) {
        String productName = productRepository.findById(campaign.getProductId())
                .map(Product::getName)
                .orElse("알 수 없는 제품");

        String planName = planRepository.findById(campaign.getLicensePlanId())
                .map(LicensePlan::getName)
                .orElse("알 수 없는 플랜");

        long codeCount = codeRepository.countByCampaignId(campaign.getId());

        return RedeemCampaignResponse.fromEntity(campaign, productName, planName, codeCount);
    }
}
