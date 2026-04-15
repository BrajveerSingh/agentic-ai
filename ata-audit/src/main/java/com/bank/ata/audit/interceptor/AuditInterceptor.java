package com.bank.ata.audit.interceptor;

import com.bank.ata.audit.entity.ToolCallEntity;
import com.bank.ata.audit.service.AuditContextHolder;
import com.bank.ata.audit.service.AuditService;
import dev.langchain4j.agent.tool.Tool;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * AOP interceptor that captures all @Tool method invocations for audit logging.
 *
 * Key Features:
 * - Logs tool inputs BEFORE execution (immutable audit)
 * - Logs tool outputs and execution time AFTER execution
 * - Captures exceptions and logs them as failures
 * - Only intercepts when an audit context is set
 */
@Aspect
@Component
public class AuditInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuditInterceptor.class);

    private final AuditService auditService;

    public AuditInterceptor(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Intercept all methods annotated with @Tool from LangChain4j.
     */
    @Around("@annotation(tool)")
    public Object interceptToolCall(ProceedingJoinPoint joinPoint, Tool tool) throws Throwable {
        UUID sessionId = AuditContextHolder.getSessionId();
        UUID applicationId = AuditContextHolder.getApplicationId();

        // Skip audit if no context is set (e.g., direct tool testing without agent)
        if (sessionId == null) {
            log.trace("No audit context, skipping audit for tool: {}",
                    joinPoint.getSignature().getName());
            return joinPoint.proceed();
        }

        String toolName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        // Log BEFORE execution (ensures input is captured even if execution fails)
        ToolCallEntity toolCall = auditService.logToolCallStart(
                sessionId, applicationId, toolName, args);

        long startTime = System.currentTimeMillis();
        try {
            // Execute the actual tool method
            Object result = joinPoint.proceed();
            long executionTime = System.currentTimeMillis() - startTime;

            // Log successful completion
            auditService.logToolCallComplete(toolCall.getToolCallId(), result, executionTime);

            log.debug("Tool '{}' executed successfully in {}ms", toolName, executionTime);
            return result;

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;

            // Log failure
            auditService.logToolCallError(toolCall.getToolCallId(), e, executionTime);

            log.warn("Tool '{}' failed after {}ms: {}", toolName, executionTime, e.getMessage());
            throw e;
        }
    }
}

