package com.bank.ata.gateway.controller;

import com.bank.ata.gateway.client.OrchestratorClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Public audit retrieval API that forwards to orchestrator.
 */
@RestController
@RequestMapping("/api/audit")
public class GatewayAuditController {

    private final OrchestratorClient orchestratorClient;
    private final String orchestratorBaseUrl;

    public GatewayAuditController(
            OrchestratorClient orchestratorClient,
            @Value("${ata.orchestrator.base-url}") String orchestratorBaseUrl) {
        this.orchestratorClient = orchestratorClient;
        this.orchestratorBaseUrl = orchestratorBaseUrl;
    }

    @GetMapping("/application/{applicationId}")
    public ResponseEntity<Map<String, Object>> byApplication(@PathVariable String applicationId) {
        return ResponseEntity.ok(orchestratorClient.getAuditByApplicationId(orchestratorBaseUrl, applicationId));
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<Map<String, Object>> bySession(@PathVariable String sessionId) {
        return ResponseEntity.ok(orchestratorClient.getAuditBySessionId(orchestratorBaseUrl, sessionId));
    }
}

