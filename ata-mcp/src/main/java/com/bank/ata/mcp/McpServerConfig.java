package com.bank.ata.mcp;

import com.bank.ata.mcp.server.McpResourceRegistry;
import com.bank.ata.mcp.server.McpToolRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * Wires tools and resources into their respective registries at startup.
 *
 * <ul>
 *   <li>{@link AuditMcpTools} → {@link McpToolRegistry}
 *       (tools: evaluate_loan, get_audit_trail, search_audit_reasoning,
 *        get_credit_score, check_compliance)</li>
 *   <li>{@link LoanPolicyResources} → {@link McpResourceRegistry}
 *       (resources: loan://policies, loan://policies/{policyId})</li>
 *   <li>{@link AuditTrailResources} → {@link McpResourceRegistry}
 *       (resource template: audit://trail/{applicationId})</li>
 * </ul>
 */
@Configuration
public class McpServerConfig {

    private static final Logger log = LoggerFactory.getLogger(McpServerConfig.class);

    private final McpToolRegistry     toolRegistry;
    private final McpResourceRegistry resourceRegistry;
    private final AuditMcpTools       auditMcpTools;
    private final LoanPolicyResources loanPolicyResources;
    private final AuditTrailResources auditTrailResources;

    public McpServerConfig(McpToolRegistry     toolRegistry,
                           McpResourceRegistry resourceRegistry,
                           AuditMcpTools       auditMcpTools,
                           LoanPolicyResources loanPolicyResources,
                           AuditTrailResources auditTrailResources) {
        this.toolRegistry     = toolRegistry;
        this.resourceRegistry = resourceRegistry;
        this.auditMcpTools    = auditMcpTools;
        this.loanPolicyResources = loanPolicyResources;
        this.auditTrailResources = auditTrailResources;
    }

    @PostConstruct
    void register() {
        // Tools
        toolRegistry.register(auditMcpTools);

        // Resources
        resourceRegistry.register(loanPolicyResources);
        resourceRegistry.register(auditTrailResources);

        log.info("MCP server ready — {} tool(s), {} resource(s) registered. "
                 + "Endpoints: GET /mcp/sse  POST /mcp/message",
                 toolRegistry.size(), resourceRegistry.size());
    }
}
