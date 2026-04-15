package com.bank.ata.a2a.model;

/**
 * Smallest content unit in the A2A protocol.
 * A "text" part carries plain-text or JSON-serialised content.
 */
public record A2aPart(
        String type,   // always "text" for now
        String text
) {
    /** Convenience factory for a plain-text part. */
    public static A2aPart text(String text) {
        return new A2aPart("text", text);
    }
}

