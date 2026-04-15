package com.bank.ata.mcp.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * HTTP-based MCP client that connects to an external MCP server using {@link RestClient}.
 *
 * <p>Implements the minimal subset of the MCP protocol required by ATA:</p>
 * <ul>
 *   <li>{@link #initialize()}  — negotiates protocol version and capabilities</li>
 *   <li>{@link #listTools()}   — discovers available tools on the remote server</li>
 *   <li>{@link #callTool(String, Map)} — invokes a remote tool and returns its text content</li>
 * </ul>
 *
 * <p>This client communicates over HTTP by POSTing JSON-RPC 2.0 messages to the server's
 * {@code /mcp/message} endpoint.</p>
 */
public class McpClientService {

    private static final Logger log = LoggerFactory.getLogger(McpClientService.class);

    private final RestClient restClient;
    private final String     serverMessageUrl;
    private final String     serverName;

    private boolean initialized     = false;
    private int     requestIdCounter = 0;

    public McpClientService(RestClient restClient, String serverMessageUrl, String serverName) {
        this.restClient       = restClient;
        this.serverMessageUrl = serverMessageUrl;
        this.serverName       = serverName;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Perform the MCP {@code initialize} handshake.
     *
     * @return {@code true} if the server accepted the session; {@code false} on failure
     */
    public boolean initialize() {
        try {
            Map<String, Object> params = Map.of(
                    "protocolVersion", "2024-11-05",
                    "clientInfo",      Map.of("name", "audit-trail-agent-client", "version", "1.0.0"),
                    "capabilities",    Map.of("tools", Map.of())
            );
            Map<String, Object> response = sendRequest("initialize", params);

            if (response != null && !response.containsKey("error")) {
                initialized = true;
                sendNotification("notifications/initialized", Map.of());
                log.info("MCP client initialized successfully: server='{}'", serverName);
                return true;
            } else {
                log.warn("MCP initialize rejected by '{}': {}", serverName, response);
            }
        } catch (Exception e) {
            log.error("MCP client initialization failed for '{}': {}", serverName, e.getMessage());
        }
        return false;
    }

    /**
     * List available tools on the remote MCP server.
     *
     * @return list of tool descriptors, or empty list on failure
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listTools() {
        try {
            Map<String, Object> response = sendRequest("tools/list", Map.of());
            if (response != null && response.containsKey("result")) {
                Map<String, Object> result = (Map<String, Object>) response.get("result");
                return (List<Map<String, Object>>) result.getOrDefault("tools", List.of());
            }
        } catch (Exception e) {
            log.error("tools/list failed for '{}': {}", serverName, e.getMessage());
        }
        return List.of();
    }

    /**
     * Call a tool on the remote MCP server.
     *
     * @param toolName  name of the tool to invoke
     * @param arguments key-value arguments for the tool
     * @return the text content of the first content block, or {@code null}
     * @throws RuntimeException if the server returns an MCP error
     */
    @SuppressWarnings("unchecked")
    public String callTool(String toolName, Map<String, Object> arguments) {
        Map<String, Object> params = Map.of(
                "name",      toolName,
                "arguments", arguments != null ? arguments : Map.of()
        );

        Map<String, Object> response = sendRequest("tools/call", params);

        if (response == null) {
            throw new RuntimeException("No response from MCP server '" + serverName + "'");
        }

        if (response.containsKey("error")) {
            Map<String, Object> error = (Map<String, Object>) response.get("error");
            throw new RuntimeException("MCP tool call failed on '" + serverName
                    + "': " + error.get("message"));
        }

        Map<String, Object> result  = (Map<String, Object>) response.get("result");
        List<Map<String, Object>> content =
                (List<Map<String, Object>>) result.getOrDefault("content", List.of());

        return content.isEmpty() ? null : (String) content.get(0).get("text");
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public boolean isInitialized()  { return initialized; }
    public String  getServerName()  { return serverName;  }
    public String  getServerUrl()   { return serverMessageUrl; }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> sendRequest(String method, Map<String, Object> params) {
        Map<String, Object> body = buildRequest(method, params);
        try {
            return restClient.post()
                    .uri(serverMessageUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.error("MCP HTTP request '{}' failed for '{}': {}", method, serverName, e.getMessage());
            return null;
        }
    }

    private void sendNotification(String method, Map<String, Object> params) {
        try {
            Map<String, Object> notification = new LinkedHashMap<>();
            notification.put("jsonrpc", "2.0");
            notification.put("method",  method);
            notification.put("params",  params);

            restClient.post()
                    .uri(serverMessageUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(notification)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            // Notifications are fire-and-forget; failure is non-critical
            log.debug("Notification '{}' send failed (non-critical) for '{}': {}",
                      method, serverName, e.getMessage());
        }
    }

    private Map<String, Object> buildRequest(String method, Map<String, Object> params) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("jsonrpc", "2.0");
        req.put("id",      ++requestIdCounter);
        req.put("method",  method);
        req.put("params",  params);
        return req;
    }
}

