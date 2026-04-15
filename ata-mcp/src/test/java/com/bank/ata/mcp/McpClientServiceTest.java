package com.bank.ata.mcp;

import com.bank.ata.mcp.client.McpClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link McpClientService}.
 *
 * <p>Uses explicit mocks for each step of the {@link RestClient} fluent chain
 * so that Mockito's strict-stubbing mode works cleanly with raw-type responses
 * (e.g. {@code Map.class}) that cause false "UnnecessaryStubbing" warnings with
 * deep-stub mocking.</p>
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class McpClientServiceTest {

    private static final String SERVER_URL  = "http://localhost:8080/mcp/message";
    private static final String SERVER_NAME = "test-mcp-server";

    // Explicit mocks for each step of the RestClient chain
    @Mock private RestClient                       restClient;
    @Mock private RestClient.RequestBodyUriSpec    postSpec;
    @Mock private RestClient.RequestBodySpec       bodySpec;
    @Mock private RestClient.ResponseSpec          responseSpec;

    private McpClientService client;

    @BeforeEach
    void setUp() {
        // Wire the fluent chain with lenient stubs.
        // In Spring 7, RequestBodySpec.body(Object) returns RequestBodySpec (fluent),
        // so the chain is: post() → uri() → contentType() → body() → retrieve() → body(Class)
        lenient().when(restClient.post()).thenReturn(postSpec);
        lenient().when(postSpec.uri(anyString())).thenReturn(bodySpec);
        lenient().when(bodySpec.contentType(any())).thenReturn(bodySpec);
        lenient().when(bodySpec.body((Object) any())).thenReturn(bodySpec);
        lenient().when(bodySpec.retrieve()).thenReturn(responseSpec);

        client = new McpClientService(restClient, SERVER_URL, SERVER_NAME);
    }

    // =========================================================================
    // initialize
    // =========================================================================

    @Test
    @DisplayName("initialize: should return true and set initialized=true on successful handshake")
    void initialize_successfulHandshake_shouldReturnTrue() {
        Map<String, Object> initResult = Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities",    Map.of("tools", Map.of()),
                "serverInfo",      Map.of("name", "remote-server", "version", "1.0.0"));
        Map<String, Object> initResponse = Map.of("jsonrpc", "2.0", "id", 1, "result", initResult);

        when(responseSpec.body(Map.class)).thenReturn(initResponse);
        when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());

        boolean result = client.initialize();

        assertThat(result).isTrue();
        assertThat(client.isInitialized()).isTrue();
    }

    @Test
    @DisplayName("initialize: should return false when server returns an error")
    void initialize_serverError_shouldReturnFalse() {
        Map<String, Object> errorResponse = Map.of(
                "jsonrpc", "2.0", "id", 1,
                "error",   Map.of("code", -32600, "message", "Bad request"));

        when(responseSpec.body(Map.class)).thenReturn(errorResponse);

        boolean result = client.initialize();

        assertThat(result).isFalse();
        assertThat(client.isInitialized()).isFalse();
    }

    @Test
    @DisplayName("initialize: should return false when HTTP call throws an exception")
    void initialize_networkError_shouldReturnFalse() {
        when(responseSpec.body(Map.class)).thenThrow(new RestClientException("Connection refused"));

        boolean result = client.initialize();

        assertThat(result).isFalse();
        assertThat(client.isInitialized()).isFalse();
    }

    // =========================================================================
    // listTools
    // =========================================================================

    @Test
    @DisplayName("listTools: should parse and return tools from server response")
    void listTools_shouldReturnParsedTools() {
        List<Map<String, Object>> tools = List.of(
                Map.of("name", "get_credit_score_external", "description", "External credit score"));
        Map<String, Object> response = Map.of(
                "jsonrpc", "2.0", "id", 1,
                "result",  Map.of("tools", tools));

        when(responseSpec.body(Map.class)).thenReturn(response);

        List<Map<String, Object>> result = client.listTools();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("name")).isEqualTo("get_credit_score_external");
    }

    @Test
    @DisplayName("listTools: should return empty list when server is unreachable")
    void listTools_networkError_shouldReturnEmptyList() {
        when(responseSpec.body(Map.class)).thenThrow(new RestClientException("Timeout"));

        List<Map<String, Object>> result = client.listTools();

        assertThat(result).isEmpty();
    }

    // =========================================================================
    // callTool
    // =========================================================================

    @Test
    @DisplayName("callTool: should return text content from the first content block")
    void callTool_successfulCall_shouldReturnText() {
        String toolResult = "{\"customerId\":\"CUST001\",\"score\":720}";
        Map<String, Object> response = Map.of(
                "jsonrpc", "2.0", "id", 1,
                "result",  Map.of("content", List.of(
                        Map.of("type", "text", "text", toolResult))));

        when(responseSpec.body(Map.class)).thenReturn(response);

        String result = client.callTool("get_credit_score_external", Map.of("customerId", "CUST001"));

        assertThat(result).isEqualTo(toolResult);
        assertThat(result).contains("720");
    }

    @Test
    @DisplayName("callTool: server error response should throw RuntimeException")
    void callTool_serverError_shouldThrowException() {
        Map<String, Object> response = Map.of(
                "jsonrpc", "2.0", "id", 1,
                "error",   Map.of("code", -32602, "message", "Unknown tool"));

        when(responseSpec.body(Map.class)).thenReturn(response);

        assertThatThrownBy(() -> client.callTool("unknown_tool", Map.of()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unknown tool");
    }

    @Test
    @DisplayName("callTool: null server response should throw RuntimeException")
    void callTool_nullResponse_shouldThrowException() {
        when(responseSpec.body(Map.class)).thenReturn(null);

        assertThatThrownBy(() -> client.callTool("any_tool", Map.of()))
                .isInstanceOf(RuntimeException.class);
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    @Test
    @DisplayName("getServerName and getServerUrl should return configured values")
    void accessors_shouldReturnConfiguredValues() {
        assertThat(client.getServerName()).isEqualTo(SERVER_NAME);
        assertThat(client.getServerUrl()).isEqualTo(SERVER_URL);
        assertThat(client.isInitialized()).isFalse();
    }
}

