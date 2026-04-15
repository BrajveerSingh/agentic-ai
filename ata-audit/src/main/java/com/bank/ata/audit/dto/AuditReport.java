package com.bank.ata.audit.dto;

import com.bank.ata.audit.entity.*;

import java.util.List;
import java.util.UUID;

/**
 * Comprehensive audit report containing all events for a loan application.
 */
public record AuditReport(
    UUID applicationId,
    List<AuditEventEntity> events,
    List<ReasoningStepEntity> reasoningSteps,
    List<ToolCallEntity> toolCalls,
    LoanDecisionEntity decision
) {
    /**
     * Total number of audit events.
     */
    public int getTotalEvents() {
        return events != null ? events.size() : 0;
    }

    /**
     * Total number of tool calls.
     */
    public int getTotalToolCalls() {
        return toolCalls != null ? toolCalls.size() : 0;
    }

    /**
     * Total execution time across all tool calls.
     */
    public long getTotalExecutionTimeMs() {
        if (toolCalls == null) return 0;
        return toolCalls.stream()
            .filter(tc -> tc.getExecutionTimeMs() != null)
            .mapToLong(ToolCallEntity::getExecutionTimeMs)
            .sum();
    }

    /**
     * Number of reasoning steps.
     */
    public int getTotalReasoningSteps() {
        return reasoningSteps != null ? reasoningSteps.size() : 0;
    }

    /**
     * Check if all tool calls were successful.
     */
    public boolean allToolCallsSuccessful() {
        if (toolCalls == null || toolCalls.isEmpty()) return true;
        return toolCalls.stream().allMatch(ToolCallEntity::isSuccess);
    }

    /**
     * Check if a final decision was made.
     */
    public boolean hasDecision() {
        return decision != null;
    }
}

