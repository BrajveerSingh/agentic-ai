package com.bank.ata.mcp;

import com.bank.ata.agent.AuditTrailAgent;
import com.bank.ata.agent.tools.LoanEvaluationTools;
import com.bank.ata.audit.dto.AuditReport;
import com.bank.ata.audit.service.AuditContextHolder;
import com.bank.ata.audit.service.AuditService;
import com.bank.ata.core.domain.*;
import com.bank.ata.mcp.annotation.McpParam;
import com.bank.ata.mcp.annotation.McpTool;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MCP tool implementations exposed to external LLM clients (Claude Desktop, IDE plugins, etc.)
 * via the custom MCP server running on {@code GET /mcp/sse} + {@code POST /mcp/message}.
 *
 * <h3>Available tools</h3>
 * <ol>
 *   <li>{@code evaluate_loan}          — full AI-powered loan evaluation with audit trail</li>
 *   <li>{@code get_audit_trail}        — retrieve the audit trail for a past evaluation</li>
 *   <li>{@code search_audit_reasoning} — semantic search over historical reasoning steps</li>
 *   <li>{@code get_credit_score}       — look up the credit score for a customer</li>
 *   <li>{@code check_compliance}       — check whether a loan scenario meets bank policies</li>
 * </ol>
 */
@Component
public class AuditMcpTools {

    private static final Logger log = LoggerFactory.getLogger(AuditMcpTools.class);

    private final AuditTrailAgent      agent;
    private final AuditService         auditService;
    private final ObjectMapper         objectMapper;
    private final LoanEvaluationTools  loanEvaluationTools;

    public AuditMcpTools(AuditTrailAgent     agent,
                         AuditService        auditService,
                         ObjectMapper        objectMapper,
                         LoanEvaluationTools loanEvaluationTools) {
        this.agent               = agent;
        this.auditService        = auditService;
        this.objectMapper        = objectMapper;
        this.loanEvaluationTools = loanEvaluationTools;
    }

    // =========================================================================
    // Tool: evaluate_loan
    // =========================================================================

    @McpTool(name = "evaluate_loan",
             description = "Evaluate a bank loan application using AI reasoning. " +
                           "Checks credit score, KYC status, employment, policy compliance and risk score. " +
                           "Returns a structured decision with reasoning and a full immutable audit trail.")
    public String evaluateLoan(
            @McpParam(description = "Bank customer identifier, e.g. CUST001") String customerId,
            @McpParam(description = "Requested loan amount in USD")            double amount,
            @McpParam(description = "Loan purpose, e.g. HOME_IMPROVEMENT, AUTO, BUSINESS, PERSONAL") String purpose,
            @McpParam(description = "Loan type: HOME_IMPROVEMENT, AUTO, BUSINESS, PERSONAL, DEBT_CONSOLIDATION") String loanType) {

        log.info("MCP tool 'evaluate_loan' called: customerId={}, amount={}", customerId, amount);

        LoanType type        = parseLoanType(loanType);
        LoanApplication app  = LoanApplication.create(customerId, BigDecimal.valueOf(amount), purpose, type);
        UUID sessionId       = UUID.randomUUID();

        LoanDecision decision = AuditContextHolder.callWithContext(
                sessionId, app.applicationId(), () -> {
                    auditService.logSessionStart(sessionId, app.applicationId(), null);
                    LoanDecision d = agent.evaluateLoan(app);
                    auditService.logSessionEnd(sessionId, app.applicationId());
                    auditService.logDecision(sessionId, app.applicationId(),
                            d.outcome().name(), d.reasoning(), d.confidenceScore());
                    return d;
                });

        Map<String, Object> result = new HashMap<>();
        result.put("applicationId",  app.applicationId().toString());
        result.put("customerId",     customerId);
        result.put("outcome",        decision.outcome().name());
        result.put("confidence",     decision.confidenceScore());
        result.put("reasoning",      decision.reasoning());
        result.put("auditSessionId", sessionId.toString());
        return toJson(result);
    }

    // =========================================================================
    // Tool: get_audit_trail
    // =========================================================================

