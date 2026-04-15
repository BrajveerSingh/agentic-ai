package com.bank.ata.a2a.server;

import com.bank.ata.a2a.model.*;
import com.bank.ata.agent.AuditTrailAgent;
import com.bank.ata.core.domain.LoanApplication;
import com.bank.ata.core.domain.LoanDecision;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Processes inbound A2A tasks routed by {@link A2aController}.
 *
 * <p>Supported skill: {@code evaluate_loan} — expects the task message text to be
 * a JSON-serialised {@link LoanApplication}; returns a JSON-serialised
 * {@link LoanDecision} as an artifact.</p>
 */
@Service
public class A2aTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(A2aTaskHandler.class);

    static final String SKILL_EVALUATE_LOAN = "evaluate_loan";

    private final AuditTrailAgent agent;
    private final ObjectMapper    objectMapper;

    public A2aTaskHandler(AuditTrailAgent agent, ObjectMapper objectMapper) {
        this.agent        = agent;
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Handle an inbound A2A task synchronously.
     *
     * @param task incoming task (status may be SUBMITTED)
     * @return the same task in COMPLETED or FAILED state with artifacts populated
     */
    public A2aTask handle(A2aTask task) {
        log.info("Handling A2A task id={} skill={}", task.id(), task.skillId());

        String skillId = task.skillId() != null ? task.skillId() : SKILL_EVALUATE_LOAN;

        return switch (skillId) {
            case SKILL_EVALUATE_LOAN -> handleEvaluateLoan(task);
            default -> {
                log.warn("Unknown skill '{}' in task {}", skillId, task.id());
                yield task.failed("Unknown skill: " + skillId);
            }
        };
    }

    /**
     * Build the AgentCard that ATA exposes at its well-known endpoint.
     */
    public AgentCard buildAgentCard(String baseUrl) {
        AgentSkill evaluateLoan = AgentSkill.of(
                SKILL_EVALUATE_LOAN,
                "Evaluate Loan Application",
                "Full AI-powered loan evaluation with immutable audit trail. " +
                "Input: JSON-serialised LoanApplication. " +
                "Output: JSON-serialised LoanDecision.",
                "loan", "audit", "finance"
        );
        return AgentCard.of(
                "audit-trail-agent",
                "Bank-grade AI agent for loan evaluation with immutable audit trail",
                baseUrl,
                "1.0.0",
                List.of(evaluateLoan)
        );
    }

    // -------------------------------------------------------------------------
    // Skill handlers
    // -------------------------------------------------------------------------

    private A2aTask handleEvaluateLoan(A2aTask task) {
        try {
            String messageText = task.message().textContent();
            if (messageText == null || messageText.isBlank()) {
                return task.failed("Message text is empty — expected JSON LoanApplication");
            }

            LoanApplication application = objectMapper.readValue(messageText, LoanApplication.class);
            LoanDecision    decision    = agent.evaluateLoan(application);
            String          resultJson  = objectMapper.writeValueAsString(decision);

            A2aArtifact artifact = A2aArtifact.of(
                    "loan_decision",
                    "Loan evaluation decision with reasoning and audit trail",
                    resultJson
            );

            log.info("A2A task {} completed: applicationId={} outcome={}",
                    task.id(), application.applicationId(), decision.outcome());

            return task.completed(List.of(artifact));

        } catch (Exception e) {
            log.error("A2A task {} failed: {}", task.id(), e.getMessage(), e);
            return task.failed("Evaluation error: " + e.getMessage());
        }
    }
}

