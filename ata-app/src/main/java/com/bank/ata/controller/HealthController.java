package com.bank.ata.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Health check and status endpoints.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "application", "Audit Trail Agent",
                "version", "1.0.0-SNAPSHOT",
                "timestamp", LocalDateTime.now()
        ));
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        return ResponseEntity.ok(Map.of(
                "name", "Audit Trail Agent",
                "description", "Bank-grade AI agent for loan evaluation with immutable audit trail",
                "version", "1.0.0-SNAPSHOT",
                "java", System.getProperty("java.version"),
                "features", Map.of(
                        "agent", "LangChain4j ReAct Agent",
                        "llm", "Ollama (dev) / vLLM (prod)",
                        "audit", "Immutable PostgreSQL",
                        "protocols", "MCP, A2A"
                )
        ));
    }
}

