package com.bank.ata.orchestrator.controller;

import com.bank.ata.agent.AuditTrailAgent;
import com.bank.ata.audit.service.AuditContextHolder;
import com.bank.ata.audit.service.AuditService;
import com.bank.ata.core.domain.LoanApplication;
import com.bank.ata.core.domain.LoanDecision;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Internal REST API used by the gateway.
 */
@RestController
@RequestMapping("/internal")
public class InternalOrchestratorController {

    private final AuditTrailAgent agent;
    private final AuditService auditService;

    public InternalOrchestratorController(AuditTrailAgent agent, AuditService auditService) {
        this.agent = agent;
        this.auditService = auditService;
    }

    @PostMapping("/loans/evaluate")
    public ResponseEntity<Map<String, Object>> evaluateLoan(@RequestBody Map<String, Object> request) {
        String customerId = (String) request.get("customerId");
        String purpose = (String) request.get("purpose");
        Object amountObj = request.get("amount");
        BigDecimal amount = amountObj instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : new BigDecimal(amountObj.toString());

        LoanApplication application = LoanApplication.create(customerId, amount, purpose,
                com.bank.ata.core.domain.LoanType.valueOf(
                        ((String) request.getOrDefault("loanType", "PERSONAL")).toUpperCase()));

        UUID sessionId = UUID.randomUUID();

        LoanDecision decision = AuditContextHolder.callWithContext(
                sessionId, application.applicationId(), () -> {
                    auditService.logSessionStart(sessionId, application.applicationId(), null);
                    LoanDecision d = agent.evaluateLoan(application);
                    auditService.logSessionEnd(sessionId, application.applicationId());
                    auditService.logDecision(sessionId, application.applicationId(), d.outcome().name(), d.reasoning(), d.confidenceScore());
                    return d;
                });

        return ResponseEntity.ok(Map.of(
                "applicationId", application.applicationId().toString(),
                "customerId", customerId,
                "amount", amount,
                "purpose", purpose,
                "outcome", decision.outcome().name(),
                "reasoning", decision.reasoning(),
                "confidenceScore", decision.confidenceScore(),
                "sessionId", sessionId.toString()
        ));
    }

    @GetMapping("/audit/application/{applicationId}")
    public ResponseEntity<String> getAuditByApplication(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(auditService.getAuditTrail(applicationId).toString());
    }

    @GetMapping("/audit/session/{sessionId}")
    public ResponseEntity<String> getAuditBySession(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(auditService.getAuditTrailBySession(sessionId).toString());
    }
}

