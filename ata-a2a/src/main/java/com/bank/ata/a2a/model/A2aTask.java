package com.bank.ata.a2a.model;

import java.util.List;
import java.util.UUID;

/**
 * The core A2A protocol object — both the request (carrying {@code message})
 * and the response (carrying {@code status} + {@code artifacts}).
 */
public record A2aTask(
        String id,
        String sessionId,
        A2aMessage message,
        TaskStatus status,
        List<A2aArtifact> artifacts,
        String skillId          // optional hint: which skill to invoke
) {
    /**
     * Create a new task from an inbound request message.
     * Status is set to SUBMITTED; artifacts are empty.
     */
    public static A2aTask of(A2aMessage message, String skillId) {
        return new A2aTask(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                message,
                TaskStatus.submitted(),
                List.of(),
                skillId
        );
    }

    /** Return a copy with an updated status. */
    public A2aTask withStatus(TaskStatus newStatus) {
        return new A2aTask(id, sessionId, message, newStatus, artifacts, skillId);
    }

    /** Return a completed copy with the given artifacts. */
    public A2aTask completed(List<A2aArtifact> newArtifacts) {
        return new A2aTask(id, sessionId, message, TaskStatus.completed(), newArtifacts, skillId);
    }

    /** Return a failed copy with an error reason. */
    public A2aTask failed(String reason) {
        return new A2aTask(id, sessionId, message, TaskStatus.failed(reason), List.of(), skillId);
    }
}

