package com.bank.ata.mcp.server;

import com.bank.ata.mcp.annotation.McpParam;
import com.bank.ata.mcp.annotation.McpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Registry that discovers all {@link McpTool}-annotated methods and stores
 * their metadata (JSON Schema) alongside callable handlers.
 *
 * <p>Tool objects register themselves via {@link #register(Object)}, which
 * is called from {@link com.bank.ata.mcp.McpServerConfig}.</p>
 */
@Component
public class McpToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(McpToolRegistry.class);

    /** Schema + handler keyed by tool name. */
    private final Map<String, ToolEntry> tools = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    /**
     * Scan {@code toolObject} for public methods annotated with {@link McpTool}
     * and register each as a callable tool.
     */
    public void register(Object toolObject) {
        for (Method method : toolObject.getClass().getMethods()) {
            McpTool ann = method.getAnnotation(McpTool.class);
            if (ann == null) continue;

            Map<String, Object> schema = buildSchema(method);
            Function<Map<String, Object>, Object> handler = args -> {
                try {
                    Object[] params = mapArgs(method, args);
                    return method.invoke(toolObject, params);
                } catch (Exception e) {
                    throw new RuntimeException("Tool invocation failed: " + e.getMessage(), e);
                }
            };

            tools.put(ann.name(), new ToolEntry(ann.name(), ann.description(), schema, handler));
            log.info("Registered MCP tool: '{}' ({})", ann.name(), method.getDeclaringClass().getSimpleName());
        }
    }

    // -------------------------------------------------------------------------
    // Lookup
    // -------------------------------------------------------------------------

    /** Returns all registered tool definitions (for tools/list). */
    public List<Map<String, Object>> listTools() {
        return tools.values().stream()
                .map(e -> Map.of(
                        "name", e.name(),
                        "description", e.description(),
                        "inputSchema", e.schema()))
                .sorted(Comparator.comparing(m -> (String) m.get("name")))
                .toList();
    }

    /** Invoke the named tool with the given arguments map. Returns a String result. */
    public String call(String toolName, Map<String, Object> arguments) {
        ToolEntry entry = tools.get(toolName);
        if (entry == null) {
            throw new NoSuchElementException("Unknown tool: " + toolName);
        }
        Object result = entry.handler().apply(arguments == null ? Map.of() : arguments);
        return result == null ? "null" : result.toString();
    }

    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    public int size() {
        return tools.size();
    }

    // -------------------------------------------------------------------------
    // Schema generation
    // -------------------------------------------------------------------------

    private static Map<String, Object> buildSchema(Method method) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        Parameter[] params = method.getParameters();
        for (Parameter param : params) {
            McpParam ann = param.getAnnotation(McpParam.class);
            String description = ann != null ? ann.description() : param.getName();
            boolean isRequired  = ann == null || ann.required();

            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", jsonType(param.getType()));
            prop.put("description", description);
            properties.put(param.getName(), prop);

            if (isRequired) required.add(param.getName());
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) schema.put("required", required);
        return schema;
    }

    private static String jsonType(Class<?> type) {
        if (type == String.class) return "string";
        if (type == int.class || type == Integer.class
                || type == long.class || type == Long.class) return "integer";
        if (type == double.class || type == Double.class
                || type == float.class || type == Float.class) return "number";
        if (type == boolean.class || type == Boolean.class) return "boolean";
        return "string"; // default — complex types serialized as JSON string
    }

    // -------------------------------------------------------------------------
    // Argument mapping
    // -------------------------------------------------------------------------

    private static Object[] mapArgs(Method method, Map<String, Object> args) {
        Parameter[] params = method.getParameters();
        Object[] result = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Object raw = args.get(params[i].getName());
            result[i] = coerce(raw, params[i].getType());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Object coerce(Object value, Class<?> target) {
        if (value == null) return null;
        if (target.isInstance(value)) return value;
        String s = value.toString();
        if (target == int.class || target == Integer.class) return Integer.parseInt(s);
        if (target == long.class || target == Long.class) return Long.parseLong(s);
        if (target == double.class || target == Double.class) return Double.parseDouble(s);
        if (target == float.class || target == Float.class) return Float.parseFloat(s);
        if (target == boolean.class || target == Boolean.class) return Boolean.parseBoolean(s);
        return s; // fallback: toString
    }

    // -------------------------------------------------------------------------
    // Internal record
    // -------------------------------------------------------------------------

    private record ToolEntry(
            String name,
            String description,
            Map<String, Object> schema,
            Function<Map<String, Object>, Object> handler) {}
}

