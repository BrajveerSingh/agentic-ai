package com.bank.ata.a2a.server;

import com.bank.ata.a2a.model.*;
import com.bank.ata.agent.AuditTrailAgent;
import com.bank.ata.core.domain.*;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link A2aTaskHandler}.
 */
@ExtendWith(MockitoExtension.class)
class A2aTaskHandlerTest {

    @Mock
    private AuditTrailAgent agent;

    private A2aTaskHandler handler;

    @BeforeEach
    void setUp() {
        handler = new A2aTaskHandler(agent, new ObjectMapper());
    }

    // =========================================================================
    // handle — evaluate_loan skill
    // =========================================================================

    @Test
    @DisplayName("handle: evaluate_loan — should return COMPLETED task with loan decision artifact")
    void handle_evaluateLoan_shouldReturnCompletedTask() throws Exception {
        LoanApplication app = LoanApplication.create(
                "CUST001", new BigDecimal("50000"), "HOME_IMPROVEMENT", LoanType.PERSONAL);
        LoanDecision decision = LoanDecision.create(
                app.applicationId(), DecisionOutcome.APPROVED, "Good credit", 0.9);

        when(agent.evaluateLoan(any(LoanApplication.class))).thenReturn(decision);

        String appJson = new ObjectMapper().writeValueAsString(app);
        A2aTask task = A2aTask.of(A2aMessage.user(appJson), A2aTaskHandler.SKILL_EVALUATE_LOAN);

        A2aTask result = handler.handle(task);

        assertThat(result.status().state()).isEqualTo(TaskState.COMPLETED);
        assertThat(result.artifacts()).hasSize(1);
        assertThat(result.artifacts().get(0).name()).isEqualTo("loan_decision");
        assertThat(result.artifacts().get(0).textContent()).contains("APPROVED");
    }

    @Test
    @DisplayName("handle: evaluate_loan — defaults to evaluate_loan when skillId is null")
    void handle_nullSkillId_defaultsToEvaluateLoan() throws Exception {
        LoanApplication app = LoanApplication.create(
                "CUST001", new BigDecimal("30000"), "DEBT_CONSOLIDATION", LoanType.PERSONAL);
        LoanDecision decision = LoanDecision.create(
                app.applicationId(), DecisionOutcome.PENDING_REVIEW, "Borderline", 0.6);

        when(agent.evaluateLoan(any(LoanApplication.class))).thenReturn(decision);

        String appJson = new ObjectMapper().writeValueAsString(app);
        // No skillId
        A2aTask task = new A2aTask(null, null, A2aMessage.user(appJson),
                TaskStatus.submitted(), List.of(), null);

        A2aTask result = handler.handle(task);

        assertThat(result.status().state()).isEqualTo(TaskState.COMPLETED);
    }

    @Test
    @DisplayName("handle: empty message text — should return FAILED task")
    void handle_emptyMessage_shouldReturnFailed() {
        A2aTask task = A2aTask.of(A2aMessage.user(""), A2aTaskHandler.SKILL_EVALUATE_LOAN);

        A2aTask result = handler.handle(task);

        assertThat(result.status().state()).isEqualTo(TaskState.FAILED);
        assertThat(result.status().message()).contains("empty");
    }

    @Test
    @DisplayName("handle: invalid JSON in message — should return FAILED task")
    void handle_invalidJson_shouldReturnFailed() {
        A2aTask task = A2aTask.of(A2aMessage.user("NOT_VALID_JSON"), A2aTaskHandler.SKILL_EVALUATE_LOAN);

        A2aTask result = handler.handle(task);

        assertThat(result.status().state()).isEqualTo(TaskState.FAILED);
        assertThat(result.status().message()).containsIgnoringCase("error");
    }

    @Test
    @DisplayName("handle: unknown skill — should return FAILED task")
    void handle_unknownSkill_shouldReturnFailed() {
        A2aTask task = A2aTask.of(A2aMessage.user("{\"text\":\"hello\"}"), "nonexistent_skill");

        A2aTask result = handler.handle(task);

        assertThat(result.status().state()).isEqualTo(TaskState.FAILED);
        assertThat(result.status().message()).contains("Unknown skill");
    }

    // =========================================================================
    // buildAgentCard
    // =========================================================================

    @Test
    @DisplayName("buildAgentCard: should return card with expected fields and skills")
    void buildAgentCard_shouldReturnWellFormedCard() {
        AgentCard card = handler.buildAgentCard("http://localhost:8080/a2a");

        assertThat(card.name()).isEqualTo("audit-trail-agent");
        assertThat(card.url()).isEqualTo("http://localhost:8080/a2a");
        assertThat(card.version()).isEqualTo("1.0.0");
        assertThat(card.skills()).hasSize(1);
        assertThat(card.skills().get(0).id()).isEqualTo(A2aTaskHandler.SKILL_EVALUATE_LOAN);
    }

    @Test
    @DisplayName("buildAgentCard: capabilities should have streaming=false")
    void buildAgentCard_capabilitiesShouldBeDefaults() {
        AgentCard card = handler.buildAgentCard("http://localhost:8080/a2a");

        assertThat(card.capabilities().streaming()).isFalse();
        assertThat(card.capabilities().pushNotifications()).isFalse();
    }
}

