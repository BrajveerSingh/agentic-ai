package com.bank.ata.mcp.annotation;

import java.lang.annotation.*;

/**
 * Documents a parameter of an {@link McpTool} method, providing the description
 * that becomes part of the JSON Schema surfaced to MCP clients.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpParam {
    /** Parameter description used in JSON Schema. */
    String description();
    /** Whether this parameter is required (default true). */
    boolean required() default true;
}

