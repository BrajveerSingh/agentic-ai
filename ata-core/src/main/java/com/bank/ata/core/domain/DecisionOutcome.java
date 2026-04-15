package com.bank.ata.core.domain;

/**
 * Possible outcomes for a loan decision.
 */
public enum DecisionOutcome {
    APPROVED,
    REJECTED,
    PENDING_REVIEW,
    REQUIRES_ADDITIONAL_INFO
}

