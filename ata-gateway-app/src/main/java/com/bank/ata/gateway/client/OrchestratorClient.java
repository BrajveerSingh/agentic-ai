package com.bank.ata.gateway.client;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Simple HTTP client used by the gateway to call the orchestrator service.
 */
@Component
public class OrchestratorClient {

    private final RestClient restClient;

    public OrchestratorClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> evaluateLoan(String orchestratorBaseUrl, Map<String, Object> requestBody) {
        return restClient.post()
                .uri(orchestratorBaseUrl + "/internal/loans/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getAuditByApplicationId(String orchestratorBaseUrl, String applicationId) {
        return restClient.get()
                .uri(orchestratorBaseUrl + "/internal/audit/application/" + applicationId)
                .retrieve()
                .body(Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getAuditBySessionId(String orchestratorBaseUrl, String sessionId) {
        return restClient.get()
                .uri(orchestratorBaseUrl + "/internal/audit/session/" + sessionId)
                .retrieve()
                .body(Map.class);
    }
}

