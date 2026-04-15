package com.bank.ata.mcp.annotation;

import java.lang.annotation.*;

/**
 * Marks a method as an MCP tool, making it discoverable via {@code tools/list}
 * and callable via {@code tools/call} by any MCP-compatible LLM client.
 *
 * <p>Methods annotated with {@code @McpTool} must be public and their parameters
 * must be annotated with {@link McpParam} to provide descriptions for the JSON Schema.</p>
 *
 * <p>Return type should be {@code String} (JSON) for best compatibility.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpTool {
    /** Tool name as presented to MCP clients (snake_case recommended). */
    String name();
    /** Human-readable description surfaced in {@code tools/list}. */
    String description();
}

