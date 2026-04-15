package com.bank.ata.core.domain;

/**
 * Risk score calculation result.
 */
public record RiskScore(
        double score,
        String level,
        String explanation
) {
    public RiskScore(double score, String level) {
        this(score, level, generateExplanation(score, level));
    }

    private static String generateExplanation(double score, String level) {
        return String.format("Risk score %.2f indicates %s risk level", score, level);
    }

    /**
     * Check if risk is acceptable for automatic approval.
     */
    public boolean isAcceptable() {
        return score < 0.5;
    }

    /**
     * Check if manual review is required.
     */
    public boolean requiresManualReview() {
        return score >= 0.5 && score < 0.7;
    }

    /**
     * Check if automatic rejection is warranted.
     */
    public boolean shouldReject() {
        return score >= 0.7;
    }
}

