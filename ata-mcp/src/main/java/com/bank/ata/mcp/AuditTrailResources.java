package com.bank.ata.mcp;

import com.bank.ata.audit.dto.AuditReport;
import com.bank.ata.audit.service.AuditService;
import com.bank.ata.mcp.annotation.McpResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * MCP resource provider that exposes the immutable audit trail as a URI-template resource.
 *
 * <p>Exposes:</p>
 * <ul>
 *   <li>{@code audit://trail/{applicationId}} — full audit report for a loan application</li>
 * </ul>
 *
 * <p>Clients can read the audit trail by sending a {@code resources/read} request with the
 * URI {@code audit://trail/<uuid>}.  This is the resource-based counterpart of the
 * {@code get_audit_trail} tool — use whichever interface suits the client.</p>
 */
@Component
public class AuditTrailResources {

    private static final Logger log = LoggerFactory.getLogger(AuditTrailResources.class);

    private final AuditService auditService;

    public AuditTrailResources(AuditService auditService) {
        this.auditService = auditService;
    }

    @McpResource(
            uri         = "audit://trail/{applicationId}",
            name        = "Loan Application Audit Trail",
            description = "Complete immutable audit trail for a loan application. "
                        + "Replace {applicationId} with the UUID returned by evaluate_loan.",
            mimeType    = "application/json")
    public String getAuditTrail(String uri) {
        try {
            String applicationId = uri.contains("/")
                    ? uri.substring(uri.lastIndexOf('/') + 1)
                    : uri;

            UUID appId = UUID.fromString(applicationId);

            if (!auditService.hasAuditTrail(appId)) {
                return "{\"error\":\"No audit trail found\","
                        + "\"applicationId\":\"" + applicationId + "\"}";
            }

            AuditReport report = auditService.getAuditTrail(appId);

            // Build a compact JSON summary (avoids pulling in ObjectMapper as a dependency)
            String decisionBlock = report.hasDecision()
                    ? ",\"decisionOutcome\":\"" + report.decision().getOutcome() + "\""
                      + ",\"decisionConfidence\":" + report.decision().getConfidenceScore()
                    : "";

            return "{\"applicationId\":\"" + applicationId + "\""
                    + ",\"totalEvents\":"         + report.getTotalEvents()
                    + ",\"totalReasoningSteps\":"  + report.getTotalReasoningSteps()
                    + ",\"totalToolCalls\":"        + report.getTotalToolCalls()
                    + ",\"totalExecutionTimeMs\":" + report.getTotalExecutionTimeMs()
                    + ",\"allToolCallsSuccessful\":" + report.allToolCallsSuccessful()
                    + ",\"hasDecision\":"           + report.hasDecision()
                    + decisionBlock
                    + "}";

        } catch (IllegalArgumentException e) {
            log.warn("Invalid applicationId in URI '{}': {}", uri, e.getMessage());
            return "{\"error\":\"Invalid applicationId format — expected UUID\","
                    + "\"uri\":\"" + uri + "\"}";
        } catch (Exception e) {
            log.error("Error reading audit trail resource for URI '{}': {}", uri, e.getMessage(), e);
            return "{\"error\":\"Failed to retrieve audit trail\","
                    + "\"detail\":\"" + e.getMessage() + "\"}";
        }
    }
}

