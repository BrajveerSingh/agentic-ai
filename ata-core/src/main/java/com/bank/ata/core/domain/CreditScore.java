package com.bank.ata.core.domain;

import java.time.LocalDate;

/**
 * Credit score information for a customer.
 */
public record CreditScore(
        String customerId,
        int score,
        String rating,
        LocalDate asOfDate
) {
    public CreditScore(String customerId, int score, String rating) {
        this(customerId, score, rating, LocalDate.now());
    }

    /**
     * Determine if credit score meets minimum requirements.
     */
    public boolean meetsMinimum(int minimum) {
        return score >= minimum;
    }

    /**
     * Get risk level based on score.
     */
    public String getRiskLevel() {
        if (score >= 750) return "LOW";
        if (score >= 650) return "MEDIUM";
        return "HIGH";
    }
}

