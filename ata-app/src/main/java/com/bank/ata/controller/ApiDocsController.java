package com.bank.ata.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * API documentation endpoint.
 */
@RestController
@RequestMapping("/api")
public class ApiDocsController {

    @GetMapping("/docs")
    public ResponseEntity<Map<String, Object>> getDocs() {
        return ResponseEntity.ok(Map.of(
                "name", "Audit Trail Agent API",
                "version", "1.0.0",
                "description", "Bank-grade loan evaluation API with AI-powered decisions and full audit trail",
                "endpoints", List.of(
                        Map.of(
                                "path", "/api/health",
                                "method", "GET",
                                "description", "Health check endpoint"
                        ),
                        Map.of(
                                "path", "/api/info",
                                "method", "GET",
                                "description", "Application information"
                        ),
                        Map.of(
                                "path", "/api/loans/evaluate",
                                "method", "POST",
                                "description", "Evaluate a loan application using AI agent",
                                "requestBody", Map.of(
                                        "customerId", "string (required)",
                                        "amount", "number (required, min: 1000)",
                                        "purpose", "string (required)",
                                        "loanType", "string (optional: PERSONAL, MORTGAGE, AUTO, BUSINESS, HOME_IMPROVEMENT, EDUCATION, DEBT_CONSOLIDATION)"
                                ),
                                "response", Map.of(
                                        "applicationId", "UUID",
                                        "outcome", "APPROVED | REJECTED | PENDING_REVIEW | REQUIRES_ADDITIONAL_INFO",
                                        "reasoning", "string",
                                        "confidenceScore", "number (0.0-1.0)"
                                )
                        ),
                        Map.of(
                                "path", "/api/loans/evaluate/quick",
                                "method", "POST",
                                "description", "Quick rule-based evaluation (no AI)"
                        )
                ),
                "tools", List.of(
                        Map.of("name", "getCreditScore", "description", "Get customer credit score"),
                        Map.of("name", "verifyKYC", "description", "Verify customer KYC status"),
                        Map.of("name", "checkPolicyCompliance", "description", "Check loan policy compliance"),
                        Map.of("name", "calculateRiskScore", "description", "Calculate loan risk score"),
                        Map.of("name", "getEmploymentInfo", "description", "Get customer employment info")
                )
        ));
    }
}

