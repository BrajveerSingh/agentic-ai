package com.bank.ata.mcp;

import com.bank.ata.mcp.server.McpJsonRpcHandler;
import com.bank.ata.mcp.server.McpResourceRegistry;
import com.bank.ata.mcp.server.McpToolRegistry;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link McpJsonRpcHandler}.
 * Covers all supported MCP protocol methods and error paths.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class McpJsonRpcHandlerTest {

    @Mock private McpToolRegistry     toolRegistry;
    @Mock private McpResourceRegistry resourceRegistry;

    private McpJsonRpcHandler handler;

    @BeforeEach
    void setUp() {
        handler = new McpJsonRpcHandler(toolRegistry, resourceRegistry, new ObjectMapper());
    }

    // =========================================================================
    // initialize
    // =========================================================================

    @Test
    @DisplayName("initialize: should return protocol version, server info, and both capabilities")
    void initialize_shouldReturnCapabilities() {
        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0", "id", 1, "method", "initialize",
                "params",  Map.of("protocolVersion", "2024-11-05"));

        Map<String, Object> response = handler.handle(request);

        assertThat(response.get("jsonrpc")).isEqualTo("2.0");
        assertThat(response.get("id")).isEqualTo(1);

        Map<String, Object> result = (Map<String, Object>) response.get("result");
        assertThat(result.get("protocolVersion")).isEqualTo("2024-11-05");

        Map<String, Object> capabilities = (Map<String, Object>) result.get("capabilities");
        assertThat(capabilities).containsKey("tools");
        assertThat(capabilities).containsKey("resources");

        Map<String, Object> serverInfo = (Map<String, Object>) result.get("serverInfo");
        assertThat(serverInfo.get("name")).isEqualTo("audit-trail-agent");
    }

    @Test
    @DisplayName("notifications/initialized: should return null (no response for notifications)")
    void notificationsInitialized_shouldReturnNull() {
        Map<String, Object> request = Map.of("jsonrpc", "2.0", "method", "notifications/initialized");
        assertThat(handler.handle(request)).isNull();
    }

    // =========================================================================
    // tools/list
    // =========================================================================

    @Test
    @DisplayName("tools/list: should return all registered tools")
    void toolsList_shouldReturnTools() {
        when(toolRegistry.listTools()).thenReturn(List.of(
                Map.of("name", "evaluate_loan", "description", "Evaluate loan"),
                Map.of("name", "get_credit_score", "description", "Get credit score")));

        Map<String, Object> request = Map.of("jsonrpc", "2.0", "id", 2, "method", "tools/list");
        Map<String, Object> response = handler.handle(request);

        Map<String, Object> result = (Map<String, Object>) response.get("result");
        List<?> tools = (List<?>) result.get("tools");
        assertThat(tools).hasSize(2);
    }

    // =========================================================================
    // tools/call
    // =========================================================================

    @Test
    @DisplayName("tools/call: should invoke a known tool and return content block")
    void toolsCall_knownTool_shouldReturnContent() {
        when(toolRegistry.hasTool("get_credit_score")).thenReturn(true);
        when(toolRegistry.call("get_credit_score", Map.of("customerId", "CUST001")))
                .thenReturn("{\"score\":720}");

        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0", "id", 3, "method", "tools/call",
                "params",  Map.of("name", "get_credit_score",
                                  "arguments", Map.of("customerId", "CUST001")));

        Map<String, Object> response = handler.handle(request);

        Map<String, Object> result = (Map<String, Object>) response.get("result");
        List<Map<String, String>> content = (List<Map<String, String>>) result.get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("type")).isEqualTo("text");
        assertThat(content.get(0).get("text")).contains("720");
    }

    @Test
    @DisplayName("tools/call: unknown tool should return JSON-RPC error -32602")
    void toolsCall_unknownTool_shouldReturnError() {
        when(toolRegistry.hasTool("nonexistent")).thenReturn(false);

        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0", "id", 4, "method", "tools/call",
                "params",  Map.of("name", "nonexistent"));

        Map<String, Object> response = handler.handle(request);

        assertThat(response).containsKey("error");
        Map<String, Object> error = (Map<String, Object>) response.get("error");
        assertThat((int) error.get("code")).isEqualTo(-32602);
    }

    @Test
    @DisplayName("tools/call: missing params should return JSON-RPC error -32602")
    void toolsCall_missingParams_shouldReturnError() {
        Map<String, Object> request = Map.of("jsonrpc", "2.0", "id", 5, "method", "tools/call");
        Map<String, Object> response = handler.handle(request);

        assertThat(response).containsKey("error");
        Map<String, Object> error = (Map<String, Object>) response.get("error");
        assertThat((int) error.get("code")).isEqualTo(-32602);
    }

    // =========================================================================
    // resources/list
    // =========================================================================

    @Test
    @DisplayName("resources/list: should return static resources and URI templates")
    void resourcesList_shouldReturnResourcesAndTemplates() {
        when(resourceRegistry.listResources()).thenReturn(List.of(
                Map.of("uri", "loan://policies", "name", "Loan Policy Index")));
        when(resourceRegistry.listTemplates()).thenReturn(List.of(
                Map.of("uriTemplate", "loan://policies/{policyId}", "name", "Loan Policy")));

        Map<String, Object> request = Map.of("jsonrpc", "2.0", "id", 6, "method", "resources/list");
        Map<String, Object> response = handler.handle(request);

        assertThat(response.get("id")).isEqualTo(6);
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        assertThat((List<?>) result.get("resources")).hasSize(1);
        assertThat((List<?>) result.get("resourceTemplates")).hasSize(1);
    }

    // =========================================================================
    // resources/read
    // =========================================================================

    @Test
    @DisplayName("resources/read: should return content block for a known URI")
    void resourcesRead_knownUri_shouldReturnContent() {
        when(resourceRegistry.read("loan://policies")).thenReturn(
                new McpResourceRegistry.ResourceResult(
                        "loan://policies", "application/json", "{\"policies\":[]}"));

        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0", "id", 7, "method", "resources/read",
                "params",  Map.of("uri", "loan://policies"));

        Map<String, Object> response = handler.handle(request);

        Map<String, Object> result = (Map<String, Object>) response.get("result");
        List<Map<String, Object>> contents = (List<Map<String, Object>>) result.get("contents");
        assertThat(contents).hasSize(1);
        assertThat(contents.get(0).get("uri")).isEqualTo("loan://policies");
        assertThat(contents.get(0).get("mimeType")).isEqualTo("application/json");
        assertThat((String) contents.get(0).get("text")).contains("policies");
    }

    @Test
    @DisplayName("resources/read: unknown URI should return JSON-RPC error -32002")
    void resourcesRead_unknownUri_shouldReturnError() {
        when(resourceRegistry.read("loan://unknown")).thenReturn(null);

        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0", "id", 8, "method", "resources/read",
                "params",  Map.of("uri", "loan://unknown"));

        Map<String, Object> response = handler.handle(request);

        assertThat(response).containsKey("error");
        Map<String, Object> error = (Map<String, Object>) response.get("error");
        assertThat((int) error.get("code")).isEqualTo(-32002);
    }

    @Test
    @DisplayName("resources/read: missing params should return JSON-RPC error -32602")
    void resourcesRead_missingParams_shouldReturnError() {
        Map<String, Object> request = Map.of("jsonrpc", "2.0", "id", 9, "method", "resources/read");
        Map<String, Object> response = handler.handle(request);

        assertThat(response).containsKey("error");
        Map<String, Object> error = (Map<String, Object>) response.get("error");
        assertThat((int) error.get("code")).isEqualTo(-32602);
    }

    // =========================================================================
    // Unknown method
    // =========================================================================

    @Test
    @DisplayName("unknown method should return JSON-RPC error -32601")
    void unknownMethod_shouldReturnMethodNotFoundError() {
        Map<String, Object> request = Map.of("jsonrpc", "2.0", "id", 10, "method", "ping");
        Map<String, Object> response = handler.handle(request);

        assertThat(response).containsKey("error");
        Map<String, Object> error = (Map<String, Object>) response.get("error");
        assertThat((int) error.get("code")).isEqualTo(-32601);
        assertThat((String) error.get("message")).contains("ping");
    }

    @Test
    @DisplayName("missing method field should return JSON-RPC error -32600")
    void missingMethod_shouldReturnInvalidRequestError() {
        Map<String, Object> request = Map.of("jsonrpc", "2.0", "id", 11);
        Map<String, Object> response = handler.handle(request);

        assertThat(response).containsKey("error");
        Map<String, Object> error = (Map<String, Object>) response.get("error");
        assertThat((int) error.get("code")).isEqualTo(-32600);
    }
}

