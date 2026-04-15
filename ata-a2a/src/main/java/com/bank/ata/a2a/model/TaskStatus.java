package com.bank.ata.a2a.model;

/**
 * Current status of an A2A task, including a human-readable detail message.
 */
public record TaskStatus(
        TaskState state,
        String message
) {
    public static TaskStatus submitted() { return new TaskStatus(TaskState.SUBMITTED, "Task submitted"); }
    public static TaskStatus working()   { return new TaskStatus(TaskState.WORKING,   "Processing");     }
    public static TaskStatus completed() { return new TaskStatus(TaskState.COMPLETED, "Task completed"); }
    public static TaskStatus failed(String reason) { return new TaskStatus(TaskState.FAILED, reason);   }
}

