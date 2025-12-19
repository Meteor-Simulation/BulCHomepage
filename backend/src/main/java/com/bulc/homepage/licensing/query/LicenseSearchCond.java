package com.bulc.homepage.licensing.query;

import com.bulc.homepage.licensing.domain.LicenseStatus;
import com.bulc.homepage.licensing.domain.LicenseType;
import com.bulc.homepage.licensing.domain.OwnerType;
import com.bulc.homepage.licensing.domain.UsageCategory;

import java.util.UUID;

/**
 * 라이선스 검색 조건
 */
public record LicenseSearchCond(
        OwnerType ownerType,
        UUID ownerId,
        UUID productId,
        UUID planId,
        LicenseStatus status,
        LicenseType licenseType,
        UsageCategory usageCategory,
        String licenseKey
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private OwnerType ownerType;
        private UUID ownerId;
        private UUID productId;
        private UUID planId;
        private LicenseStatus status;
        private LicenseType licenseType;
        private UsageCategory usageCategory;
        private String licenseKey;

        public Builder ownerType(OwnerType ownerType) {
            this.ownerType = ownerType;
            return this;
        }

        public Builder ownerId(UUID ownerId) {
            this.ownerId = ownerId;
            return this;
        }

        public Builder productId(UUID productId) {
            this.productId = productId;
            return this;
        }

        public Builder planId(UUID planId) {
            this.planId = planId;
            return this;
        }

        public Builder status(LicenseStatus status) {
            this.status = status;
            return this;
        }

        public Builder licenseType(LicenseType licenseType) {
            this.licenseType = licenseType;
            return this;
        }

        public Builder usageCategory(UsageCategory usageCategory) {
            this.usageCategory = usageCategory;
            return this;
        }

        public Builder licenseKey(String licenseKey) {
            this.licenseKey = licenseKey;
            return this;
        }

        public LicenseSearchCond build() {
            return new LicenseSearchCond(
                    ownerType, ownerId, productId, planId,
                    status, licenseType, usageCategory, licenseKey
            );
        }
    }
}
