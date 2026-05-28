package com.bulc.homepage.email;

public enum EmailCategory {
    ACCOUNT,
    TRANSACTION,
    OPERATIONAL,
    PROMOTIONAL;

    public boolean requiresMarketingConsent() {
        return this == PROMOTIONAL;
    }
}
