package com.bulc.homepage.licensing.adapter;

import com.bulc.homepage.licensing.domain.OwnerType;
import com.bulc.homepage.licensing.domain.UsageCategory;
import com.bulc.homepage.licensing.dto.LicenseIssueResult;
import com.bulc.homepage.licensing.exception.LicenseException;
import com.bulc.homepage.licensing.service.LicenseService;
import com.bulc.homepage.payment.port.IssuedLicense;
import com.bulc.homepage.payment.port.LicenseAlreadyOwnedException;
import com.bulc.homepage.payment.port.LicenseIssueFailedException;
import com.bulc.homepage.payment.port.LicenseIssuePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * 결제 모듈의 {@link LicenseIssuePort} 를 licensing 모듈로 연결한다.
 *
 * <p>결제가 쓰던 고정 인자(OwnerType.USER, UsageCategory.COMMERCIAL)와
 * licensing 예외 분기를 여기서 흡수해, 결제 쪽에 licensing 타입이 새지 않도록 한다.
 */
@Component
@RequiredArgsConstructor
public class PaymentLicenseIssueAdapter implements LicenseIssuePort {

    /** 결제로 발급되는 라이선스는 항상 개인 소유·상업용이다. */
    private static final OwnerType OWNER_TYPE = OwnerType.USER;
    private static final UsageCategory USAGE_CATEGORY = UsageCategory.COMMERCIAL;

    private final LicenseService licenseService;

    @Override
    public void requirePurchasable(UUID userId, UUID licensePlanId) {
        try {
            licenseService.requireUserCanPurchasePlan(userId, licensePlanId);
        } catch (LicenseException e) {
            if (e.getErrorCode() == LicenseException.ErrorCode.LICENSE_ALREADY_EXISTS) {
                throw new LicenseAlreadyOwnedException("이미 해당 제품의 라이선스를 보유하고 있습니다.");
            }
            throw new LicenseIssueFailedException(e.getMessage(), e);
        }
    }

    @Override
    public IssuedLicense issue(UUID userId, UUID licensePlanId, UUID sourceOrderId) {
        try {
            LicenseIssueResult result = licenseService.issueLicenseWithPlanForBilling(
                    OWNER_TYPE, userId, licensePlanId, sourceOrderId, USAGE_CATEGORY);
            return new IssuedLicense(result.id(), result.licenseKey(), result.validUntil());
        } catch (RuntimeException e) {
            throw new LicenseIssueFailedException(e.getMessage(), e);
        }
    }

    @Override
    public void renew(UUID userId, UUID licensePlanId, UUID sourceOrderId, Instant newValidUntil) {
        try {
            licenseService.renewSubscriptionLicense(
                    OWNER_TYPE, userId, licensePlanId, sourceOrderId, USAGE_CATEGORY, newValidUntil);
        } catch (RuntimeException e) {
            throw new LicenseIssueFailedException(e.getMessage(), e);
        }
    }
}
