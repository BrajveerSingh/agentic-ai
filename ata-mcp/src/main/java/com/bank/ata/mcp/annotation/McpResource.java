package com.bank.ata.mcp.annotation;

import java.lang.annotation.*;

/**
 * Marks a method as an MCP resource provider, making the resource discoverable
 * via {@code resources/list} and readable via {@code resources/read} by MCP clients.
 *
 * <p>The annotated method must be public and accept exactly one {@code String} parameter
 * which receives the full resource URI at invocation time.  The return type should be
 * {@code String} (JSON or plain-text content).</p>
 *
 * <p>If {@link #uri()} contains a {@code {variable}} placeholder the resource is
 * registered as a <em>URI template</em>; otherwise it is registered as a
 * <em>static resource</em>.</p>
 *
 * <p>Example:</p>
 * <pre>
 * {@literal @}McpResource(
 *     uri         = "loan://policies/{policyId}",
 *     name        = "Loan Policy Document",
 *     description = "Bank loan policy by ID",
 *     mimeType    = "application/json")
 * public String getLoanPolicy(String uri) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpResource {

    /** Resource URI (static) or URI template (contains {@code {variable}}). */
    String uri();

    /** Human-readable resource name surfaced in {@code resources/list}. */
    String name();

    /** Optional description surfaced in {@code resources/list}. */
    String description() default "";

    /** MIME type of the content returned by this resource. */
    String mimeType() default "application/json";
}

