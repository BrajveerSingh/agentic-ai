package com.bank.ata.a2a.model;

import java.util.List;

/**
 * Agent Card — well-known descriptor that a remote agent fetches from
 * {@code GET /a2a/.well-known/agent.json} before sending its first task.
 */
public record AgentCard(
        String name,
        String description,
        String url,
        String version,
        AgentCapabilities capabilities,
        List<AgentSkill> skills
) {
    public static AgentCard of(String name, String description, String url,
                                String version, List<AgentSkill> skills) {
        return new AgentCard(name, description, url, version,
                AgentCapabilities.defaults(), skills);
    }
}

