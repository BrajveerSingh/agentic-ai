package com.bank.ata.audit.entity;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Captures tool invocations including inputs, outputs, and execution time.
 * Inputs are logged before execution; outputs are logged after.
 */
@Entity
@Table(name = "tool_call", indexes = {
    @Index(name = "idx_tool_call_event", columnList = "event_id"),
    @Index(name = "idx_tool_call_name", columnList = "tool_name")
})
public class ToolCallEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tool_call_id", updatable = false, nullable = false)
    private UUID toolCallId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "tool_name", nullable = false, updatable = false, length = 100)
    private String toolName;

    @Column(name = "input_params", columnDefinition = "TEXT", updatable = false)
    private String inputParams;

    @Column(name = "output_result", columnDefinition = "TEXT")
    private String outputResult;

    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    @Column(name = "success", nullable = false)
    private boolean success = true;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    protected ToolCallEntity() {
        // JPA required
    }

    public ToolCallEntity(UUID eventId, String toolName, String inputParams) {
        this.eventId = eventId;
        this.toolName = toolName;
        this.inputParams = inputParams;
    }

    /**
     * Mark the tool call as successfully completed.
     */
    public void complete(String outputResult, long executionTimeMs) {
        this.outputResult = outputResult;
        this.executionTimeMs = executionTimeMs;
        this.success = true;
    }

    /**
     * Mark the tool call as failed.
     */
    public void fail(String errorMessage, long executionTimeMs) {
        this.errorMessage = errorMessage;
        this.executionTimeMs = executionTimeMs;
        this.success = false;
    }

    // Getters
    public UUID getToolCallId() { return toolCallId; }
    public UUID getEventId() { return eventId; }
    public String getToolName() { return toolName; }
    public String getInputParams() { return inputParams; }
    public String getOutputResult() { return outputResult; }
    public Long getExecutionTimeMs() { return executionTimeMs; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }

    @Override
    public String toString() {
        return "ToolCallEntity{" +
                "toolCallId=" + toolCallId +
                ", toolName='" + toolName + '\'' +
                ", success=" + success +
                ", executionTimeMs=" + executionTimeMs +
                '}';
    }
}

