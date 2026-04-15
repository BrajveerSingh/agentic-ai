package com.bank.ata.a2a.mock;

import com.bank.ata.a2a.model.*;
import com.bank.ata.core.domain.LoanApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Mock Fraud Agent — available at {@code /mock/fraud-agent/*}.
 *
 * <p>Simulates a peer fraud-detection agent that accepts A2A tasks with skill
 * {@code check_fraud} and returns a simple JSON fraud report. Used for local
 * development and unit tests so that {@link com.bank.ata.a2a.orchestration.MultiAgentLoanOrchestrator}
 * can exercise multi-agent orchestration without an external service.</p>
 *
 * <pre>
 * GET  /mock/fraud-agent/.well-known/agent.json
 * POST /mock/fraud-agent/tasks/send
 * GET  /mock/fraud-agent/health
 * </pre>
 */
@RestController
@RequestMapping("/mock/fraud-agent")
public class MockFraudAgentController {

    private static final Logger log = LoggerFactory.getLogger(MockFraudAgentController.class);

    static final String SKILL_CHECK_FRAUD = "check_fraud";

    private final ObjectMapper objectMapper;
    private final AgentCard    agentCard;

    public MockFraudAgentController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        AgentSkill skill = AgentSkill.of(
                SKILL_CHECK_FRAUD,
                "Check Fraud",
                "Performs fraud detection on a loan application. " +
                "Input: JSON-serialised LoanApplication. " +
                "Output: JSON fraud report {fraudDetected, fraudScore, reasons}.",
                "fraud", "security", "finance"
        );
        this.agentCard = AgentCard.of(
                "mock-fraud-agent",
                "Mock fraud-detection peer agent (development only)",
                "http://localhost:8080/mock/fraud-agent",
                "1.0.0",
                List.of(skill)
        );
    }

    // -------------------------------------------------------------------------
    // Well-known
    // -------------------------------------------------------------------------

    @GetMapping("/.well-known/agent.json")
    public AgentCard agentCard() {
        return agentCard;
    }

    // -------------------------------------------------------------------------
    // Tasks
    // -------------------------------------------------------------------------

    @PostMapping("/tasks/send")
    public ResponseEntity<A2aTask> sendTask(@RequestBody A2aTask task) {
        if (task.message() == null) {
            return ResponseEntity.badRequest().build();
        }
        log.debug("MockFraudAgent received task id={}", task.id());

        A2aTask toProcess = task.id() != null ? task : A2aTask.of(task.message(), task.skillId());
        A2aTask result    = handleTask(toProcess);
        return ResponseEntity.ok(result);
    }

    // -------------------------------------------------------------------------
    // Health
    // -------------------------------------------------------------------------

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status",  "UP",
                "agent",   agentCard.name(),
                "version", agentCard.version()
        );
    }

    // -------------------------------------------------------------------------
    // Internal logic
    // -------------------------------------------------------------------------

    private A2aTask handleTask(A2aTask task) {
        try {
            String text = task.message().textContent();
            if (text == null || text.isBlank()) {
                return task.failed("No message text — expected JSON LoanApplication");
            }

            LoanApplication app          = objectMapper.readValue(text, LoanApplication.class);
            boolean         fraudDetected = isFraudSuspected(app);
            double          fraudScore    = fraudDetected ? 0.85 : 0.10;
            List<String>    reasons       = fraudDetected
                    ? List.of("Velocity check: multiple applications in short window",
                              "Amount pattern: unusual for customer profile")
                    : List.of();

            String reportJson = objectMapper.writeValueAsString(Map.of(
                    "fraudDetected", fraudDetected,
                    "fraudScore",    fraudScore,
                    "reasons",       reasons,
                    "agentId",       "mock-fraud-agent"
            ));

            log.info("MockFraudAgent: applicationId={} fraudDetected={}",
                    app.applicationId(), fraudDetected);

            A2aArtifact artifact = A2aArtifact.of("fraud_report",
                    "Fraud detection report", reportJson);
            return task.completed(List.of(artifact));

        } catch (Exception e) {
            log.error("MockFraudAgent task failed: {}", e.getMessage(), e);
            return task.failed("Fraud check error: " + e.getMessage());
        }
    }

    /**
     * Simple heuristic: flag customers whose ID contains "FRAUD" or whose
     * purpose contains "GAMBLING". In a real agent this would query a fraud DB.
     */
    private boolean isFraudSuspected(LoanApplication app) {
        if (app.customerId() != null && app.customerId().toUpperCase().contains("FRAUD")) {
            return true;
        }
        if (app.purpose() != null && app.purpose().toUpperCase().contains("GAMBLING")) {
            return true;
        }
        return false;
    }
}

