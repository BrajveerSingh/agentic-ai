package com.bank.ata.mcp;

import com.bank.ata.agent.AuditTrailAgent;
import com.bank.ata.agent.tools.LoanEvaluationTools;
import com.bank.ata.audit.dto.AuditReport;
import com.bank.ata.audit.entity.LoanDecisionEntity;
import com.bank.ata.audit.service.AuditService;
import com.bank.ata.core.domain.*;
import com.bank.ata.mcp.annotation.McpTool;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuditMcpTools} — verifies tool response shapes,
 * error handling, and JSON serialisation without starting a Spring context.
 */
@ExtendWith(MockitoExtension.class)
class McpToolsTest {

    @Mock  private AuditTrailAgent     agent;
    @Mock  private AuditService        auditService;
    @Mock  private LoanEvaluationTools loanEvaluationTools;

    private AuditMcpTools tools;
    private ObjectMapper  objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        tools = new AuditMcpTools(agent, auditService, objectMapper, loanEvaluationTools);
    }

    // =========================================================================
    // evaluate_loan
    // =========================================================================

    @Test
    @DisplayName("evaluate_loan: should return valid JSON with applicationId and outcome")
    void evaluateLoan_shouldReturnJsonWithOutcomeAndApplicationId() throws Exception {
        UUID appId   = UUID.randomUUID();
        LoanDecision decision = LoanDecision.create(appId, DecisionOutcome.APPROVED,
                "Good credit and KYC verified", 0.92);

        when(agent.evaluateLoan(any())).thenReturn(decision);
        when(auditService.logSessionStart(any(), any(), any())).thenReturn(null);

        String json = tools.evaluateLoan("CUST001", 50000.0, "HOME_IMPROVEMENT", "HOME_IMPROVEMENT");

        JsonNode node = objectMapper.readTree(json);
        assertThat(node.has("applicationId")).isTrue();
        assertThat(node.get("outcome").asText()).isEqualTo("APPROVED");
        assertThat(node.get("confidence").asDouble()).isEqualTo(0.92);
        assertThat(node.get("customerId").asText()).isEqualTo("CUST001");
        assertThat(node.get("auditSessionId").asText()).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    @DisplayName("evaluate_loan: unknown loan type should default to PERSONAL without throwing")
    void evaluateLoan_unknownLoanType_shouldDefaultToPersonal() throws Exception {
        UUID appId   = UUID.randomUUID();
        LoanDecision decision = LoanDecision.create(appId, DecisionOutcome.APPROVED, "OK", 0.8);
        when(agent.evaluateLoan(any())).thenReturn(decision);
        when(auditService.logSessionStart(any(), any(), any())).thenReturn(null);

        String json = tools.evaluateLoan("CUST002", 20000.0, "OTHER", "UNKNOWN_TYPE");

        JsonNode node = objectMapper.readTree(json);
        assertThat(node.has("applicationId")).isTrue();
    }

    // =========================================================================
    // get_audit_trail
    // =========================================================================

    @Test
    @DisplayName("get_audit_trail: should return not-found JSON for unknown applicationId")
    void getAuditTrail_notFound_shouldReturnErrorJson() throws Exception {
        UUID appId = UUID.randomUUID();
        when(auditService.hasAuditTrail(appId)).thenReturn(false);

        String json = tools.getAuditTrail(appId.toString());

        JsonNode node = objectMapper.readTree(json);
        assertThat(node.has("error")).isTrue();
        assertThat(node.get("error").asText()).contains("No audit trail found");
    }

    @Test
    @DisplayName("get_audit_trail: should return summary JSON for known applicationId")
    void getAuditTrail_found_shouldReturnSummary() throws Exception {
        UUID appId     = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        LoanDecisionEntity decisionEntity = new LoanDecisionEntity(
                appId, sessionId, "APPROVED", "Meets all criteria", 0.95);
        AuditReport report = new AuditReport(appId, List.of(), List.of(), List.of(), decisionEntity);

        when(auditService.hasAuditTrail(appId)).thenReturn(true);
        when(auditService.getAuditTrail(appId)).thenReturn(report);

        String json = tools.getAuditTrail(appId.toString());

        JsonNode node = objectMapper.readTree(json);
        assertThat(node.get("applicationId").asText()).isEqualTo(appId.toString());
        assertThat(node.get("hasDecision").asBoolean()).isTrue();
        assertThat(node.get("decisionOutcome").asText()).isEqualTo("APPROVED");
        assertThat(node.get("totalEvents").asInt()).isEqualTo(0);
    }

    @Test
    @DisplayName("get_audit_trail: invalid UUID should surface an IllegalArgumentException")
    void getAuditTrail_invalidUuid_shouldThrow() {
        assertThatThrownBy(() -> tools.getAuditTrail("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // =========================================================================
    // search_audit_reasoning
    // =========================================================================

    @Test
    @DisplayName("search_audit_reasoning: should return JSON with results and count")
    void searchAuditReasoning_shouldReturnJson() throws Exception {
        when(auditService.searchSimilarReasoningSteps(eq("credit score"), anyInt()))
                .thenReturn(List.of());

        String json = tools.searchAuditReasoning("credit score", 5);

        JsonNode node = objectMapper.readTree(json);
        assertThat(node.get("query").asText()).isEqualTo("credit score");
        assertThat(node.get("count").asInt()).isEqualTo(0);
        assertThat(node.get("results").isArray()).isTrue();
    }

    @Test
    @DisplayName("search_audit_reasoning: maxResults should be clamped to [1, 20]")
    void searchAuditReasoning_shouldClampMaxResults() throws Exception {
        when(auditService.searchSimilarReasoningSteps(any(), eq(20))).thenReturn(List.of());

        String json = tools.searchAuditReasoning("anything", 999);  // 999 → clamped to 20
        JsonNode node = objectMapper.readTree(json);
        assertThat(node.get("count").asInt()).isEqualTo(0);
    }

    // =========================================================================
    // get_credit_score
    // =========================================================================

    @Test
    @DisplayName("get_credit_score: should return JSON with score, rating and riskLevel")
    void getCreditScore_shouldReturnJson() throws Exception {
        CreditScore creditScore = new CreditScore("CUST001", 720, "GOOD", LocalDate.of(2026, 1, 1));
        when(loanEvaluationTools.getCreditScore("CUST001")).thenReturn(creditScore);

        String json = tools.getCreditScore("CUST001");

        JsonNode node = objectMapper.readTree(json);
        assertThat(node.get("customerId").asText()).isEqualTo("CUST001");
        assertThat(node.get("score").asInt()).isEqualTo(720);
        assertThat(node.get("rating").asText()).isEqualTo("GOOD");
        assertThat(node.has("riskLevel")).isTrue();
        assertThat(node.has("asOfDate")).isTrue();
    }

    // =========================================================================
    // check_compliance
    // =========================================================================

    @Test
    @DisplayName("check_compliance: compliant scenario should return compliant=true")
    void checkCompliance_compliantScenario_shouldReturnCompliantTrue() throws Exception {
        PolicyResult policyResult = new PolicyResult(true,
                List.of("PASS: Credit score meets minimum", "PASS: Amount within limit"),
                List.of());
        when(loanEvaluationTools.checkPolicyCompliance(
                eq(new BigDecimal("50000.0")), eq("HOME_IMPROVEMENT"), eq(720)))
                .thenReturn(policyResult);

        String json = tools.checkCompliance(50000.0, "HOME_IMPROVEMENT", 720);

        JsonNode node = objectMapper.readTree(json);
        assertThat(node.get("compliant").asBoolean()).isTrue();
        assertThat(node.get("fullyCompliant").asBoolean()).isTrue();
        assertThat(node.get("rules").isArray()).isTrue();
        assertThat(node.get("violations").isArray()).isTrue();
        assertThat(node.get("violations").size()).isEqualTo(0);
    }

    @Test
    @DisplayName("check_compliance: non-compliant scenario should list violations")
    void checkCompliance_nonCompliantScenario_shouldReturnViolations() throws Exception {
        PolicyResult policyResult = new PolicyResult(false,
                List.of("PASS: Amount within limit"),
                List.of("FAIL: Credit score 580 below minimum 650"));
        when(loanEvaluationTools.checkPolicyCompliance(any(), any(), anyInt()))
                .thenReturn(policyResult);

        String json = tools.checkCompliance(50000.0, "HOME_IMPROVEMENT", 580);

        JsonNode node = objectMapper.readTree(json);
        assertThat(node.get("compliant").asBoolean()).isFalse();
        assertThat(node.get("violations").size()).isEqualTo(1);
        assertThat(node.get("violations").get(0).asText()).contains("580");
    }

    // =========================================================================
    // Tool discoverability
    // =========================================================================

    @Test
    @DisplayName("AuditMcpTools should expose exactly five @McpTool-annotated methods")
    void shouldExposeThreeMcpToolMethods() {
        long toolCount = java.util.Arrays.stream(AuditMcpTools.class.getMethods())
                .filter(m -> m.isAnnotationPresent(McpTool.class))
                .count();

        assertThat(toolCount)
                .as("Expected 5 @McpTool methods: evaluate_loan, get_audit_trail, "
                  + "search_audit_reasoning, get_credit_score, check_compliance")
                .isEqualTo(5);
    }
}

