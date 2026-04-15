package com.bank.ata.agent.tools;

import com.bank.ata.core.domain.*;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loan evaluation tools that the AI agent can invoke.
 * Each tool is deterministic and provides transparent, auditable results.
 */
@Component
public class LoanEvaluationTools {

    private static final Logger log = LoggerFactory.getLogger(LoanEvaluationTools.class);

    // Mock data stores (will be replaced with real services in production)
    private final Map<String, CreditScore> creditScoreCache = new ConcurrentHashMap<>();
    private final Map<String, KycResult> kycCache = new ConcurrentHashMap<>();

    // Policy thresholds
    private static final int MIN_CREDIT_SCORE = 650;
    private static final BigDecimal MAX_LOAN_AMOUNT = new BigDecimal("500000");
    private static final int MIN_EMPLOYMENT_YEARS = 1;

    /**
     * Get credit score for a customer.
     * In production, this would call the credit bureau API.
     */
    @Tool("Get the credit score for a customer. Returns credit score, rating, and risk level.")
    public CreditScore getCreditScore(
            @P("The unique customer identifier") String customerId) {

        log.info("Tool invoked: getCreditScore(customerId={})", customerId);

        // Mock implementation - generate deterministic score based on customer ID
        CreditScore score = creditScoreCache.computeIfAbsent(customerId, id -> {
            int mockScore = generateMockCreditScore(id);
            String rating = determineRating(mockScore);
            return new CreditScore(id, mockScore, rating);
        });

        log.info("Credit score retrieved: customerId={}, score={}, rating={}",
                customerId, score.score(), score.rating());

        return score;
    }

    /**
     * Check if a loan application complies with bank policies.
     */
    @Tool("Check if a loan application complies with all bank policies. Returns compliance status and rule results.")
    public PolicyResult checkPolicyCompliance(
            @P("The loan amount requested") BigDecimal amount,
            @P("The purpose of the loan") String purpose,
            @P("The customer's credit score") int creditScore) {

        log.info("Tool invoked: checkPolicyCompliance(amount={}, purpose={}, creditScore={})",
                amount, purpose, creditScore);

        List<String> ruleResults = new ArrayList<>();
        List<String> violations = new ArrayList<>();
        boolean compliant = true;

        // Rule 1: Credit Score Check
        if (creditScore >= MIN_CREDIT_SCORE) {
            ruleResults.add("PASS: Credit score " + creditScore + " meets minimum requirement of " + MIN_CREDIT_SCORE);
        } else {
            violations.add("FAIL: Credit score " + creditScore + " below minimum " + MIN_CREDIT_SCORE);
            compliant = false;
        }

        // Rule 2: Maximum Loan Amount
        if (amount.compareTo(MAX_LOAN_AMOUNT) <= 0) {
            ruleResults.add("PASS: Loan amount $" + amount + " within maximum limit of $" + MAX_LOAN_AMOUNT);
        } else {
            violations.add("FAIL: Loan amount $" + amount + " exceeds maximum $" + MAX_LOAN_AMOUNT);
            compliant = false;
        }

        // Rule 3: Purpose Validation
        List<String> validPurposes = List.of(
                "HOME_IMPROVEMENT", "DEBT_CONSOLIDATION", "BUSINESS",
                "EDUCATION", "AUTO", "PERSONAL", "MORTGAGE"
        );
        String normalizedPurpose = purpose.toUpperCase().replace(" ", "_");
        if (validPurposes.stream().anyMatch(normalizedPurpose::contains)) {
            ruleResults.add("PASS: Loan purpose '" + purpose + "' is valid");
        } else {
            violations.add("FAIL: Loan purpose '" + purpose + "' is not approved");
            compliant = false;
        }

        // Rule 4: Minimum Amount
        BigDecimal minAmount = new BigDecimal("1000");
        if (amount.compareTo(minAmount) >= 0) {
            ruleResults.add("PASS: Loan amount $" + amount + " meets minimum $" + minAmount);
        } else {
            violations.add("FAIL: Loan amount $" + amount + " below minimum $" + minAmount);
            compliant = false;
        }

        PolicyResult result = new PolicyResult(compliant, ruleResults, violations);

        log.info("Policy compliance check complete: compliant={}, rules={}, violations={}",
                compliant, ruleResults.size(), violations.size());

        return result;
    }

