package com.bank.ata.a2a.model;

/** Capability flags advertised in an AgentCard. */
public record AgentCapabilities(
        boolean streaming,
        boolean pushNotifications
) {
    /** ATA v1: synchronous only, no push. */
    public static AgentCapabilities defaults() {
        return new AgentCapabilities(false, false);
    }
}

