package com.bank.ata.mcp.server;

import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring MVC controller that implements the MCP HTTP+SSE transport.
 *
 * <h3>Transport overview:</h3>
 * <ol>
 *   <li>Client opens {@code GET /mcp/sse} — receives an SSE stream.</li>
 *   <li>Server immediately sends an {@code endpoint} event:
 *       {@code data: /mcp/message?sessionId=<uuid>}</li>
 *   <li>Client POSTs JSON-RPC messages to {@code POST /mcp/message?sessionId=<uuid>}.</li>
 *   <li>Server processes the message and sends the response via the SSE stream.</li>
 * </ol>
 *
 * <h3>Claude Desktop integration:</h3>
 * Add to {@code claude_desktop_config.json}:
 * <pre>
 * {
 *   "mcpServers": {
 *     "audit-trail-agent": {
 *       "url": "http://localhost:8080/mcp/sse"
 *     }
 *   }
 * }
 * </pre>
 */
@RestController
@RequestMapping("/mcp")
public class McpController {

    private static final Logger log = LoggerFactory.getLogger(McpController.class);

    private final McpJsonRpcHandler handler;
    private final ObjectMapper      objectMapper;

    /** Active SSE sessions: sessionId → emitter. */
    private final ConcurrentHashMap<String, SseEmitter> sessions = new ConcurrentHashMap<>();

    public McpController(McpJsonRpcHandler handler, ObjectMapper objectMapper) {
        this.handler      = handler;
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------------------------
    // GET /mcp/sse  — SSE stream for MCP clients
    // -------------------------------------------------------------------------

    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect() {
        String sessionId = java.util.UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emitter.onCompletion(() -> sessions.remove(sessionId));
        emitter.onTimeout(()    -> sessions.remove(sessionId));
        emitter.onError(e       -> sessions.remove(sessionId));

        sessions.put(sessionId, emitter);

        try {
            // Tell the client where to POST its JSON-RPC messages
            emitter.send(SseEmitter.event()
                    .name("endpoint")
                    .data("/mcp/message?sessionId=" + sessionId));
            log.debug("MCP SSE session opened: {}", sessionId);
        } catch (IOException e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }

    // -------------------------------------------------------------------------
    // POST /mcp/message  — JSON-RPC message endpoint
    // -------------------------------------------------------------------------

    @PostMapping(value = "/message",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> message(
            @RequestParam(required = false) String sessionId,
            @RequestBody Map<String, Object> request) {

        Map<String, Object> response = handler.handle(request);

        if (response == null) {
            // Notification (e.g., notifications/initialized) — 204 No Content
            return ResponseEntity.noContent().build();
        }

        // Send response via SSE stream if session is active
        if (sessionId != null) {
            SseEmitter emitter = sessions.get(sessionId);
            if (emitter != null) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data(objectMapper.writeValueAsString(response)));
                } catch (Exception e) {
                    log.warn("Could not send SSE response for session {}: {}", sessionId, e.getMessage());
                    sessions.remove(sessionId);
                }
            }
        }

        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // GET /mcp/health  — simple liveness for monitoring
    // -------------------------------------------------------------------------

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "server", McpJsonRpcHandler.SERVER_NAME,
                "version", McpJsonRpcHandler.SERVER_VERSION,
                "protocol", McpJsonRpcHandler.PROTOCOL_VERSION,
                "activeSessions", sessions.size()));
    }
}

