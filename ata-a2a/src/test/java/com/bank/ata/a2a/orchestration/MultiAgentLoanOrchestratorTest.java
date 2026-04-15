package com.bank.ata.a2a.orchestration;

import com.bank.ata.a2a.client.A2aClient;
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
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MultiAgentLoanOrchestrator}.
 */
@ExtendWith(MockitoExtension.class)
class MultiAgentLoanOrchestratorTest {

    @Mock private AuditTrailAgent agent;
    @Mock private A2aClient       riskClient;
    @Mock private A2aClient       fraudClient;

    private MultiAgentLoanOrchestrator orchestrator;
    private ObjectMapper objectMapper;

    private LoanApplication sampleApp;
    private LoanDecision    baseApproved;
    private LoanDecision    baseRejected;

    @BeforeEach
    void setUp() {
        objectMapper  = new ObjectMapper();
        orchestrator  = new MultiAgentLoanOrchestrator(agent, riskClient, fraudClient, objectMapper);

        sampleApp    = LoanApplication.create(
                "CUST001", new BigDecimal("50000"), "HOME_IMPROVEMENT", LoanType.PERSONAL);
        baseApproved = LoanDecision.create(
                sampleApp.applicationId(), DecisionOutcome.APPROVED, "Good credit profile", 0.88);
        baseRejected = LoanDecision.create(
                sampleApp.applicationId(), DecisionOutcome.REJECTED, "Poor credit", 0.95);
    }

    // =========================================================================
    // No peer concerns — base decision preserved
    // =========================================================================

    @Test
    @DisplayName("evaluateWithPeerAgents: no peer flags — should return base decision unchanged")
    void evaluate_noPeerConcerns_shouldReturnBaseDecision() {
        when(agent.evaluateLoan(any())).thenReturn(baseApproved);
        when(riskClient.isAvailable()).thenReturn(true);
        when(fraudClient.isAvailable()).thenReturn(true);

        // Risk: LOW, no flag
        A2aTask riskResult = A2aTask.of(A2aMessage.user("{}"), "assess_risk")
                .completed(List.of(A2aArtifact.of("risk_report", "Low risk",
                        "{\"riskScore\":0.2,\"riskLevel\":\"LOW\",\"flagged\":false}")));
        // Fraud: not detected
        A2aTask fraudResult = A2aTask.of(A2aMessage.user("{}"), "check_fraud")
                .completed(List.of(A2aArtifact.of("fraud_report", "No fraud",
                        "{\"fraudDetected\":false,\"fraudScore\":0.1}")));

        when(riskClient.sendTask(any())).thenReturn(riskResult);
        when(fraudClient.sendTask(any())).thenReturn(fraudResult);

        LoanDecision decision = orchestrator.evaluateWithPeerAgents(sampleApp);

        assertThat(decision.outcome()).isEqualTo(DecisionOutcome.APPROVED);
        assertThat(decision.confidenceScore()).isEqualTo(0.88);
    }

    // =========================================================================
    // Risk agent flags HIGH risk
    // =========================================================================

    @Test
    @DisplayName("evaluateWithPeerAgents: risk HIGH — should override outcome to PENDING_REVIEW")
    void evaluate_riskHigh_shouldOverrideToPendingReview() {
        when(agent.evaluateLoan(any())).thenReturn(baseApproved);
        when(riskClient.isAvailable()).thenReturn(true);
        when(fraudClient.isAvailable()).thenReturn(true);

        A2aTask riskResult = A2aTask.of(A2aMessage.user("{}"), "assess_risk")
                .completed(List.of(A2aArtifact.of("risk_report", "High risk",
                        "{\"riskScore\":0.82,\"riskLevel\":\"HIGH\",\"flagged\":true}")));
        A2aTask fraudResult = A2aTask.of(A2aMessage.user("{}"), "check_fraud")
                .completed(List.of(A2aArtifact.of("fraud_report", "No fraud",
                        "{\"fraudDetected\":false}")));

        when(riskClient.sendTask(any())).thenReturn(riskResult);
        when(fraudClient.sendTask(any())).thenReturn(fraudResult);

        LoanDecision decision = orchestrator.evaluateWithPeerAgents(sampleApp);

        assertThat(decision.outcome()).isEqualTo(DecisionOutcome.PENDING_REVIEW);
        assertThat(decision.reasoning()).contains("[Risk Agent]");
    }

    // =========================================================================
    // Fraud agent flags fraud
    // =========================================================================

