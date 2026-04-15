package com.bank.ata.audit.service;

import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Scoped-value context holder for audit session information.
 *
 * <p>Replaces the former {@code ThreadLocal} implementation with Java 25
 * {@link ScopedValue}, which is virtual-thread-safe and fully compatible
 * with Project Loom's structured concurrency (Spring Boot 4 enables virtual
 * threads by default).</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 *   // Controller / entry-point:
 *   LoanDecision decision = AuditContextHolder.callWithContext(
 *       sessionId, applicationId,
 *       () -> agent.evaluateLoan(application));   // AuditInterceptor reads context here
 * }</pre>
 *
 * <p>The context is automatically inherited by any virtual thread spawned inside
 * the scope (e.g. parallel tool calls), unlike {@code ThreadLocal} which requires
 * manual propagation.</p>
 */
public final class AuditContextHolder {

    /** Single immutable ScopedValue key — created once at class load time. */
    private static final ScopedValue<AuditContext> CONTEXT = ScopedValue.newInstance();

    private AuditContextHolder() { /* utility class */ }

    // -----------------------------------------------------------------------
    // Write API — execute a task inside a bound context
    // -----------------------------------------------------------------------

    /**
     * Run {@code task} with the given audit context bound for its duration.
     * Any virtual thread spawned inside {@code task} inherits the context.
     *
     * @throws RuntimeException wrapping any checked exception thrown by {@code task}
     */
    public static void runWithContext(UUID sessionId, UUID applicationId, Runnable task) {
        ScopedValue.where(CONTEXT, new AuditContext(sessionId, applicationId)).run(task);
    }

    /**
     * Call {@code task} with the given audit context and return its result.
     *
     * @throws RuntimeException wrapping any checked exception thrown by {@code task}
     */
    public static <T> T callWithContext(UUID sessionId, UUID applicationId, Callable<T> task) {
        try {
            // task::call adapts java.util.concurrent.Callable to ScopedValue.CallableOp
            return ScopedValue.where(CONTEXT, new AuditContext(sessionId, applicationId))
                              .call(task::call);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Audit context call failed", e);
        }
    }

    // -----------------------------------------------------------------------
    // Read API — called from AuditInterceptor / AuditService
    // -----------------------------------------------------------------------

    /**
     * Returns the current {@link AuditContext}, or {@code null} if none is bound.
     * NOTE: {@code ScopedValue.orElse(null)} throws NPE in Java 25 — always guard with {@code isBound()}.
     */
    public static AuditContext getContext() {
        return CONTEXT.isBound() ? CONTEXT.get() : null;
    }

    /** Returns the current session ID, or {@code null} if none is bound. */
    public static UUID getSessionId() {
        return CONTEXT.isBound() ? CONTEXT.get().sessionId() : null;
    }

    /** Returns the current application ID, or {@code null} if none is bound. */
    public static UUID getApplicationId() {
        return CONTEXT.isBound() ? CONTEXT.get().applicationId() : null;
    }

    /** Returns {@code true} if an audit context is bound on the current scope. */
    public static boolean hasContext() {
        return CONTEXT.isBound();
    }

    // -----------------------------------------------------------------------
    // Domain record
    // -----------------------------------------------------------------------

    /** Immutable audit context record containing session and application IDs. */
    public record AuditContext(UUID sessionId, UUID applicationId) {}
}

