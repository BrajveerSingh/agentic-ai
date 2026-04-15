package com.bank.ata.core.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class LoanApplicationTest {

    @Test
    void shouldCreateLoanApplicationWithGeneratedId() {
        // When
        LoanApplication app = LoanApplication.create(
                "CUST001",
                new BigDecimal("50000"),
                "Home Improvement",
                LoanType.HOME_IMPROVEMENT
        );

        // Then
        assertThat(app.applicationId()).isNotNull();
        assertThat(app.customerId()).isEqualTo("CUST001");
        assertThat(app.amount()).isEqualTo(new BigDecimal("50000"));
        assertThat(app.purpose()).isEqualTo("Home Improvement");
        assertThat(app.loanType()).isEqualTo(LoanType.HOME_IMPROVEMENT);
        assertThat(app.status()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(app.applicationDate()).isNotNull();
    }

    @Test
    void shouldUpdateStatus() {
        // Given
        LoanApplication app = LoanApplication.create(
                "CUST001",
                new BigDecimal("50000"),
                "Home Improvement",
                LoanType.HOME_IMPROVEMENT
        );

        // When
        LoanApplication approved = app.withStatus(ApplicationStatus.APPROVED);

        // Then
        assertThat(approved.status()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(approved.applicationId()).isEqualTo(app.applicationId());
    }
}

