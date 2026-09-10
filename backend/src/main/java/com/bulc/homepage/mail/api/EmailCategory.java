package com.bulc.homepage.mail.api;

public enum EmailCategory {
    ACCOUNT,
    TRANSACTION,
    OPERATIONAL,
    PROMOTIONAL;

    public boolean requiresMarketingConsent() {
        return this == PROMOTIONAL;
    }
}