    @Test
    @DisplayName("evaluateWithPeerAgents: fraud detected — should override outcome to PENDING_REVIEW")
    void evaluate_fraudDetected_shouldOverrideToPendingReview() {
        when(agent.evaluateLoan(any())).thenReturn(baseApproved);
        when(riskClient.isAvailable()).thenReturn(true);
        when(fraudClient.isAvailable()).thenReturn(true);

        A2aTask riskResult = A2aTask.of(A2aMessage.user("{}"), "assess_risk")
                .completed(List.of(A2aArtifact.of("risk_report", "Low risk",
                        "{\"riskScore\":0.2,\"riskLevel\":\"LOW\"}")));
        A2aTask fraudResult = A2aTask.of(A2aMessage.user("{}"), "check_fraud")
                .completed(List.of(A2aArtifact.of("fraud_report", "Fraud Detected",
                        "{\"fraudDetected\":true,\"fraudScore\":0.9}")));

        when(riskClient.sendTask(any())).thenReturn(riskResult);
        when(fraudClient.sendTask(any())).thenReturn(fraudResult);

        LoanDecision decision = orchestrator.evaluateWithPeerAgents(sampleApp);

        assertThat(decision.outcome()).isEqualTo(DecisionOutcome.PENDING_REVIEW);
        assertThat(decision.reasoning()).contains("[Fraud Agent]");
    }

    // =========================================================================
    // Already REJECTED — stays REJECTED even when peers flag
    // =========================================================================

    @Test
    @DisplayName("evaluateWithPeerAgents: base REJECTED + peer flags — should stay REJECTED")
    void evaluate_baseRejected_shouldStayRejected() {
        when(agent.evaluateLoan(any())).thenReturn(baseRejected);
        when(riskClient.isAvailable()).thenReturn(true);
        when(fraudClient.isAvailable()).thenReturn(true);

        A2aTask riskResult = A2aTask.of(A2aMessage.user("{}"), "assess_risk")
                .completed(List.of(A2aArtifact.of("risk_report", "High risk",
                        "{\"riskScore\":0.9,\"riskLevel\":\"HIGH\"}")));
        A2aTask fraudResult = A2aTask.of(A2aMessage.user("{}"), "check_fraud")
                .completed(List.of(A2aArtifact.of("fraud_report", "Fraud Detected",
                        "{\"fraudDetected\":true}")));

        when(riskClient.sendTask(any())).thenReturn(riskResult);
        when(fraudClient.sendTask(any())).thenReturn(fraudResult);

        LoanDecision decision = orchestrator.evaluateWithPeerAgents(sampleApp);

        assertThat(decision.outcome()).isEqualTo(DecisionOutcome.REJECTED);
    }

    // =========================================================================
    // Graceful degradation — agents unavailable
    // =========================================================================

    @Test
    @DisplayName("evaluateWithPeerAgents: both peers unavailable — should return base decision")
    void evaluate_peersUnavailable_shouldReturnBaseDecision() {
        when(agent.evaluateLoan(any())).thenReturn(baseApproved);
        when(riskClient.isAvailable()).thenReturn(false);
        when(fraudClient.isAvailable()).thenReturn(false);

        LoanDecision decision = orchestrator.evaluateWithPeerAgents(sampleApp);

        assertThat(decision.outcome()).isEqualTo(DecisionOutcome.APPROVED);
        verify(riskClient, never()).sendTask(any());
        verify(fraudClient, never()).sendTask(any());
    }

    @Test
    @DisplayName("evaluateWithPeerAgents: risk throws exception — should still return valid decision")
    void evaluate_riskThrows_shouldDegradeGracefully() {
        when(agent.evaluateLoan(any())).thenReturn(baseApproved);
        when(riskClient.isAvailable()).thenReturn(true);
        when(fraudClient.isAvailable()).thenReturn(true);

        when(riskClient.sendTask(any())).thenThrow(new RuntimeException("Connection timeout"));
        A2aTask fraudResult = A2aTask.of(A2aMessage.user("{}"), "check_fraud")
                .completed(List.of(A2aArtifact.of("fraud_report", "No fraud",
                        "{\"fraudDetected\":false}")));
        when(fraudClient.sendTask(any())).thenReturn(fraudResult);

        LoanDecision decision = orchestrator.evaluateWithPeerAgents(sampleApp);

        // Should fall back to base — only fraud checked, no flags raised
        assertThat(decision.outcome()).isEqualTo(DecisionOutcome.APPROVED);
    }

    // =========================================================================
    // buildLoanTask helper
    // =========================================================================

    @Test
    @DisplayName("buildLoanTask: should create task with correct skill and serialised application")
    void buildLoanTask_shouldCreateCorrectTask() {
        A2aTask task = orchestrator.buildLoanTask(sampleApp, "assess_risk");

        assertThat(task.skillId()).isEqualTo("assess_risk");
        assertThat(task.message().role()).isEqualTo("user");
        assertThat(task.message().textContent()).contains("CUST001");
        assertThat(task.status().state()).isEqualTo(TaskState.SUBMITTED);
    }
}


