package com.bank.ata.audit.entity;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Captures reasoning steps (Thought/Action/Observation) from the ReAct agent.
 * Immutable once created.
 */
@Entity
@Table(name = "reasoning_step", indexes = {
    @Index(name = "idx_reasoning_event", columnList = "event_id")
})
public class ReasoningStepEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "step_id", updatable = false, nullable = false)
    private UUID stepId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "step_number", nullable = false, updatable = false)
    private int stepNumber;

    @Column(name = "thought", columnDefinition = "TEXT", updatable = false)
    private String thought;

    @Column(name = "action", columnDefinition = "TEXT", updatable = false)
    private String action;

    @Column(name = "observation", columnDefinition = "TEXT", updatable = false)
    private String observation;

    protected ReasoningStepEntity() {
        // JPA required
    }

    public ReasoningStepEntity(UUID eventId, int stepNumber, String thought, String action, String observation) {
        this.eventId = eventId;
        this.stepNumber = stepNumber;
        this.thought = thought;
        this.action = action;
        this.observation = observation;
    }

    // Getters only - immutable
    public UUID getStepId() { return stepId; }
    public UUID getEventId() { return eventId; }
    public int getStepNumber() { return stepNumber; }
    public String getThought() { return thought; }
    public String getAction() { return action; }
    public String getObservation() { return observation; }

    @Override
    public String toString() {
        return "ReasoningStepEntity{" +
                "stepId=" + stepId +
                ", stepNumber=" + stepNumber +
                ", action='" + action + '\'' +
                '}';
    }
}

