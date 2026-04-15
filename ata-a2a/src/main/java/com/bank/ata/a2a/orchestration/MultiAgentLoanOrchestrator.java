package com.bank.ata.a2a.orchestration;

import com.bank.ata.a2a.client.A2aClient;
import com.bank.ata.a2a.model.*;
import com.bank.ata.agent.AuditTrailAgent;
import com.bank.ata.core.domain.DecisionOutcome;
import com.bank.ata.core.domain.LoanApplication;
import com.bank.ata.core.domain.LoanDecision;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates a multi-agent loan evaluation:
 * <ol>
 *   <li>AuditTrailAgent (local) produces a base {@link LoanDecision}</li>
 *   <li>Risk Agent (peer, via A2A) returns a risk assessment</li>
 *   <li>Fraud Agent (peer, via A2A) returns a fraud check</li>
 *   <li>Results are merged; outcome is overridden to {@code PENDING_REVIEW}
 *       if either peer raises a concern</li>
 * </ol>
 *
 * <p>Peer-agent calls are best-effort — if a peer is unavailable or returns FAILED
 * the base decision is returned unchanged (graceful degradation).</p>
 */
@Service
public class MultiAgentLoanOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentLoanOrchestrator.class);

    private final AuditTrailAgent agent;
    private final A2aClient       riskAgentClient;
    private final A2aClient       fraudAgentClient;
    private final ObjectMapper    objectMapper;

    public MultiAgentLoanOrchestrator(
            AuditTrailAgent agent,
            @Qualifier("riskAgentClient")  A2aClient riskAgentClient,
            @Qualifier("fraudAgentClient") A2aClient fraudAgentClient,
            ObjectMapper objectMapper) {
        this.agent            = agent;
        this.riskAgentClient  = riskAgentClient;
        this.fraudAgentClient = fraudAgentClient;
        this.objectMapper     = objectMapper;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Evaluate a loan application using the local agent plus Risk and Fraud peers.
     *
     * @param application loan application to evaluate
     * @return merged LoanDecision (outcome may be overridden by peers)
     */
    public LoanDecision evaluateWithPeerAgents(LoanApplication application) {
        log.info("Starting multi-agent evaluation: applicationId={}", application.applicationId());

        // Step 1 — local agent evaluation
        LoanDecision baseDecision = agent.evaluateLoan(application);
        log.info("Base decision: outcome={} confidence={}",
                baseDecision.outcome(), baseDecision.confidenceScore());

        // Step 2 — risk assessment (best-effort)
        String riskSummary = callRiskAgent(application);

        // Step 3 — fraud check (best-effort)
        String fraudSummary = callFraudAgent(application);

        // Step 4 — merge
        return mergeDecision(baseDecision, riskSummary, fraudSummary);
    }

    // -------------------------------------------------------------------------
    // Peer calls
    // -------------------------------------------------------------------------

    private String callRiskAgent(LoanApplication application) {
        if (!riskAgentClient.isAvailable()) {
            log.warn("Risk agent unavailable — skipping risk assessment");
            return null;
        }
        try {
            A2aTask task   = buildLoanTask(application, "assess_risk");
            A2aTask result = riskAgentClient.sendTask(task);
            String  text   = firstArtifactText(result);
            log.info("Risk agent response: {}", text);
            return text;
        } catch (Exception e) {
            log.warn("Risk agent call failed (non-critical): {}", e.getMessage());
            return null;
        }
    }

    private String callFraudAgent(LoanApplication application) {
        if (!fraudAgentClient.isAvailable()) {
            log.warn("Fraud agent unavailable — skipping fraud check");
            return null;
        }
        try {
            A2aTask task   = buildLoanTask(application, "check_fraud");
            A2aTask result = fraudAgentClient.sendTask(task);
            String  text   = firstArtifactText(result);
            log.info("Fraud agent response: {}", text);
            return text;
        } catch (Exception e) {
            log.warn("Fraud agent call failed (non-critical): {}", e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Decision merging
    // -------------------------------------------------------------------------

    private LoanDecision mergeDecision(LoanDecision base, String riskSummary, String fraudSummary) {
        boolean riskFlagged  = isRiskHigh(riskSummary);
        boolean fraudFlagged = isFraudDetected(fraudSummary);

        if (!riskFlagged && !fraudFlagged) {
            log.info("No peer concerns — returning base decision");
            return base;
        }

        // Build an augmented reasoning string
        StringBuilder augmentedReasoning = new StringBuilder(base.reasoning());
        if (riskFlagged) {
            augmentedReasoning.append("\n[Risk Agent] ").append(riskSummary);
        }
        if (fraudFlagged) {
            augmentedReasoning.append("\n[Fraud Agent] ").append(fraudSummary);
        }

        // Downgrade to PENDING_REVIEW unless already REJECTED
        DecisionOutcome mergedOutcome = base.outcome() == DecisionOutcome.REJECTED
                ? DecisionOutcome.REJECTED
                : DecisionOutcome.PENDING_REVIEW;

        log.info("Peer agents flagged concerns — outcome overridden to {}", mergedOutcome);

        return LoanDecision.create(
                base.applicationId(),
                mergedOutcome,
                augmentedReasoning.toString(),
                base.confidenceScore()
        );
    }

    private boolean isRiskHigh(String riskSummary) {
        if (riskSummary == null) return false;
        String upper = riskSummary.toUpperCase();
        return upper.contains("HIGH") || upper.contains("\"riskScore\":0.7")
                || upper.contains("RISK_SCORE") && riskScoreAboveThreshold(riskSummary, 0.7);
    }

    private boolean isFraudDetected(String fraudSummary) {
        if (fraudSummary == null) return false;
        String upper = fraudSummary.toUpperCase();
        return upper.contains("\"FRAUDDETECTED\":TRUE")
                || upper.contains("FRAUD_DETECTED: TRUE")
                || upper.contains("FRAUD DETECTED");
    }

    private boolean riskScoreAboveThreshold(String json, double threshold) {
        try {
            var node = objectMapper.readTree(json);
            if (node.has("riskScore")) {
                return node.get("riskScore").asDouble() >= threshold;
            }
        } catch (Exception ignored) { }
        return false;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    A2aTask buildLoanTask(LoanApplication application, String skillId) {
        try {
            String json = objectMapper.writeValueAsString(application);
            return A2aTask.of(A2aMessage.user(json), skillId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialise LoanApplication for A2A task", e);
        }
    }

    private String firstArtifactText(A2aTask result) {
        List<A2aArtifact> artifacts = result.artifacts();
        if (artifacts == null || artifacts.isEmpty()) return null;
        return artifacts.get(0).textContent();
    }
}

