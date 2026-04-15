package com.bank.ata.audit.service;

import com.bank.ata.audit.dto.AuditReport;
import com.bank.ata.audit.entity.AuditEventEntity;
import com.bank.ata.audit.entity.LoanDecisionEntity;
import com.bank.ata.audit.entity.ReasoningStepEntity;
import com.bank.ata.audit.entity.ToolCallEntity;
import com.bank.ata.audit.repository.AuditEventRepository;
import com.bank.ata.audit.repository.LoanDecisionRepository;
import com.bank.ata.audit.repository.ReasoningStepRepository;
import com.bank.ata.audit.repository.ToolCallRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditEventRepository auditEventRepository;
    @Mock
    private ReasoningStepRepository reasoningStepRepository;
    @Mock
    private ToolCallRepository toolCallRepository;
    @Mock
    private LoanDecisionRepository loanDecisionRepository;

    private AuditService auditService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        auditService = new AuditService(
                auditEventRepository,
                reasoningStepRepository,
                toolCallRepository,
                loanDecisionRepository,
                objectMapper
        );
    }

    @Test
    @DisplayName("Should log session start")
    void shouldLogSessionStart() {
        // Given
        UUID sessionId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(auditEventRepository.save(any(AuditEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        AuditEventEntity result = auditService.logSessionStart(sessionId, applicationId, userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSessionId()).isEqualTo(sessionId);
        assertThat(result.getApplicationId()).isEqualTo(applicationId);
        assertThat(result.getEventType()).isEqualTo("SESSION_START");
        verify(auditEventRepository).save(any(AuditEventEntity.class));
    }

    @Test
    @DisplayName("Should log reasoning step")
    void shouldLogReasoningStep() {
        // Given
        UUID sessionId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        String thought = "I need to check the credit score";
        String action = "getCreditScore";
        String observation = "Credit score is 720";

        when(auditEventRepository.save(any(AuditEventEntity.class)))
                .thenAnswer(invocation -> {
                    AuditEventEntity entity = invocation.getArgument(0);
                    return entity;
                });
        when(reasoningStepRepository.save(any(ReasoningStepEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        AuditEventEntity result = auditService.logReasoningStep(
                sessionId, applicationId, 1, thought, action, observation);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEventType()).isEqualTo("REASONING_STEP");
        verify(reasoningStepRepository).save(any(ReasoningStepEntity.class));
    }

    @Test
    @DisplayName("Should log tool call start and completion")
    void shouldLogToolCallStartAndComplete() {
        // Given
        UUID sessionId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        String toolName = "getCreditScore";
        Object[] args = new Object[]{"CUST001"};

        AuditEventEntity savedEvent = new AuditEventEntity(sessionId, applicationId, "TOOL_CALL", null);
        ToolCallEntity savedToolCall = new ToolCallEntity(UUID.randomUUID(), toolName, "[\"CUST001\"]");

        // Inject a known toolCallId — JPA @GeneratedValue does not fire outside persistence context
        UUID knownToolCallId = UUID.randomUUID();
        ReflectionTestUtils.setField(savedToolCall, "toolCallId", knownToolCallId);

        when(auditEventRepository.save(any(AuditEventEntity.class)))
                .thenReturn(savedEvent);
        when(toolCallRepository.save(any(ToolCallEntity.class)))
                .thenReturn(savedToolCall);
        when(toolCallRepository.findById(knownToolCallId))
                .thenReturn(Optional.of(savedToolCall));

        // When - Start
        ToolCallEntity startResult = auditService.logToolCallStart(sessionId, applicationId, toolName, args);

        // Then
        assertThat(startResult).isNotNull();
        assertThat(startResult.getToolName()).isEqualTo(toolName);

        // When - Complete (use the known ID, not savedToolCall.getToolCallId() which would be null before JPA save)
        auditService.logToolCallComplete(knownToolCallId,
                new TestResult(720, "GOOD"), 150L);

        // Then
        verify(toolCallRepository, times(2)).save(any(ToolCallEntity.class));
    }

    @Test
    @DisplayName("Should log decision")
    void shouldLogDecision() {
        // Given
        UUID sessionId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        String outcome = "APPROVED";
        String reasoning = "Customer meets all criteria";
        double confidence = 0.95;

        when(auditEventRepository.save(any(AuditEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(loanDecisionRepository.save(any(LoanDecisionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        LoanDecisionEntity result = auditService.logDecision(
                sessionId, applicationId, outcome, reasoning, confidence);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getOutcome()).isEqualTo(outcome);
        assertThat(result.getReasoning()).isEqualTo(reasoning);
        verify(loanDecisionRepository).save(any(LoanDecisionEntity.class));
    }

    @Test
    @DisplayName("Should retrieve audit trail")
    void shouldRetrieveAuditTrail() {
        // Given
        UUID applicationId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        AuditEventEntity event = new AuditEventEntity(sessionId, applicationId, "SESSION_START", null);

        when(auditEventRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId))
                .thenReturn(List.of(event));
        when(reasoningStepRepository.findByEventIdInOrderByStepNumberAsc(any()))
                .thenReturn(List.of());
        when(toolCallRepository.findByEventIdIn(any()))
                .thenReturn(List.of());
        when(loanDecisionRepository.findByApplicationId(applicationId))
                .thenReturn(Optional.empty());

        // When
        AuditReport report = auditService.getAuditTrail(applicationId);

        // Then
        assertThat(report).isNotNull();
        assertThat(report.applicationId()).isEqualTo(applicationId);
        assertThat(report.getTotalEvents()).isEqualTo(1);
    }

    @Test
    @DisplayName("AuditService should not expose any delete operations (immutability)")
    void shouldNotExposeDeleteOperations() {
        // Phase 2 acceptance criterion: audit data must be immutable — no delete methods allowed
        boolean hasDeleteMethod = Arrays.stream(AuditService.class.getMethods())
                .anyMatch(m -> m.getName().toLowerCase().contains("delete"));
        assertThat(hasDeleteMethod)
                .as("AuditService must not expose any delete methods to guarantee immutability")
                .isFalse();
    }

    // Helper class for test
    record TestResult(int score, String rating) {
    }
}

