package com.bank.ata.core.domain;

import java.util.List;

/**
 * Result of policy compliance check.
 */
public record PolicyResult(
        boolean compliant,
        List<String> ruleResults,
        List<String> violations
) {
    public PolicyResult(boolean compliant, List<String> ruleResults) {
        this(compliant, ruleResults, List.of());
    }

    /**
     * Check if all policies are satisfied.
     */
    public boolean isFullyCompliant() {
        return compliant && violations.isEmpty();
    }
}

