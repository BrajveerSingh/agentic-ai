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
 * Mock Risk Agent — available at {@code /mock/risk-agent/*}.
 *
 * <p>Simulates a peer risk-assessment agent that accepts A2A tasks with skill
 * {@code assess_risk} and returns a simple JSON risk report. Used for local
 * development and unit tests so that {@link com.bank.ata.a2a.orchestration.MultiAgentLoanOrchestrator}
 * can exercise multi-agent orchestration without an external service.</p>
 *
 * <pre>
 * GET  /mock/risk-agent/.well-known/agent.json
 * POST /mock/risk-agent/tasks/send
 * GET  /mock/risk-agent/health
 * </pre>
 */
@RestController
@RequestMapping("/mock/risk-agent")
public class MockRiskAgentController {

    private static final Logger log = LoggerFactory.getLogger(MockRiskAgentController.class);

    static final String SKILL_ASSESS_RISK = "assess_risk";

    private final ObjectMapper objectMapper;
    private final AgentCard    agentCard;

    public MockRiskAgentController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        AgentSkill skill = AgentSkill.of(
                SKILL_ASSESS_RISK,
                "Assess Risk",
                "Evaluates the financial risk of a loan application. " +
                "Input: JSON-serialised LoanApplication. " +
                "Output: JSON risk report {riskScore, riskLevel, flagged}.",
                "risk", "finance"
        );
        this.agentCard = AgentCard.of(
                "mock-risk-agent",
                "Mock risk-assessment peer agent (development only)",
                "http://localhost:8080/mock/risk-agent",
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
        log.debug("MockRiskAgent received task id={}", task.id());

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

            LoanApplication app        = objectMapper.readValue(text, LoanApplication.class);
            double          riskScore  = computeRiskScore(app);
            String          riskLevel  = riskLevel(riskScore);
            boolean         flagged    = riskScore >= 0.7;

            String reportJson = objectMapper.writeValueAsString(Map.of(
                    "riskScore",  riskScore,
                    "riskLevel",  riskLevel,
                    "flagged",    flagged,
                    "agentId",    "mock-risk-agent"
            ));

            log.info("MockRiskAgent: applicationId={} riskScore={} flagged={}",
                    app.applicationId(), riskScore, flagged);

            A2aArtifact artifact = A2aArtifact.of("risk_report",
                    "Risk assessment report", reportJson);
            return task.completed(List.of(artifact));

        } catch (Exception e) {
            log.error("MockRiskAgent task failed: {}", e.getMessage(), e);
            return task.failed("Risk assessment error: " + e.getMessage());
        }
    }

    /**
     * Simple deterministic risk score based on loan amount.
     * In a real agent this would call ML models, credit bureaus, etc.
     */
    private double computeRiskScore(LoanApplication app) {
        if (app.amount() == null) return 0.5;
        double amount = app.amount().doubleValue();
        // £0–50k → LOW; £50k–150k → MEDIUM; >£150k → HIGH
        if (amount < 50_000)  return 0.2;
        if (amount < 150_000) return 0.5;
        return 0.8;  // HIGH — will trigger PENDING_REVIEW in orchestrator
    }

    private String riskLevel(double score) {
        if (score < 0.3) return "LOW";
        if (score < 0.6) return "MEDIUM";
        return "HIGH";
    }
}