    /**
     * Verify customer KYC (Know Your Customer) status.
     */
    @Tool("Verify the KYC (Know Your Customer) status for a customer. Returns verification status and date.")
    public KycResult verifyKYC(
            @P("The unique customer identifier") String customerId) {

        log.info("Tool invoked: verifyKYC(customerId={})", customerId);

        // Mock implementation - generate deterministic KYC based on customer ID
        KycResult result = kycCache.computeIfAbsent(customerId, id -> {
            boolean verified = !id.contains("UNVERIFIED");
            LocalDate verificationDate = verified ?
                    LocalDate.now().minusMonths(generateMonthsAgo(id)) : null;
            String level = verified ? "FULL" : "NONE";
            return new KycResult(id, verified, verificationDate, level);
        });

        log.info("KYC verification result: customerId={}, verified={}, current={}",
                customerId, result.verified(), result.isCurrent());

        return result;
    }

    /**
     * Calculate risk score for a loan application.
     */
    @Tool("Calculate the risk score for a loan application. Returns a risk score between 0 (lowest risk) and 1 (highest risk).")
    public RiskScore calculateRiskScore(
            @P("The customer's credit score") int creditScore,
            @P("The loan amount requested") BigDecimal amount,
            @P("Years of employment") int employmentYears) {

        log.info("Tool invoked: calculateRiskScore(creditScore={}, amount={}, employmentYears={})",
                creditScore, amount, employmentYears);

        // Risk calculation algorithm
        double riskScore = 0.0;

        // Credit score factor (40% weight)
        // Score 800+ = 0 risk, Score 500 = 0.4 risk
        double creditRisk = Math.max(0, Math.min(0.4, (800 - creditScore) / 750.0));
        riskScore += creditRisk;

        // Loan amount factor (30% weight)
        // Higher amounts = higher risk
        double amountRisk = amount.divide(MAX_LOAN_AMOUNT.multiply(new BigDecimal("2")), 4, RoundingMode.HALF_UP)
                .doubleValue() * 0.3;
        amountRisk = Math.min(0.3, amountRisk);
        riskScore += amountRisk;

        // Employment factor (30% weight)
        // More years = lower risk
        double employmentRisk = Math.max(0, (5 - employmentYears) / 5.0) * 0.3;
        employmentRisk = Math.max(0, Math.min(0.3, employmentRisk));
        riskScore += employmentRisk;

        // Normalize to 0-1 range
        riskScore = Math.max(0, Math.min(1, riskScore));
        riskScore = Math.round(riskScore * 100) / 100.0;

        // Determine risk level
        String level;
        if (riskScore < 0.3) {
            level = "LOW";
        } else if (riskScore < 0.5) {
            level = "MEDIUM";
        } else if (riskScore < 0.7) {
            level = "HIGH";
        } else {
            level = "VERY_HIGH";
        }

        String explanation = String.format(
                "Risk score %.2f calculated from: credit factor %.2f, amount factor %.2f, employment factor %.2f",
                riskScore, creditRisk, amountRisk, employmentRisk
        );

        RiskScore result = new RiskScore(riskScore, level, explanation);

        log.info("Risk score calculated: score={}, level={}", riskScore, level);

        return result;
    }

    /**
     * Get employment information for a customer.
     */
    @Tool("Get employment information for a customer including years of employment and employer details.")
    public EmploymentInfo getEmploymentInfo(
            @P("The unique customer identifier") String customerId) {

        log.info("Tool invoked: getEmploymentInfo(customerId={})", customerId);

        // Mock implementation
        int years = generateEmploymentYears(customerId);
        String employer = "Acme Corporation";
        String status = years >= MIN_EMPLOYMENT_YEARS ? "STABLE" : "NEW";

        EmploymentInfo result = new EmploymentInfo(customerId, years, employer, status);

        log.info("Employment info retrieved: customerId={}, years={}, status={}",
                customerId, years, status);

        return result;
    }

    // Helper methods for mock data generation
    private int generateMockCreditScore(String customerId) {
        // Generate deterministic score based on customer ID hash
        int hash = Math.abs(customerId.hashCode());
        // Range: 550-800
        return 550 + (hash % 251);
    }

    private String determineRating(int score) {
        if (score >= 750) return "EXCELLENT";
        if (score >= 700) return "GOOD";
        if (score >= 650) return "FAIR";
        if (score >= 600) return "POOR";
        return "VERY_POOR";
    }

    private int generateMonthsAgo(String customerId) {
        int hash = Math.abs(customerId.hashCode());
        return 1 + (hash % 12); // 1-12 months ago
    }

    private int generateEmploymentYears(String customerId) {
        int hash = Math.abs(customerId.hashCode());
        return (hash % 15); // 0-14 years
    }

    /**
     * Employment information record.
     */
    public record EmploymentInfo(
            String customerId,
            int yearsEmployed,
            String employer,
            String status
    ) {}
}

