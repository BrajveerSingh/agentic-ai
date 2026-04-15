package com.bank.ata.a2a.model;

import java.util.List;

/**
 * A single message exchanged in an A2A task conversation.
 * Role is either "user" (task sender) or "agent" (task responder).
 */
public record A2aMessage(
        String role,
        List<A2aPart> parts
) {
    public static A2aMessage user(String text) {
        return new A2aMessage("user", List.of(A2aPart.text(text)));
    }

    public static A2aMessage agent(String text) {
        return new A2aMessage("agent", List.of(A2aPart.text(text)));
    }

    /** Convenience: return the concatenated text of all text parts. */
    public String textContent() {
        if (parts == null) return "";
        return parts.stream()
                .filter(p -> "text".equals(p.type()))
                .map(A2aPart::text)
                .reduce("", (a, b) -> a + b);
    }
}

