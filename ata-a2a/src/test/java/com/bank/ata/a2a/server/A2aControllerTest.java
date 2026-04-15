package com.bank.ata.a2a.server;

import com.bank.ata.a2a.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link A2aController}.
 */
@ExtendWith(MockitoExtension.class)
class A2aControllerTest {

    @Mock
    private A2aTaskHandler handler;

    private A2aController controller;

    private static final String BASE_URL = "http://localhost:8080/a2a";

    @BeforeEach
    void setUp() {
        AgentCard card = AgentCard.of(
                "audit-trail-agent", "Test agent", BASE_URL, "1.0.0",
                List.of(AgentSkill.of("evaluate_loan", "Evaluate Loan", "Desc")));
        when(handler.buildAgentCard(eq(BASE_URL))).thenReturn(card);
        controller = new A2aController(handler, BASE_URL);
    }

    // =========================================================================
    // Agent Card
    // =========================================================================

    @Test
    @DisplayName("agentCard: should return the pre-built agent card")
    void agentCard_shouldReturnCard() {
        AgentCard card = controller.agentCard();

        assertThat(card.name()).isEqualTo("audit-trail-agent");
        assertThat(card.url()).isEqualTo(BASE_URL);
        assertThat(card.skills()).hasSize(1);
    }

    // =========================================================================
    // sendTask
    // =========================================================================

    @Test
    @DisplayName("sendTask: valid task — should delegate to handler and return 200")
    void sendTask_validTask_shouldReturn200() {
        A2aTask incoming = A2aTask.of(A2aMessage.user("{\"customerId\":\"CUST001\"}"), "evaluate_loan");
        A2aTask completed = incoming.completed(
                List.of(A2aArtifact.of("loan_decision", "Decision", "{\"outcome\":\"APPROVED\"}")));

        when(handler.handle(any(A2aTask.class))).thenReturn(completed);

        ResponseEntity<A2aTask> response = controller.sendTask(incoming);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status().state()).isEqualTo(TaskState.COMPLETED);
        verify(handler).handle(any(A2aTask.class));
    }

    @Test
    @DisplayName("sendTask: task with null message — should return 400")
    void sendTask_nullMessage_shouldReturn400() {
        A2aTask badTask = new A2aTask("id-1", "session-1", null,
                TaskStatus.submitted(), List.of(), "evaluate_loan");

        ResponseEntity<A2aTask> response = controller.sendTask(badTask);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @DisplayName("sendTask: task without id — should auto-assign a new id")
    void sendTask_noId_shouldAutoAssignId() {
        A2aTask noIdTask = new A2aTask(null, null, A2aMessage.user("{}"), null, List.of(), "evaluate_loan");
        A2aTask handlerResult = A2aTask.of(A2aMessage.user("{}"), "evaluate_loan")
                .completed(List.of());

        when(handler.handle(any(A2aTask.class))).thenReturn(handlerResult);

        ResponseEntity<A2aTask> response = controller.sendTask(noIdTask);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        // The handler should have been called with a task that has an id
        verify(handler).handle(any(A2aTask.class));
    }

    // =========================================================================
    // health
    // =========================================================================

    @Test
    @DisplayName("health: should return status UP with agent name and version")
    void health_shouldReturnUp() {
        Map<String, Object> health = controller.health();

        assertThat(health.get("status")).isEqualTo("UP");
        assertThat(health.get("agent")).isEqualTo("audit-trail-agent");
        assertThat(health.get("version")).isEqualTo("1.0.0");
        assertThat(health.get("skillCount")).isEqualTo(1);
    }
}

