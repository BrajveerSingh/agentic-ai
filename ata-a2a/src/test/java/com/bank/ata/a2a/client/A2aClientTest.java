package com.bank.ata.a2a.client;

import com.bank.ata.a2a.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link A2aClient}.
 * Uses Mockito deep stubs to mock the fluent {@link RestClient} chain.
 */
@ExtendWith(MockitoExtension.class)
class A2aClientTest {

    /** Deep stubs allow chaining: restClient.post().uri(...).contentType(...).body(...).retrieve().body(...) */
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private RestClient restClient;

    private A2aClient client;

    private static final String TASKS_URL = "http://localhost:9090/mock/risk-agent/tasks/send";

    @BeforeEach
    void setUp() {
        client = new A2aClient(restClient, TASKS_URL, "risk-agent");
    }

    // =========================================================================
    // sendTask
    // =========================================================================

    @Test
    @DisplayName("sendTask: successful response — should return completed task")
    void sendTask_success_shouldReturnCompletedTask() {
        A2aTask task   = A2aTask.of(A2aMessage.user("{\"text\":\"hello\"}"), "assess_risk");
        A2aTask result = task.completed(List.of(
                A2aArtifact.of("risk_report", "Risk report", "{\"riskScore\":0.2}")));

        when(restClient.post()
                .uri(anyString())
                .contentType(any())
                .body(any(A2aTask.class))
                .retrieve()
                .body(A2aTask.class))
                .thenReturn(result);

        A2aTask returned = client.sendTask(task);

        assertThat(returned.status().state()).isEqualTo(TaskState.COMPLETED);
        assertThat(returned.artifacts()).hasSize(1);
    }

    @Test
    @DisplayName("sendTask: null response body — should throw RuntimeException")
    void sendTask_nullBody_shouldThrow() {
        A2aTask task = A2aTask.of(A2aMessage.user("{}"), "assess_risk");

        when(restClient.post()
                .uri(anyString())
                .contentType(any())
                .body(any(A2aTask.class))
                .retrieve()
                .body(A2aTask.class))
                .thenReturn(null);

        assertThatThrownBy(() -> client.sendTask(task))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No response body");
    }

    @Test
    @DisplayName("sendTask: peer returns FAILED task — should throw RuntimeException")
    void sendTask_peerFailed_shouldThrow() {
        A2aTask task   = A2aTask.of(A2aMessage.user("{}"), "assess_risk");
        A2aTask failed = task.failed("Internal error in risk agent");

        when(restClient.post()
                .uri(anyString())
                .contentType(any())
                .body(any(A2aTask.class))
                .retrieve()
                .body(A2aTask.class))
                .thenReturn(failed);

        assertThatThrownBy(() -> client.sendTask(task))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("FAILED");
    }

    @Test
    @DisplayName("sendTask: HTTP error thrown — should propagate exception")
    void sendTask_httpError_shouldPropagate() {
        A2aTask task = A2aTask.of(A2aMessage.user("{}"), "assess_risk");

        when(restClient.post()
                .uri(anyString())
                .contentType(any())
                .body(any(A2aTask.class))
                .retrieve()
                .body(A2aTask.class))
                .thenThrow(new RestClientException("503 Service Unavailable"));

        assertThatThrownBy(() -> client.sendTask(task))
                .isInstanceOf(RestClientException.class);
    }

    // =========================================================================
    // isAvailable
    // =========================================================================

    @Test
    @DisplayName("isAvailable: health endpoint returns 200 — should return true")
    void isAvailable_healthy_shouldReturnTrue() {
        when(restClient.get()
                .uri(anyString())
                .retrieve()
                .toBodilessEntity())
                .thenReturn(ResponseEntity.ok().build());

        assertThat(client.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("isAvailable: health endpoint throws — should return false")
    void isAvailable_unhealthy_shouldReturnFalse() {
        when(restClient.get()
                .uri(anyString())
                .retrieve()
                .toBodilessEntity())
                .thenThrow(new RestClientException("Connection refused"));

        assertThat(client.isAvailable()).isFalse();
    }

    // =========================================================================
    // fetchAgentCard
    // =========================================================================

    @Test
    @DisplayName("fetchAgentCard: should return the agent card from well-known endpoint")
    void fetchAgentCard_shouldReturnCard() {
        AgentCard card = AgentCard.of(
                "mock-risk-agent", "Risk agent", "http://localhost:9090/mock/risk-agent",
                "1.0.0", List.of(AgentSkill.of("assess_risk", "Assess Risk", "Desc")));

        when(restClient.get()
                .uri(anyString())
                .retrieve()
                .body(AgentCard.class))
                .thenReturn(card);

        AgentCard returned = client.fetchAgentCard();

        assertThat(returned.name()).isEqualTo("mock-risk-agent");
        assertThat(returned.skills()).hasSize(1);
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    @Test
    @DisplayName("accessors: should return correct agent name and tasks URL")
    void accessors_shouldReturnCorrectValues() {
        assertThat(client.getAgentName()).isEqualTo("risk-agent");
        assertThat(client.getTasksEndpointUrl()).isEqualTo(TASKS_URL);
    }
}
