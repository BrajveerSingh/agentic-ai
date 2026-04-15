package com.bank.ata.core.domain;

import java.time.LocalDate;

/**
 * Result of KYC (Know Your Customer) verification.
 */
public record KycResult(
        String customerId,
        boolean verified,
        LocalDate verificationDate,
        String verificationLevel
) {
    public KycResult(String customerId, boolean verified, LocalDate verificationDate) {
        this(customerId, verified, verificationDate, verified ? "FULL" : "NONE");
    }

    /**
     * Check if KYC is current (within last 12 months).
     */
    public boolean isCurrent() {
        return verified && verificationDate.isAfter(LocalDate.now().minusMonths(12));
    }
}

