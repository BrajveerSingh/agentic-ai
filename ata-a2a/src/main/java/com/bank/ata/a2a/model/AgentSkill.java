package com.bank.ata.a2a.model;

import java.util.List;

/**
 * A single capability/skill advertised in the AgentCard.
 */
public record AgentSkill(
        String id,
        String name,
        String description,
        List<String> tags,
        List<String> inputModes,
        List<String> outputModes
) {
    public static AgentSkill of(String id, String name, String description, String... tags) {
        return new AgentSkill(id, name, description, List.of(tags),
                List.of("text"), List.of("text", "data"));
    }
}

