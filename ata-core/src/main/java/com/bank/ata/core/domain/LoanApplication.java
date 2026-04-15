package com.bank.ata.core.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Represents a loan application submitted for evaluation.
 */
public record LoanApplication(
        UUID applicationId,

        @NotBlank(message = "Customer ID is required")
        String customerId,

        @NotNull(message = "Loan amount is required")
        @DecimalMin(value = "1000", message = "Minimum loan amount is 1000")
        BigDecimal amount,

        @NotBlank(message = "Purpose is required")
        String purpose,

        LoanType loanType,

        LocalDate applicationDate,

        ApplicationStatus status
) {
    /**
     * Create a new loan application with generated ID and current date.
     */
    public static LoanApplication create(String customerId, BigDecimal amount,
                                          String purpose, LoanType loanType) {
        return new LoanApplication(
                UUID.randomUUID(),
                customerId,
                amount,
                purpose,
                loanType,
                LocalDate.now(),
                ApplicationStatus.PENDING
        );
    }

    /**
     * Create a copy with updated status.
     */
    public LoanApplication withStatus(ApplicationStatus newStatus) {
        return new LoanApplication(
                applicationId, customerId, amount, purpose,
                loanType, applicationDate, newStatus
        );
    }
}

