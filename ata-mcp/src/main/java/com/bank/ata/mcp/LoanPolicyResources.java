package com.bank.ata.mcp;

import com.bank.ata.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

/**
 * MCP resource provider for bank loan policy documents.
 *
 * <p>Exposes two MCP resources:</p>
 * <ul>
 *   <li>{@code loan://policies}              — index of all available policies</li>
 *   <li>{@code loan://policies/{policyId}}   — individual policy document
 *       ({@code standard | credit | kyc | risk})</li>
 * </ul>
 *
 * <p>These resources are registered with {@link com.bank.ata.mcp.server.McpResourceRegistry}
 * at startup by {@link McpServerConfig} and become accessible to any MCP client via the
 * {@code resources/list} and {@code resources/read} protocol messages.</p>
 */
@Component
public class LoanPolicyResources {

    // -------------------------------------------------------------------------
    // Static resource: policy index
    // -------------------------------------------------------------------------

    @McpResource(
            uri         = "loan://policies",
            name        = "Loan Policy Index",
            description = "Index of all available loan policy documents",
            mimeType    = "application/json")
    public String getPoliciesIndex(String uri) {
        return """
                {
                  "policies": [
                    {"id":"standard","name":"Standard Loan Policy",   "uri":"loan://policies/standard"},
                    {"id":"credit",  "name":"Credit Score Policy",    "uri":"loan://policies/credit"},
                    {"id":"kyc",     "name":"KYC Policy",             "uri":"loan://policies/kyc"},
                    {"id":"risk",    "name":"Risk Assessment Policy",  "uri":"loan://policies/risk"}
                  ]
                }""";
    }

    // -------------------------------------------------------------------------
    // URI-template resource: individual policy
    // -------------------------------------------------------------------------

    @McpResource(
            uri         = "loan://policies/{policyId}",
            name        = "Loan Policy Document",
            description = "Bank loan policy document. Supported IDs: standard, credit, kyc, risk",
            mimeType    = "application/json")
    public String getLoanPolicy(String uri) {
        String policyId = uri.contains("/") ? uri.substring(uri.lastIndexOf('/') + 1) : uri;
        return switch (policyId.toLowerCase()) {
            case "standard" -> standardPolicy();
            case "credit"   -> creditPolicy();
            case "kyc"      -> kycPolicy();
            case "risk"     -> riskPolicy();
            default         -> "{\"error\":\"Policy not found\",\"policyId\":\"" + policyId + "\"}";
        };
    }

    // -------------------------------------------------------------------------
    // Policy documents
    // -------------------------------------------------------------------------

    private String standardPolicy() {
        return """
                {
                  "id":"standard", "name":"Standard Loan Policy", "version":"2.0",
                  "effectiveDate":"2025-01-01",
                  "rules":[
                    {"rule":"MIN_CREDIT_SCORE",     "value":650,    "description":"Minimum credit score required"},
                    {"rule":"MAX_LOAN_AMOUNT",       "value":500000, "description":"Maximum loan amount (USD)"},
                    {"rule":"MIN_LOAN_AMOUNT",       "value":1000,   "description":"Minimum loan amount (USD)"},
                    {"rule":"MIN_EMPLOYMENT_YEARS",  "value":1,      "description":"Minimum years of employment"},
                    {"rule":"MAX_RISK_SCORE",        "value":0.7,    "description":"Maximum acceptable risk score"},
                    {"rule":"KYC_REQUIRED",          "value":true,   "description":"KYC verification mandatory"}
                  ]
                }""";
    }

    private String creditPolicy() {
        return """
                {
                  "id":"credit", "name":"Credit Score Policy", "version":"1.5",
                  "tiers":[
                    {"range":"750+",    "rating":"EXCELLENT", "maxAmount":500000, "rateMultiplier":1.0},
                    {"range":"700-749", "rating":"GOOD",      "maxAmount":300000, "rateMultiplier":1.1},
                    {"range":"650-699", "rating":"FAIR",      "maxAmount":150000, "rateMultiplier":1.3},
                    {"range":"<650",    "rating":"POOR",      "maxAmount":0,      "rateMultiplier":null}
                  ]
                }""";
    }

    private String kycPolicy() {
        return """
                {
                  "id":"kyc", "name":"KYC Policy", "version":"1.2",
                  "requirements":[
                    {"req":"IDENTITY_VERIFICATION",       "description":"Government-issued ID required"},
                    {"req":"ADDRESS_VERIFICATION",        "description":"Proof of address within 3 months"},
                    {"req":"REFRESH_PERIOD_MONTHS",       "value":24, "description":"Refresh every 24 months"},
                    {"req":"RISK_BASED_DUE_DILIGENCE",    "description":"Enhanced due diligence for high-risk customers"}
                  ]
                }""";
    }

    private String riskPolicy() {
        return """
                {
                  "id":"risk", "name":"Risk Assessment Policy", "version":"1.0",
                  "thresholds":[
                    {"level":"LOW",       "range":"0.0-0.3", "action":"AUTO_APPROVE"},
                    {"level":"MEDIUM",    "range":"0.3-0.5", "action":"APPROVE_WITH_CONDITIONS"},
                    {"level":"HIGH",      "range":"0.5-0.7", "action":"PENDING_REVIEW"},
                    {"level":"VERY_HIGH", "range":"0.7-1.0", "action":"AUTO_REJECT"}
                  ]
                }""";
    }
}

