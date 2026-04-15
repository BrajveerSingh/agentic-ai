package com.bank.ata.agent.tools;

import com.bank.ata.core.domain.CreditScore;
import com.bank.ata.core.domain.KycResult;
import com.bank.ata.core.domain.PolicyResult;
import com.bank.ata.core.domain.RiskScore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for LoanEvaluationTools.
 */
class LoanEvaluationToolsTest {

    private LoanEvaluationTools tools;

    @BeforeEach
    void setUp() {
        tools = new LoanEvaluationTools();
    }

    // ========================================
    // Credit Score Tests
    // ========================================

    @Test
    @DisplayName("getCreditScore should return valid score for customer")
    void getCreditScore_shouldReturnValidScore() {
        // When
        CreditScore score = tools.getCreditScore("CUST001");

        // Then
        assertThat(score).isNotNull();
        assertThat(score.customerId()).isEqualTo("CUST001");
        assertThat(score.score()).isBetween(550, 800);
        assertThat(score.rating()).isIn("EXCELLENT", "GOOD", "FAIR", "POOR", "VERY_POOR");
    }

    @Test
    @DisplayName("getCreditScore should be deterministic for same customer")
    void getCreditScore_shouldBeDeterministic() {
        // When
        CreditScore score1 = tools.getCreditScore("CUST001");
        CreditScore score2 = tools.getCreditScore("CUST001");

        // Then
        assertThat(score1.score()).isEqualTo(score2.score());
        assertThat(score1.rating()).isEqualTo(score2.rating());
    }

    @Test
    @DisplayName("getCreditScore should return different scores for different customers")
    void getCreditScore_shouldVaryByCustomer() {
        // When
        CreditScore score1 = tools.getCreditScore("CUST001");
        CreditScore score2 = tools.getCreditScore("CUST002");
        CreditScore score3 = tools.getCreditScore("CUST003");

        // Then - at least some should differ
        boolean allSame = score1.score() == score2.score() && score2.score() == score3.score();
        // This test might occasionally fail due to hash collisions, but very unlikely
        assertThat(allSame).isFalse();
    }

    // ========================================
    // Policy Compliance Tests
    // ========================================

    @Test
    @DisplayName("checkPolicyCompliance should pass for good application")
    void checkPolicyCompliance_shouldPassForGoodApplication() {
        // When
        PolicyResult result = tools.checkPolicyCompliance(
                new BigDecimal("50000"),
                "HOME_IMPROVEMENT",
                720
        );

        // Then
        assertThat(result.compliant()).isTrue();
        assertThat(result.ruleResults()).isNotEmpty();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    @DisplayName("checkPolicyCompliance should fail for low credit score")
    void checkPolicyCompliance_shouldFailForLowCreditScore() {
        // When
        PolicyResult result = tools.checkPolicyCompliance(
                new BigDecimal("50000"),
                "HOME_IMPROVEMENT",
                500  // Below minimum 650
        );

        // Then
        assertThat(result.compliant()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.toLowerCase().contains("credit"));
    }

    @Test
    @DisplayName("checkPolicyCompliance should fail for excessive loan amount")
    void checkPolicyCompliance_shouldFailForExcessiveAmount() {
        // When
        PolicyResult result = tools.checkPolicyCompliance(
                new BigDecimal("600000"),  // Exceeds $500,000 max
                "HOME_IMPROVEMENT",
                720
        );

        // Then
        assertThat(result.compliant()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("exceeds maximum"));
    }

    @Test
    @DisplayName("checkPolicyCompliance should fail for invalid purpose")
    void checkPolicyCompliance_shouldFailForInvalidPurpose() {
        // When
        PolicyResult result = tools.checkPolicyCompliance(
                new BigDecimal("50000"),
                "GAMBLING",  // Not approved
                720
        );

        // Then
        assertThat(result.compliant()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("not approved"));
    }

    // ========================================
    // KYC Verification Tests
    // ========================================

    @Test
    @DisplayName("verifyKYC should return verified status for normal customer")
    void verifyKYC_shouldReturnVerifiedForNormalCustomer() {
        // When
        KycResult result = tools.verifyKYC("CUST001");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.verified()).isTrue();
        assertThat(result.verificationDate()).isNotNull();
        assertThat(result.verificationLevel()).isEqualTo("FULL");
    }

    @Test
    @DisplayName("verifyKYC should return unverified for unverified customer")
    void verifyKYC_shouldReturnUnverifiedForUnverifiedCustomer() {
        // When
        KycResult result = tools.verifyKYC("UNVERIFIED_CUST");

        // Then
        assertThat(result.verified()).isFalse();
    }

    @Test
    @DisplayName("verifyKYC should check if current")
    void verifyKYC_shouldCheckIfCurrent() {
        // When
        KycResult result = tools.verifyKYC("CUST001");

        // Then
        assertThat(result.isCurrent()).isTrue();  // Within last 12 months
    }

    // ========================================
    // Risk Score Tests
    // ========================================

    @Test
    @DisplayName("calculateRiskScore should return low risk for good profile")
    void calculateRiskScore_shouldReturnLowRiskForGoodProfile() {
        // When
        RiskScore score = tools.calculateRiskScore(
                780,  // Excellent credit
                new BigDecimal("30000"),  // Moderate amount
                10   // Long employment
        );

        // Then
        assertThat(score.score()).isLessThan(0.3);
        assertThat(score.level()).isEqualTo("LOW");
        assertThat(score.isAcceptable()).isTrue();
    }

    @Test
    @DisplayName("calculateRiskScore should return high risk for poor profile")
    void calculateRiskScore_shouldReturnHighRiskForPoorProfile() {
        // When
        RiskScore score = tools.calculateRiskScore(
                580,  // Poor credit
                new BigDecimal("400000"),  // High amount
                0    // No employment history
        );

        // Then
        assertThat(score.score()).isGreaterThan(0.5);
        assertThat(score.level()).isIn("HIGH", "VERY_HIGH");
        assertThat(score.isAcceptable()).isFalse();
    }

    @Test
    @DisplayName("calculateRiskScore should return medium risk for average profile")
    void calculateRiskScore_shouldReturnMediumRiskForAverageProfile() {
        // When
        RiskScore score = tools.calculateRiskScore(
                680,  // Fair credit
                new BigDecimal("150000"),  // Medium-high amount
                3    // Some employment
        );

        // Then
        assertThat(score.score()).isBetween(0.2, 0.6);
        assertThat(score.explanation()).contains("Risk score");
    }

    @Test
    @DisplayName("calculateRiskScore should be bounded between 0 and 1")
    void calculateRiskScore_shouldBeBounded() {
        // Test extreme low risk
        RiskScore lowRisk = tools.calculateRiskScore(850, new BigDecimal("1000"), 20);
        assertThat(lowRisk.score()).isBetween(0.0, 1.0);

        // Test extreme high risk
        RiskScore highRisk = tools.calculateRiskScore(400, new BigDecimal("500000"), 0);
        assertThat(highRisk.score()).isBetween(0.0, 1.0);
    }

    // ========================================
    // Employment Info Tests
    // ========================================

    @Test
    @DisplayName("getEmploymentInfo should return valid employment data")
    void getEmploymentInfo_shouldReturnValidData() {
        // When
        var info = tools.getEmploymentInfo("CUST001");

        // Then
        assertThat(info).isNotNull();
        assertThat(info.customerId()).isEqualTo("CUST001");
        assertThat(info.yearsEmployed()).isGreaterThanOrEqualTo(0);
        assertThat(info.employer()).isNotBlank();
        assertThat(info.status()).isIn("STABLE", "NEW");
    }
}

