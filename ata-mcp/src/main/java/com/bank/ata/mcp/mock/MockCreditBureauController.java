package com.bank.ata.mcp.mock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Mock Credit Bureau MCP Server — for development and integration testing only.
 *
 * <p>Simulates an external credit-bureau service that exposes a single MCP tool:
 * {@code get_credit_score_external}.  The {@link com.bank.ata.mcp.client.McpClientService}
 * ({@code creditBureauMcpClient} bean) is configured by default to point here.</p>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /mock/credit-bureau-mcp/message} — JSON-RPC 2.0 message handler</li>
 *   <li>{@code GET  /mock/credit-bureau-mcp/health}  — liveness probe</li>
 * </ul>
 *
 * <h3>Supported MCP methods</h3>
 * <ul>
 *   <li>{@code initialize}               — negotiation handshake</li>
 *   <li>{@code notifications/initialized}— client ACK (204 No Content)</li>
 *   <li>{@code tools/list}               — returns the {@code get_credit_score_external} tool</li>
 *   <li>{@code tools/call}               — invokes {@code get_credit_score_external}</li>
 * </ul>
 *
 * <p><strong>Note:</strong> disable in production by setting
 * {@code mcp.mock.credit-bureau.enabled=false}.</p>
 */
@RestController
@ConditionalOnProperty(name = "mcp.mock.credit-bureau.enabled", havingValue = "true")
@RequestMapping(value = "/mock/credit-bureau-mcp", produces = MediaType.APPLICATION_JSON_VALUE)
public class MockCreditBureauController {

    private static final Logger log = LoggerFactory.getLogger(MockCreditBureauController.class);

    // -------------------------------------------------------------------------
    // Message endpoint
    // -------------------------------------------------------------------------

    @PostMapping("/message")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> handleMessage(
            @RequestBody Map<String, Object> request) {

        Object id     = request.get("id");
        String method = (String) request.get("method");

        if (method == null) {
            return ResponseEntity.ok(errorResponse(id, -32600, "Missing 'method'"));
        }

        log.debug("Mock Credit Bureau MCP: method={}", method);

        return switch (method) {
            case "initialize"                -> ResponseEntity.ok(handleInitialize(id));
            case "notifications/initialized" -> ResponseEntity.noContent().build();
            case "tools/list"                -> ResponseEntity.ok(handleToolsList(id));
            case "tools/call"                -> ResponseEntity.ok(
                    handleToolsCall(id, (Map<String, Object>) request.get("params")));
            default -> ResponseEntity.ok(
                    errorResponse(id, -32601, "Method not found: " + method));
        };
    }

    // -------------------------------------------------------------------------
    // Health endpoint
    // -------------------------------------------------------------------------

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "server", "mock-credit-bureau-mcp",
                "note",   "Development mock — not for production use"));
    }

    // -------------------------------------------------------------------------
    // Method handlers
    // -------------------------------------------------------------------------

    private Map<String, Object> handleInitialize(Object id) {
        return successResponse(id, Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities",    Map.of("tools", Map.of()),
                "serverInfo",      Map.of("name", "mock-credit-bureau-mcp", "version", "1.0.0")));
    }

    private Map<String, Object> handleToolsList(Object id) {
        List<Map<String, Object>> tools = List.of(Map.of(
                "name",        "get_credit_score_external",
                "description", "Retrieve credit score from the Credit Bureau for a given customer ID.",
                "inputSchema", Map.of(
                        "type",       "object",
                        "properties", Map.of(
                                "customerId", Map.of(
                                        "type",        "string",
                                        "description", "Unique customer identifier")),
                        "required",   List.of("customerId"))));

        return successResponse(id, Map.of("tools", tools));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleToolsCall(Object id, Map<String, Object> params) {
        if (params == null) {
            return errorResponse(id, -32602, "Missing params");
        }

        String toolName = (String) params.get("name");
        if (!"get_credit_score_external".equals(toolName)) {
            return errorResponse(id, -32602, "Unknown tool: " + toolName);
        }

        Map<String, Object> args = (Map<String, Object>) params.getOrDefault("arguments", Map.of());
        String customerId = (String) args.get("customerId");

        if (customerId == null || customerId.isBlank()) {
            return errorResponse(id, -32602, "Missing required argument: customerId");
        }

        // Deterministic mock score derived from customerId hash
        int    score = 550 + (Math.abs(customerId.hashCode()) % 251);
        String tier  = score >= 750 ? "EXCELLENT"
                     : score >= 700 ? "GOOD"
                     : score >= 650 ? "FAIR"
                     :                "POOR";

        String resultJson = String.format(
                "{\"customerId\":\"%s\",\"score\":%d,\"tier\":\"%s\",\"source\":\"MOCK_CREDIT_BUREAU\"}",
                customerId, score, tier);

        log.debug("Mock credit score: customerId={}, score={}, tier={}", customerId, score, tier);

        return successResponse(id, Map.of(
                "content", List.of(Map.of("type", "text", "text", resultJson))));
    }

    // -------------------------------------------------------------------------
    // JSON-RPC helpers
    // -------------------------------------------------------------------------

    private static Map<String, Object> successResponse(Object id, Object result) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("id",      id);
        resp.put("result",  result);
        return resp;
    }

    private static Map<String, Object> errorResponse(Object id, int code, String message) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("id",      id);
        resp.put("error",   Map.of("code", code, "message", message));
        return resp;
    }
}

