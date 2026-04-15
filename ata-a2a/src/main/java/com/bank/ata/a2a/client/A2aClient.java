package com.bank.ata.a2a.client;

import com.bank.ata.a2a.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * HTTP client for calling a peer A2A agent using the modern {@link RestClient}.
 *
 * <p>Constructed by {@link com.bank.ata.a2a.client.A2aClientConfig}; not a Spring component itself so
 * multiple instances (risk-agent, fraud-agent) can be created with different URLs.</p>
 *
 * <pre>
 * A2aTask result = riskAgentClient.sendTask(A2aTask.of(
 *         A2aMessage.user(loanJson), "assess_risk"));
 * </pre>
 */
public class A2aClient {

    private static final Logger log = LoggerFactory.getLogger(A2aClient.class);

    private final RestClient restClient;
    private final String     tasksEndpointUrl;
    private final String     agentCardUrl;
    private final String     healthUrl;
    private final String     agentName;

    public A2aClient(RestClient restClient, String tasksEndpointUrl, String agentName) {
        this.restClient       = restClient;
        this.tasksEndpointUrl = tasksEndpointUrl;
        this.agentName        = agentName;

        // Derive agent-card and health URLs from the tasks URL
        // e.g. http://host/mock/risk-agent/tasks/send
        //   →  http://host/mock/risk-agent/.well-known/agent.json
        //   →  http://host/mock/risk-agent/health
        String base       = tasksEndpointUrl.replaceAll("/tasks/send$", "");
        this.agentCardUrl = base + "/.well-known/agent.json";
        this.healthUrl    = base + "/health";
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Send a task to the peer agent and wait for the synchronous response.
     *
     * @param task task to send (must have a non-null message)
     * @return completed task with artifacts
     * @throws RestClientException if the HTTP call fails
     * @throws RuntimeException    if the peer returns a FAILED task
     */
    public A2aTask sendTask(A2aTask task) {
        log.debug("Sending A2A task to agent='{}' url='{}'", agentName, tasksEndpointUrl);

        A2aTask result = restClient.post()
                .uri(tasksEndpointUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(task)
                .retrieve()
                .body(A2aTask.class);

        if (result == null) {
            throw new RuntimeException("No response body from agent '" + agentName + "'");
        }
        if (result.status() != null && result.status().state() == TaskState.FAILED) {
            throw new RuntimeException("Agent '" + agentName + "' returned FAILED: "
                    + result.status().message());
        }

        log.info("A2A task completed: agent='{}' taskId='{}' state={}",
                agentName, result.id(), result.status() != null ? result.status().state() : "?");
        return result;
    }

    /**
     * Fetch the Agent Card from the peer agent.
     *
     * @return the peer's AgentCard
     */
    public AgentCard fetchAgentCard() {
        return restClient.get()
                .uri(agentCardUrl)
                .retrieve()
                .body(AgentCard.class);
    }

    /**
     * Lightweight availability check — returns {@code false} on any exception
     * so callers can degrade gracefully.
     */
    public boolean isAvailable() {
        try {
            restClient.get()
                    .uri(healthUrl)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("Agent '{}' health check failed: {}", agentName, e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String getAgentName()        { return agentName; }
    public String getTasksEndpointUrl() { return tasksEndpointUrl; }
}
