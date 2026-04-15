package com.bank.ata.a2a.model;

import java.util.List;

/**
 * Output artifact produced by an agent for a completed task.
 */
public record A2aArtifact(
        String name,
        String description,
        List<A2aPart> parts
) {
    public static A2aArtifact of(String name, String description, String textContent) {
        return new A2aArtifact(name, description, List.of(A2aPart.text(textContent)));
    }

    /** Return the concatenated text from all text parts. */
    public String textContent() {
        if (parts == null) return "";
        return parts.stream()
                .filter(p -> "text".equals(p.type()))
                .map(A2aPart::text)
                .reduce("", (a, b) -> a + b);
    }
}

