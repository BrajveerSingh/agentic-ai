package com.bank.ata.controller;

import com.bank.ata.agent.AuditTrailAgent;
import com.bank.ata.audit.service.AuditContextHolder;
import com.bank.ata.audit.service.AuditService;
import com.bank.ata.controller.LoanApiDtos.*;
import com.bank.ata.core.domain.LoanApplication;
import com.bank.ata.core.domain.LoanDecision;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST API for loan evaluation.
 */
@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private static final Logger log = LoggerFactory.getLogger(LoanController.class);

    private final AuditTrailAgent agent;
    private final AuditService auditService;
    private final Counter evaluationCounter;
    private final Counter approvalCounter;
    private final Counter rejectionCounter;

    public LoanController(AuditTrailAgent agent,
                          AuditService auditService,
                          MeterRegistry meterRegistry) {
        this.agent = agent;
        this.auditService = auditService;
        this.evaluationCounter = meterRegistry.counter("ata.loan.evaluations.total");
        this.approvalCounter = meterRegistry.counter("ata.loan.evaluations.approved");
        this.rejectionCounter = meterRegistry.counter("ata.loan.evaluations.rejected");
    }

    /**
     * Evaluate a loan application.
     *
     * @param request The loan evaluation request
     * @return The loan decision with reasoning
     */
    @PostMapping("/evaluate")
    @Timed(value = "ata.loan.evaluation.duration", description = "Time taken to evaluate a loan")
    public ResponseEntity<LoanEvaluationResponse> evaluateLoan(
            @Valid @RequestBody LoanEvaluationRequest request) {

        log.info("Received loan evaluation request: customerId={}, amount={}, purpose={}",
                request.customerId(), request.amount(), request.purpose());

        evaluationCounter.increment();

        try {
            LoanApplication application = request.toApplication();
            UUID sessionId = UUID.randomUUID();

            // Bind the audit context for this evaluation scope.
            // AuditInterceptor (AOP) reads the context from ScopedValue — works
            // safely across virtual threads without any ThreadLocal leakage.
            LoanDecision decision = AuditContextHolder.callWithContext(
                    sessionId, application.applicationId(), () -> {
                        auditService.logSessionStart(sessionId, application.applicationId(), null);
                        LoanDecision d = agent.evaluateLoan(application);
                        auditService.logSessionEnd(sessionId, application.applicationId());
                        auditService.logDecision(
                                sessionId, application.applicationId(),
                                d.outcome().name(), d.reasoning(), d.confidenceScore());
                        return d;
                    });

            switch (decision.outcome()) {
                case APPROVED -> approvalCounter.increment();
                case REJECTED -> rejectionCounter.increment();
                default -> { /* PENDING_REVIEW / REQUIRES_ADDITIONAL_INFO — no counter */ }
            }

            LoanEvaluationResponse response = LoanEvaluationResponse.from(application, decision);

            log.info("Loan evaluation complete: applicationId={}, outcome={}",
                    application.applicationId(), decision.outcome());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error evaluating loan: {}", e.getMessage(), e);
            throw new LoanEvaluationException("Failed to evaluate loan: " + e.getMessage(), e);
        }
    }

    /**
     * Quick evaluation endpoint (for testing without full agent processing).
     */
    @PostMapping("/evaluate/quick")
    public ResponseEntity<LoanEvaluationResponse> quickEvaluate(
            @Valid @RequestBody LoanEvaluationRequest request) {

        log.info("Quick evaluation request: customerId={}, amount={}",
                request.customerId(), request.amount());

        // Create application
        LoanApplication application = request.toApplication();

        // Simple rule-based decision (no LLM)
        var outcome = request.amount().compareTo(new java.math.BigDecimal("100000")) <= 0
                ? com.bank.ata.core.domain.DecisionOutcome.APPROVED
                : com.bank.ata.core.domain.DecisionOutcome.PENDING_REVIEW;

        LoanDecision decision = LoanDecision.create(
                application.applicationId(),
                outcome,
                "Quick evaluation based on amount threshold. For full AI evaluation, use /api/loans/evaluate",
                0.5
        );

        return ResponseEntity.ok(LoanEvaluationResponse.from(application, decision));
    }

    /**
     * Exception handler for loan evaluation errors.
     */
    @ExceptionHandler(LoanEvaluationException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleLoanEvaluationException(LoanEvaluationException e) {
        log.error("Loan evaluation error: {}", e.getMessage());
        return ErrorResponse.of("EVALUATION_ERROR", e.getMessage());
    }

    /**
     * Exception handler for validation errors.
     */
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidationException(
            org.springframework.web.bind.MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation error");
        return ErrorResponse.of("VALIDATION_ERROR", message);
    }

    /**
     * Custom exception for loan evaluation errors.
     */
    public static class LoanEvaluationException extends RuntimeException {
        public LoanEvaluationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

