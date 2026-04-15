package com.bank.ata.mcp.server;

import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Handles MCP JSON-RPC 2.0 message routing.
 *
 * <p>Implements the MCP protocol methods:</p>
 * <ul>
 *   <li>{@code initialize}              — negotiate protocol version and return capabilities</li>
 *   <li>{@code notifications/initialized} — ACK from client (no response needed)</li>
 *   <li>{@code tools/list}              — enumerate available tools with their JSON Schemas</li>
 *   <li>{@code tools/call}              — invoke a tool and return content blocks</li>
 *   <li>{@code resources/list}          — enumerate available resources and templates</li>
 *   <li>{@code resources/read}          — read a resource by URI</li>
 * </ul>
 *
 * <p>Protocol reference: <a href="https://spec.modelcontextprotocol.io">MCP Spec</a></p>
 */
@Component
public class McpJsonRpcHandler {

    private static final Logger log = LoggerFactory.getLogger(McpJsonRpcHandler.class);

    static final String PROTOCOL_VERSION = "2024-11-05";
    static final String SERVER_NAME      = "audit-trail-agent";
    static final String SERVER_VERSION   = "1.0.0";

    private final McpToolRegistry     toolRegistry;
    private final McpResourceRegistry resourceRegistry;
    private final ObjectMapper        objectMapper;

    public McpJsonRpcHandler(McpToolRegistry toolRegistry,
                             McpResourceRegistry resourceRegistry,
                             ObjectMapper objectMapper) {
        this.toolRegistry     = toolRegistry;
        this.resourceRegistry = resourceRegistry;
        this.objectMapper     = objectMapper;
    }

    // -------------------------------------------------------------------------
    // Dispatch
    // -------------------------------------------------------------------------

    /**
     * Process one JSON-RPC request object and return a response object (or null
     * for notifications that require no response).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> handle(Map<String, Object> request) {
        Object id     = request.get("id");
        String method = (String) request.get("method");

        if (method == null) {
            return errorResponse(id, -32600, "Invalid Request: missing 'method'");
        }

        log.debug("MCP request: method={}, id={}", method, id);

        return switch (method) {
            case "initialize"                 -> handleInitialize(id, (Map<String,Object>) request.get("params"));
            case "notifications/initialized"  -> null; // notification — no response
            case "tools/list"                 -> handleToolsList(id);
            case "tools/call"                 -> handleToolsCall(id, (Map<String,Object>) request.get("params"));
            case "resources/list"             -> handleResourcesList(id);
            case "resources/read"             -> handleResourcesRead(id, (Map<String,Object>) request.get("params"));
            default -> errorResponse(id, -32601, "Method not found: " + method);
        };
    }

    // -------------------------------------------------------------------------
    // Method handlers
    // -------------------------------------------------------------------------

    private Map<String, Object> handleInitialize(Object id, Map<String, Object> params) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", PROTOCOL_VERSION);
        result.put("capabilities", Map.of(
                "tools",     Map.of(),
                "resources", Map.of()));
        result.put("serverInfo", Map.of("name", SERVER_NAME, "version", SERVER_VERSION));
        return successResponse(id, result);
    }

    private Map<String, Object> handleToolsList(Object id) {
        return successResponse(id, Map.of("tools", toolRegistry.listTools()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleToolsCall(Object id, Map<String, Object> params) {
        if (params == null) {
            return errorResponse(id, -32602, "Invalid params: 'params' is required for tools/call");
        }

        String toolName = (String) params.get("name");
        Map<String, Object> arguments = (Map<String, Object>) params.getOrDefault("arguments", Map.of());

        if (toolName == null || toolName.isBlank()) {
            return errorResponse(id, -32602, "Invalid params: 'name' is required");
        }

        if (!toolRegistry.hasTool(toolName)) {
            return errorResponse(id, -32602, "Unknown tool: " + toolName);
        }

        try {
            String resultText = toolRegistry.call(toolName, arguments);
            // MCP content block format
            List<Map<String, String>> content = List.of(Map.of("type", "text", "text", resultText));
            return successResponse(id, Map.of("content", content));
        } catch (Exception e) {
            log.error("Tool '{}' threw an exception: {}", toolName, e.getMessage(), e);
            return errorResponse(id, -32603, "Tool execution failed: " + e.getMessage());
        }
    }

    private Map<String, Object> handleResourcesList(Object id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resources",         resourceRegistry.listResources());
        result.put("resourceTemplates", resourceRegistry.listTemplates());
        return successResponse(id, result);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleResourcesRead(Object id, Map<String, Object> params) {
        if (params == null) {
            return errorResponse(id, -32602, "Invalid params: 'params' is required for resources/read");
        }

        String uri = (String) params.get("uri");
        if (uri == null || uri.isBlank()) {
            return errorResponse(id, -32602, "Invalid params: 'uri' is required");
        }

        try {
            McpResourceRegistry.ResourceResult result = resourceRegistry.read(uri);
            if (result == null) {
                return errorResponse(id, -32002, "Resource not found: " + uri);
            }
            List<Map<String, Object>> contents = List.of(Map.of(
                    "uri",      result.uri(),
                    "mimeType", result.mimeType(),
                    "text",     result.content()));
            return successResponse(id, Map.of("contents", contents));
        } catch (Exception e) {
            log.error("Resource read failed for URI '{}': {}", uri, e.getMessage(), e);
            return errorResponse(id, -32603, "Resource read failed: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // JSON-RPC helpers
    // -------------------------------------------------------------------------

    static Map<String, Object> successResponse(Object id, Object result) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("id", id);
        resp.put("result", result);
        return resp;
    }

    static Map<String, Object> errorResponse(Object id, int code, String message) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("id", id);
        resp.put("error", Map.of("code", code, "message", message));
        return resp;
    }
}

