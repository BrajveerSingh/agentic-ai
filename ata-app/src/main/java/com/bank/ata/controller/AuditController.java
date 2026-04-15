package com.bank.ata.controller;

import com.bank.ata.audit.dto.AuditReport;
import com.bank.ata.audit.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for audit trail retrieval.
 * Provides read-only access to audit data for compliance officers.
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private static final Logger log = LoggerFactory.getLogger(AuditController.class);

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Get complete audit trail for a loan application.
     */
    @GetMapping("/application/{applicationId}")
    public ResponseEntity<AuditReportResponse> getAuditTrail(
            @PathVariable UUID applicationId) {

        log.info("Retrieving audit trail for application: {}", applicationId);

        if (!auditService.hasAuditTrail(applicationId)) {
            return ResponseEntity.notFound().build();
        }

        AuditReport report = auditService.getAuditTrail(applicationId);
        return ResponseEntity.ok(AuditReportResponse.from(report));
    }

    /**
     * Get audit trail by session ID.
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<AuditReportResponse> getAuditTrailBySession(
            @PathVariable UUID sessionId) {

        log.info("Retrieving audit trail for session: {}", sessionId);

        AuditReport report = auditService.getAuditTrailBySession(sessionId);

        if (report.getTotalEvents() == 0) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(AuditReportResponse.from(report));
    }

    /**
     * Check if audit trail exists for an application.
     */
    @GetMapping("/application/{applicationId}/exists")
    public ResponseEntity<Map<String, Boolean>> checkAuditExists(
            @PathVariable UUID applicationId) {

        boolean exists = auditService.hasAuditTrail(applicationId);
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    /**
     * Response DTO for audit reports.
     */
    public record AuditReportResponse(
            UUID applicationId,
            int totalEvents,
            int totalReasoningSteps,
            int totalToolCalls,
            long totalExecutionTimeMs,
            boolean allToolCallsSuccessful,
            boolean hasDecision,
            String decisionOutcome,
            java.util.List<EventSummary> events,
            java.util.List<ReasoningStepSummary> reasoningSteps,
            java.util.List<ToolCallSummary> toolCalls
    ) {
        public static AuditReportResponse from(AuditReport report) {
            return new AuditReportResponse(
                    report.applicationId(),
                    report.getTotalEvents(),
                    report.getTotalReasoningSteps(),
                    report.getTotalToolCalls(),
                    report.getTotalExecutionTimeMs(),
                    report.allToolCallsSuccessful(),
                    report.hasDecision(),
                    report.decision() != null ? report.decision().getOutcome() : null,
                    report.events().stream()
                            .map(e -> new EventSummary(
                                    e.getEventId(),
                                    e.getEventType(),
                                    e.getCreatedAt().toString()))
                            .toList(),
                    report.reasoningSteps().stream()
                            .map(s -> new ReasoningStepSummary(
                                    s.getStepNumber(),
                                    s.getThought(),
                                    s.getAction(),
                                    s.getObservation()))
                            .toList(),
                    report.toolCalls().stream()
                            .map(t -> new ToolCallSummary(
                                    t.getToolCallId(),
                                    t.getToolName(),
                                    t.isSuccess(),
                                    t.getExecutionTimeMs()))
                            .toList()
            );
        }
    }

    public record EventSummary(UUID eventId, String eventType, String timestamp) {}
    public record ReasoningStepSummary(int stepNumber, String thought, String action, String observation) {}
    public record ToolCallSummary(UUID toolCallId, String toolName, boolean success, Long executionTimeMs) {}
}