    @McpTool(name = "get_audit_trail",
             description = "Retrieve the complete immutable audit trail for a loan application. " +
                           "Returns all reasoning steps, tool calls with inputs/outputs, execution times, " +
                           "and the final decision. Use this for compliance review and explainability.")
    public String getAuditTrail(
            @McpParam(description = "UUID of the loan application, as returned by evaluate_loan") String applicationId) {

        log.info("MCP tool 'get_audit_trail' called: applicationId={}", applicationId);
        UUID appId = UUID.fromString(applicationId);

        if (!auditService.hasAuditTrail(appId)) {
            return toJson(Map.of(
                    "error",         "No audit trail found for applicationId: " + applicationId,
                    "applicationId", applicationId));
        }

        AuditReport report = auditService.getAuditTrail(appId);
        Map<String, Object> result = new HashMap<>();
        result.put("applicationId",        applicationId);
        result.put("totalEvents",          report.getTotalEvents());
        result.put("totalReasoningSteps",  report.getTotalReasoningSteps());
        result.put("totalToolCalls",       report.getTotalToolCalls());
        result.put("totalExecutionTimeMs", report.getTotalExecutionTimeMs());
        result.put("allToolCallsSuccessful", report.allToolCallsSuccessful());
        result.put("hasDecision",          report.hasDecision());
        if (report.hasDecision()) {
            result.put("decisionOutcome",    report.decision().getOutcome());
            result.put("decisionConfidence", report.decision().getConfidenceScore());
            result.put("decisionReasoning",  report.decision().getReasoning());
        }
        return toJson(result);
    }

    // =========================================================================
    // Tool: search_audit_reasoning
    // =========================================================================

    @McpTool(name = "search_audit_reasoning",
             description = "Semantic search over historical audit reasoning steps using natural language. " +
                           "Useful for finding similar past decisions, compliance patterns, or " +
                           "reasoning chains related to a specific topic.")
    public String searchAuditReasoning(
            @McpParam(description = "Natural language search query, e.g. 'credit score evaluation'") String query,
            @McpParam(description = "Maximum results to return (1-20)")                              int maxResults) {

        log.info("MCP tool 'search_audit_reasoning' called: query='{}', maxResults={}", query, maxResults);
        int limit   = Math.min(Math.max(maxResults, 1), 20);
        var matches = auditService.searchSimilarReasoningSteps(query, limit);

        var results = matches.stream()
                .map(m -> Map.of(
                        "text",    m.embedded().text(),
                        "score",   m.score(),
                        "eventId", m.embedded().metadata().getString("eventId") != null
                                   ? m.embedded().metadata().getString("eventId") : "unknown"))
                .toList();

        return toJson(Map.of("query", query, "results", results, "count", results.size()));
    }

    // =========================================================================
    // Tool: get_credit_score
    // =========================================================================

    @McpTool(name = "get_credit_score",
             description = "Look up the credit score for a bank customer. " +
                           "Returns the numeric score (300–850), rating (EXCELLENT/GOOD/FAIR/POOR/VERY_POOR), " +
                           "and risk level (LOW/MEDIUM/HIGH).")
    public String getCreditScore(
            @McpParam(description = "Bank customer identifier, e.g. CUST001") String customerId) {

        log.info("MCP tool 'get_credit_score' called: customerId={}", customerId);

        CreditScore score = loanEvaluationTools.getCreditScore(customerId);

        return toJson(Map.of(
                "customerId", customerId,
                "score",      score.score(),
                "rating",     score.rating(),
                "riskLevel",  score.getRiskLevel(),
                "asOfDate",   score.asOfDate().toString()));
    }

    // =========================================================================
    // Tool: check_compliance
    // =========================================================================

    @McpTool(name = "check_compliance",
             description = "Check whether a loan scenario complies with bank policies. " +
                           "Validates loan amount limits, credit score minimums, and approved purposes. " +
                           "Returns compliance status, passing rules, and any violations.")
    public String checkCompliance(
            @McpParam(description = "Loan amount in USD")               double amount,
            @McpParam(description = "Loan purpose string")              String purpose,
            @McpParam(description = "Customer credit score (300–850)")  int creditScore) {

        log.info("MCP tool 'check_compliance' called: amount={}, purpose={}, creditScore={}",
                 amount, purpose, creditScore);

        PolicyResult result = loanEvaluationTools.checkPolicyCompliance(
                BigDecimal.valueOf(amount), purpose, creditScore);

        return toJson(Map.of(
                "compliant",      result.compliant(),
                "rules",          result.ruleResults(),
                "violations",     result.violations(),
                "fullyCompliant", result.isFullyCompliant()));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private LoanType parseLoanType(String loanType) {
        try {
            return LoanType.valueOf(loanType.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown loan type '{}', defaulting to PERSONAL", loanType);
            return LoanType.PERSONAL;
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"error\":\"serialization failed: " + e.getMessage() + "\"}";
        }
    }
}

