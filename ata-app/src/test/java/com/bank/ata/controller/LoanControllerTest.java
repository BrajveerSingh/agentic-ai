package com.bank.ata.controller;

import com.bank.ata.core.domain.DecisionOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for LoanApiDtos.
 */
class LoanControllerTest {

    @Test
    @DisplayName("LoanEvaluationRequest should create valid LoanApplication")
    void requestShouldCreateValidApplication() {
        // Given
        var request = new LoanApiDtos.LoanEvaluationRequest(
                "CUST001",
                new BigDecimal("50000"),
                "HOME_IMPROVEMENT",
                com.bank.ata.core.domain.LoanType.HOME_IMPROVEMENT
        );

        // When
        var application = request.toApplication();

        // Then
        assertThat(application).isNotNull();
        assertThat(application.customerId()).isEqualTo("CUST001");
        assertThat(application.amount()).isEqualByComparingTo(new BigDecimal("50000"));
        assertThat(application.purpose()).isEqualTo("HOME_IMPROVEMENT");
        assertThat(application.applicationId()).isNotNull();
    }

    @Test
    @DisplayName("LoanEvaluationResponse should create from application and decision")
    void responseShouldCreateFromDomain() {
        // Given
        var request = new LoanApiDtos.LoanEvaluationRequest(
                "CUST001",
                new BigDecimal("50000"),
                "HOME_IMPROVEMENT",
                com.bank.ata.core.domain.LoanType.HOME_IMPROVEMENT
        );
        var application = request.toApplication();
        var decision = com.bank.ata.core.domain.LoanDecision.create(
                application.applicationId(),
                DecisionOutcome.APPROVED,
                "Test reasoning",
                0.95
        );

        // When
        var response = LoanApiDtos.LoanEvaluationResponse.from(application, decision);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.customerId()).isEqualTo("CUST001");
        assertThat(response.outcome()).isEqualTo("APPROVED");
        assertThat(response.confidenceScore()).isEqualTo(0.95);
    }
}
