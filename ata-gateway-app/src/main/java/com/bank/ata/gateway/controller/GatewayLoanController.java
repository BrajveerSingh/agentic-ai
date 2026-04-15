package com.bank.ata.gateway.controller;

import com.bank.ata.gateway.client.OrchestratorClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Public REST API that forwards requests to the orchestrator service.
 */
@RestController
@RequestMapping("/api/loans")
@Validated
public class GatewayLoanController {

    private final OrchestratorClient orchestratorClient;
    private final String orchestratorBaseUrl;

    public GatewayLoanController(
            OrchestratorClient orchestratorClient,
            @Value("${ata.orchestrator.base-url}") String orchestratorBaseUrl) {
        this.orchestratorClient = orchestratorClient;
        this.orchestratorBaseUrl = orchestratorBaseUrl;
    }

    public record LoanEvaluationRequest(
            @NotBlank String customerId,
            @NotNull @DecimalMin("1000") BigDecimal amount,
            @NotBlank String purpose,
            String loanType
    ) {}

    @PostMapping("/evaluate")
    public ResponseEntity<Map<String, Object>> evaluate(@Valid @RequestBody LoanEvaluationRequest request) {
        Map<String, Object> resp = orchestratorClient.evaluateLoan(orchestratorBaseUrl, Map.of(
                "customerId", request.customerId,
                "amount", request.amount,
                "purpose", request.purpose,
                "loanType", request.loanType,
                "timestamp", Instant.now().toString()
        ));
        return ResponseEntity.ok(resp);
    }
}

