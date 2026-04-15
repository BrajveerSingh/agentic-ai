package com.bank.ata.controller;

import com.bank.ata.core.domain.LoanApplication;
import com.bank.ata.core.domain.LoanDecision;
import com.bank.ata.core.domain.LoanType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request/Response DTOs for loan evaluation API.
 */
public class LoanApiDtos {

    /**
     * Request to evaluate a loan application.
     */
    public record LoanEvaluationRequest(
            @NotBlank(message = "Customer ID is required")
            String customerId,

            @NotNull(message = "Amount is required")
            @DecimalMin(value = "1000", message = "Minimum loan amount is $1,000")
            BigDecimal amount,

            @NotBlank(message = "Purpose is required")
            String purpose,

            LoanType loanType
    ) {
        public LoanApplication toApplication() {
            return LoanApplication.create(
                    customerId,
                    amount,
                    purpose,
                    loanType != null ? loanType : LoanType.PERSONAL
            );
        }
    }

    /**
     * Response containing the loan decision.
     */
    public record LoanEvaluationResponse(
            String applicationId,
            String customerId,
            BigDecimal amount,
            String purpose,
            String outcome,
            String reasoning,
            double confidenceScore,
            String timestamp
    ) {
        public static LoanEvaluationResponse from(LoanApplication application, LoanDecision decision) {
            return new LoanEvaluationResponse(
                    application.applicationId().toString(),
                    application.customerId(),
                    application.amount(),
                    application.purpose(),
                    decision.outcome().name(),
                    decision.reasoning(),
                    decision.confidenceScore(),
                    decision.timestamp().toString()
            );
        }
    }

    /**
     * Error response.
     */
    public record ErrorResponse(
            String error,
            String message,
            String timestamp
    ) {
        public static ErrorResponse of(String error, String message) {
            return new ErrorResponse(
                    error,
                    message,
                    java.time.LocalDateTime.now().toString()
            );
        }
    }
}

