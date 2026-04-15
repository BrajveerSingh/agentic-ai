package com.bank.ata.core.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Represents the decision made by the agent for a loan application.
 */
public record LoanDecision(
        UUID decisionId,
        UUID applicationId,
        DecisionOutcome outcome,
        String reasoning,
        List<String> auditEventIds,
        double confidenceScore,
        LocalDateTime timestamp
) {
    /**
     * Create a new loan decision.
     */
    public static LoanDecision create(UUID applicationId, DecisionOutcome outcome,
                                       String reasoning, double confidenceScore) {
        return new LoanDecision(
                UUID.randomUUID(),
                applicationId,
                outcome,
                reasoning,
                List.of(),
                confidenceScore,
                LocalDateTime.now()
        );
    }

    /**
     * Create decision with audit trail.
     */
    public LoanDecision withAuditEvents(List<String> eventIds) {
        return new LoanDecision(
                decisionId, applicationId, outcome, reasoning,
                eventIds, confidenceScore, timestamp
        );
    }
}

