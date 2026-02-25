package com.bulc.homepage.licensing.service;

import com.bulc.homepage.entity.Product;
import com.bulc.homepage.licensing.domain.*;
import com.bulc.homepage.licensing.dto.RedeemClaimResponse;
import com.bulc.homepage.licensing.exception.LicenseException;
import com.bulc.homepage.licensing.exception.LicenseException.ErrorCode;
import com.bulc.homepage.licensing.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
public class RedeemService {

    private final RedeemCodeRepository codeRepository;
    private final RedeemCampaignRepository campaignRepository;
    private final RedeemRedemptionRepository redemptionRepository;
    private final RedeemUserCampaignCounterRepository counterRepository;
    private final LicenseService licenseService;
    private final LicensePlanRepository planRepository;
    private final ProductRepository productRepository;
    private final RedeemCodeHashService hashService;
    private final RedeemRateLimiter rateLimiter;

    public RedeemService(RedeemCodeRepository codeRepository,
                         RedeemCampaignRepository campaignRepository,
                         RedeemRedemptionRepository redemptionRepository,
                         RedeemUserCampaignCounterRepository counterRepository,
                         LicenseService licenseService,
                         LicensePlanRepository planRepository,
                         ProductRepository productRepository,
                         RedeemCodeHashService hashService,
                         RedeemRateLimiter rateLimiter) {
        this.codeRepository = codeRepository;
        this.campaignRepository = campaignRepository;
        this.redemptionRepository = redemptionRepository;
        this.counterRepository = counterRepository;
        this.licenseService = licenseService;
        this.planRepository = planRepository;
        this.productRepository = productRepository;
        this.hashService = hashService;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public RedeemClaimResponse claim(UUID userId, String rawCode, String ipAddress, String userAgent) {
        // Rate limit 확인
        if (!rateLimiter.isAllowed(userId)) {
            throw new LicenseException(ErrorCode.REDEEM_RATE_LIMITED);
        }

        // 1. 정규화 → 검증 → 해시 → 코드 조회
        String normalized = hashService.normalize(rawCode);
        hashService.validate(normalized);
        String codeHash = hashService.hash(normalized);

        RedeemCode code = codeRepository.findByCodeHash(codeHash)
                .orElseThrow(() -> new LicenseException(ErrorCode.REDEEM_CODE_NOT_FOUND));

        // 2. 코드 상태 확인
        if (!code.isActive()) {
            throw new LicenseException(ErrorCode.REDEEM_CODE_DISABLED);
        }
        if (code.getExpiresAt() != null && Instant.now().isAfter(code.getExpiresAt())) {
            throw new LicenseException(ErrorCode.REDEEM_CODE_EXPIRED);
        }

        // 3. 캠페인 상태 확인
        RedeemCampaign campaign = campaignRepository.findById(code.getCampaignId())
                .orElseThrow(() -> new LicenseException(ErrorCode.REDEEM_CAMPAIGN_NOT_FOUND));

        if (!campaign.isAvailable()) {
            throw new LicenseException(ErrorCode.REDEEM_CAMPAIGN_NOT_ACTIVE);
        }

        // 4. 원자 코드 사용횟수 증가
        int codeUpdated = codeRepository.incrementRedemptionsAtomically(code.getId());
        if (codeUpdated == 0) {
            throw new LicenseException(ErrorCode.REDEEM_CODE_DEPLETED);
        }

        // 5. 원자 캠페인 좌석 증가
        int campaignUpdated = campaignRepository.incrementSeatsUsedAtomically(campaign.getId());
        if (campaignUpdated == 0) {
            throw new LicenseException(ErrorCode.REDEEM_CAMPAIGN_FULL);
        }

        // 6. 사용자별 카운터 증가 (서비스 레이어 fallback)
        RedeemUserCampaignCounter counter = counterRepository
                .findByUserIdAndCampaignId(userId, campaign.getId())
                .orElseGet(() -> new RedeemUserCampaignCounter(userId, campaign.getId()));

        if (counter.getCount() >= campaign.getPerUserLimit()) {
            throw new LicenseException(ErrorCode.REDEEM_USER_LIMIT_EXCEEDED);
        }
        counter.increment();
        counterRepository.save(counter);

        // 7. 라이선스 발급
        License license = licenseService.issueLicenseForRedeem(
                userId,
                campaign.getLicensePlanId(),
                campaign.getUsageCategory()
        );

        // 8. 감사 로그 기록
        RedeemRedemption redemption = RedeemRedemption.builder()
                .codeId(code.getId())
                .campaignId(campaign.getId())
                .userId(userId)
                .licenseId(license.getId())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();
        redemptionRepository.save(redemption);

        log.info("리딤 코드 사용 완료: userId={}, campaignId={}, licenseId={}",
                userId, campaign.getId(), license.getId());

        // 응답 빌드
        String productName = productRepository.findById(campaign.getProductId())
                .map(Product::getName)
                .orElse("알 수 없는 제품");

        String planName = planRepository.findById(campaign.getLicensePlanId())
                .map(LicensePlan::getName)
                .orElse("알 수 없는 플랜");

        return new RedeemClaimResponse(
                license.getId(),
                license.getLicenseKey(),
                productName,
                planName,
                license.getValidUntil()
        );
    }
}
