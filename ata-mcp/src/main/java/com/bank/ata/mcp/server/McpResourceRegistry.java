package com.bank.ata.mcp.server;

import com.bank.ata.mcp.annotation.McpResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Registry that discovers all {@link McpResource}-annotated methods and stores
 * their metadata alongside callable handlers.
 *
 * <p>Resource objects register themselves via {@link #register(Object)}, which is called
 * from {@link com.bank.ata.mcp.McpServerConfig}.  At registration time each method is
 * inspected:
 * <ul>
 *   <li>If the URI contains a {@code {variable}} placeholder it is stored as a
 *       <em>URI template</em> (returned via {@code resourceTemplates} in the
 *       {@code resources/list} response).</li>
 *   <li>Otherwise it is stored as a <em>static resource</em> (returned via
 *       {@code resources} in the same response).</li>
 * </ul>
 * </p>
 *
 * <p>Every handler method must accept exactly one {@code String} parameter that receives
 * the full URI passed by the client in the {@code resources/read} request.</p>
 */
@Component
public class McpResourceRegistry {

    private static final Logger log = LoggerFactory.getLogger(McpResourceRegistry.class);

    /** Static resources keyed by exact URI. */
    private final Map<String, ResourceEntry> staticResources = new ConcurrentHashMap<>();

    /** URI-template resources (ordered; first match wins). */
    private final List<TemplateEntry> templates = Collections.synchronizedList(new ArrayList<>());

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    /**
     * Scan {@code resourceObject} for public methods annotated with {@link McpResource}
     * and register each as a resource or resource template.
     */
    public void register(Object resourceObject) {
        for (Method method : resourceObject.getClass().getMethods()) {
            McpResource ann = method.getAnnotation(McpResource.class);
            if (ann == null) continue;

            Function<String, Object> handler = uri -> {
                try {
                    return method.invoke(resourceObject, uri);
                } catch (Exception e) {
                    throw new RuntimeException("Resource handler failed: " + e.getMessage(), e);
                }
            };

            if (ann.uri().contains("{")) {
                // Build a regex from the URI template  e.g. "loan://policies/{policyId}"
                // → "^loan://policies/[^/]+$"
                String regex = "^" + ann.uri().replaceAll("\\{[^/}]+}", "[^/]+") + "$";
                TemplateEntry entry = new TemplateEntry(
                        ann.uri(), ann.name(), ann.description(), ann.mimeType(),
                        Pattern.compile(regex), handler);
                templates.add(entry);
                log.info("Registered MCP resource template: '{}'", ann.uri());
            } else {
                ResourceEntry entry = new ResourceEntry(
                        ann.uri(), ann.name(), ann.description(), ann.mimeType(), handler);
                staticResources.put(ann.uri(), entry);
                log.info("Registered MCP static resource: '{}'", ann.uri());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Lookup
    // -------------------------------------------------------------------------

    /** Returns static resource descriptors for the {@code resources/list} response. */
    public List<Map<String, Object>> listResources() {
        return staticResources.values().stream()
                .map(e -> Map.<String, Object>of(
                        "uri",         e.uri(),
                        "name",        e.name(),
                        "description", e.description(),
                        "mimeType",    e.mimeType()))
                .toList();
    }

    /** Returns URI-template descriptors for the {@code resources/list} response. */
    public List<Map<String, Object>> listTemplates() {
        return templates.stream()
                .map(e -> Map.<String, Object>of(
                        "uriTemplate", e.uriTemplate(),
                        "name",        e.name(),
                        "description", e.description(),
                        "mimeType",    e.mimeType()))
                .toList();
    }

    /**
     * Read a resource by its full URI.
     *
     * @param uri full resource URI from the client
     * @return {@link ResourceResult} containing content, or {@code null} if not found
     */
    public ResourceResult read(String uri) {
        // 1. Exact (static) match
        ResourceEntry se = staticResources.get(uri);
        if (se != null) {
            Object content = se.handler().apply(uri);
            return new ResourceResult(uri, se.mimeType(), content == null ? "null" : content.toString());
        }

        // 2. Template match (first wins)
        for (TemplateEntry te : templates) {
            if (te.pattern().matcher(uri).matches()) {
                Object content = te.handler().apply(uri);
                return new ResourceResult(uri, te.mimeType(), content == null ? "null" : content.toString());
            }
        }

        return null; // not found
    }

    public boolean hasResource(String uri) {
        if (staticResources.containsKey(uri)) return true;
        return templates.stream().anyMatch(t -> t.pattern().matcher(uri).matches());
    }

    public int size() {
        return staticResources.size() + templates.size();
    }

    // -------------------------------------------------------------------------
    // Internal types
    // -------------------------------------------------------------------------

    private record ResourceEntry(
            String uri, String name, String description, String mimeType,
            Function<String, Object> handler) {}

    private record TemplateEntry(
            String uriTemplate, String name, String description, String mimeType,
            Pattern pattern, Function<String, Object> handler) {}

    /** Result of a successful {@code resources/read} call. */
    public record ResourceResult(String uri, String mimeType, String content) {}
}

