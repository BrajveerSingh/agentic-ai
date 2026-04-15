package com.bank.ata.integration;

import com.bank.ata.agent.AuditTrailAgent;
import com.bank.ata.core.domain.DecisionOutcome;
import com.bank.ata.core.domain.LoanApplication;
import com.bank.ata.core.domain.LoanDecision;
import com.bank.ata.core.domain.LoanType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for the AI Agent with Ollama.
 * <p>
 * Tests skip gracefully (with a visible reason) when Ollama is not running,
 * replacing the previous hard {@code @Disabled} that silently hid the gap.
 * <p>
 * How to enable:
 * docker-compose up -d ollama
 * docker-compose exec ollama ollama pull llama3:8b  (first time only)
 */
@SpringBootTest
@ActiveProfiles("dev")
class AgentIntegrationTest {

    private static final String OLLAMA_BASE_URL = "http://localhost:11434";
    private static boolean ollamaReachable = false;
    @Autowired
    private AuditTrailAgent agent;

    @BeforeAll
    static void probeOllama() {
        try {
            HttpURLConnection conn = (HttpURLConnection)
                    new URL(OLLAMA_BASE_URL + "/api/tags").openConnection();
            conn.setConnectTimeout(2_000);
            conn.setReadTimeout(2_000);
            if (conn.getResponseCode() == 200) {
                ollamaReachable = true;
            }
            conn.disconnect();
        } catch (Exception ignored) {
            // Ollama not reachable — tests will be skipped via assumeTrue
        }
    }

    @Test
    @DisplayName("Agent should approve loan with good credit")
    void shouldApproveLoanWithGoodCredit() {
        assumeTrue(ollamaReachable,
                "Ollama not running at " + OLLAMA_BASE_URL + " — start with: docker-compose up -d ollama");

        LoanApplication app = LoanApplication.create(
                "GOOD_CUST_001",
                new BigDecimal("50000"),
                "HOME_IMPROVEMENT",
                LoanType.HOME_IMPROVEMENT
        );

        LoanDecision decision = agent.evaluateLoan(app);

        assertThat(decision).isNotNull();
        assertThat(decision.applicationId()).isEqualTo(app.applicationId());
        assertThat(decision.reasoning()).isNotBlank();
        assertThat(decision.confidenceScore()).isBetween(0.0, 1.0);
        assertThat(decision.outcome()).isIn(DecisionOutcome.APPROVED, DecisionOutcome.PENDING_REVIEW);
    }

    @Test
    @DisplayName("Agent should reject loan for unverified customer")
    void shouldHandleUnverifiedCustomer() {
        assumeTrue(ollamaReachable,
                "Ollama not running — start with: docker-compose up -d ollama");

        LoanApplication app = LoanApplication.create(
                "UNVERIFIED_CUST_001",
                new BigDecimal("50000"),
                "PERSONAL",
                LoanType.PERSONAL
        );

        LoanDecision decision = agent.evaluateLoan(app);

        assertThat(decision).isNotNull();
        assertThat(decision.reasoning()).isNotBlank();
        assertThat(decision.outcome()).isIn(
                DecisionOutcome.REQUIRES_ADDITIONAL_INFO,
                DecisionOutcome.REJECTED,
                DecisionOutcome.PENDING_REVIEW
        );
    }

    @Test
    @DisplayName("Agent should handle large loan amounts")
    void shouldHandleLargeLoanAmount() {
        assumeTrue(ollamaReachable,
                "Ollama not running — start with: docker-compose up -d ollama");

        LoanApplication app = LoanApplication.create(
                "CUST_001",
                new BigDecimal("600000"),  // Exceeds $500k limit
                "BUSINESS",
                LoanType.BUSINESS
        );

        LoanDecision decision = agent.evaluateLoan(app);

        assertThat(decision).isNotNull();
        assertThat(decision.reasoning()).containsIgnoringCase("amount");
        assertThat(decision.outcome()).isIn(DecisionOutcome.REJECTED, DecisionOutcome.PENDING_REVIEW);
    }

    @Test
    @DisplayName("Agent should provide detailed reasoning")
    void shouldProvideDetailedReasoning() {
        assumeTrue(ollamaReachable,
                "Ollama not running — start with: docker-compose up -d ollama");

        LoanApplication app = LoanApplication.create(
                "CUST_002",
                new BigDecimal("75000"),
                "DEBT_CONSOLIDATION",
                LoanType.DEBT_CONSOLIDATION
        );

        LoanDecision decision = agent.evaluateLoan(app);

        assertThat(decision.reasoning())
                .isNotBlank()
                .hasSizeGreaterThan(50);
    }

    @Test
    @DisplayName("Agent should complete evaluation within timeout")
    void shouldCompleteWithinTimeout() {
        assumeTrue(ollamaReachable,
                "Ollama not running — start with: docker-compose up -d ollama");

        LoanApplication app = LoanApplication.create(
                "CUST_003",
                new BigDecimal("25000"),
                "AUTO",
                LoanType.AUTO
        );

        long startTime = System.currentTimeMillis();
        LoanDecision decision = agent.evaluateLoan(app);
        long duration = System.currentTimeMillis() - startTime;

        assertThat(decision).isNotNull();
        assertThat(duration).isLessThan(60_000L);
    }
}