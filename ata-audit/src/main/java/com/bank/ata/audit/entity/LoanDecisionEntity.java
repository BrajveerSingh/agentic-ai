package com.bank.ata.audit.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Captures the final loan decision with reasoning and confidence score.
 * Immutable once created.
 */
@Entity
@Table(name = "loan_decision", indexes = {
    @Index(name = "idx_loan_decision_app", columnList = "application_id"),
    @Index(name = "idx_loan_decision_session", columnList = "session_id")
})
public class LoanDecisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "decision_id", updatable = false, nullable = false)
    private UUID decisionId;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "outcome", nullable = false, updatable = false, length = 50)
    private String outcome;

    @Column(name = "reasoning", columnDefinition = "TEXT", updatable = false)
    private String reasoning;

    @Column(name = "confidence_score", precision = 5, scale = 4, updatable = false)
    private BigDecimal confidenceScore;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;

    protected LoanDecisionEntity() {
        // JPA required
    }

    public LoanDecisionEntity(UUID applicationId, UUID sessionId, String outcome,
                               String reasoning, double confidenceScore) {
        this.applicationId = applicationId;
        this.sessionId = sessionId;
        this.outcome = outcome;
        this.reasoning = reasoning;
        this.confidenceScore = BigDecimal.valueOf(confidenceScore);
        this.decidedAt = Instant.now();
    }

    // Getters only - immutable
    public UUID getDecisionId() { return decisionId; }
    public UUID getApplicationId() { return applicationId; }
    public UUID getSessionId() { return sessionId; }
    public String getOutcome() { return outcome; }
    public String getReasoning() { return reasoning; }
    public BigDecimal getConfidenceScore() { return confidenceScore; }
    public Instant getDecidedAt() { return decidedAt; }

    @Override
    public String toString() {
        return "LoanDecisionEntity{" +
                "decisionId=" + decisionId +
                ", applicationId=" + applicationId +
                ", outcome='" + outcome + '\'' +
                ", confidenceScore=" + confidenceScore +
                '}';
    }
}

