package com.bank.ata.core.domain;

/**
 * Status of a loan application.
 */
public enum ApplicationStatus {
    PENDING,
    IN_REVIEW,
    APPROVED,
    REJECTED,
    PENDING_ADDITIONAL_INFO,
    CANCELLED
}

