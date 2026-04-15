package com.bank.ata.audit.service;

import com.bank.ata.audit.dto.AuditReport;
import com.bank.ata.audit.entity.*;
import com.bank.ata.audit.repository.*;
import tools.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing audit trail persistence.
 * All audit events are immutable once created.
 */
@Service
@Transactional
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository auditEventRepository;
    private final ReasoningStepRepository reasoningStepRepository;
    private final ToolCallRepository toolCallRepository;
    private final LoanDecisionRepository loanDecisionRepository;
    private final ObjectMapper objectMapper;

    // Optional — present when EmbeddingStoreConfig is on the classpath
    @Autowired(required = false)
    private EmbeddingStore<TextSegment> embeddingStore;

    @Autowired(required = false)
    private EmbeddingModel embeddingModel;

    public AuditService(AuditEventRepository auditEventRepository,
                        ReasoningStepRepository reasoningStepRepository,
                        ToolCallRepository toolCallRepository,
                        LoanDecisionRepository loanDecisionRepository,
                        ObjectMapper objectMapper) {
        this.auditEventRepository = auditEventRepository;
        this.reasoningStepRepository = reasoningStepRepository;
        this.toolCallRepository = toolCallRepository;
        this.loanDecisionRepository = loanDecisionRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Log the start of a new evaluation session.
     */
    public AuditEventEntity logSessionStart(UUID sessionId, UUID applicationId, UUID userId) {
        log.info("Logging session start: sessionId={}, applicationId={}", sessionId, applicationId);
        AuditEventEntity event = new AuditEventEntity(sessionId, applicationId, "SESSION_START", userId);
        return auditEventRepository.save(event);
    }

    /**
     * Log the end of an evaluation session.
     */
    public AuditEventEntity logSessionEnd(UUID sessionId, UUID applicationId) {
        log.info("Logging session end: sessionId={}", sessionId);
        AuditEventEntity event = new AuditEventEntity(sessionId, applicationId, "SESSION_END", null);
        return auditEventRepository.save(event);
    }

    /**
     * Log a reasoning step (Thought/Action/Observation).
     * Also stores an embedding of the step text for semantic search.
     */
    public AuditEventEntity logReasoningStep(UUID sessionId, UUID applicationId, int stepNumber,
                                              String thought, String action, String observation) {
        AuditEventEntity event = new AuditEventEntity(sessionId, applicationId, "REASONING_STEP", null);
        event = auditEventRepository.save(event);

        ReasoningStepEntity step = new ReasoningStepEntity(
                event.getEventId(), stepNumber, thought, action, observation);
        reasoningStepRepository.save(step);

        // Store embedding for semantic search over reasoning history
        embedReasoningStep(event.getEventId(), stepNumber, thought, action, observation);

        log.debug("Logged reasoning step {}: action={}", stepNumber, action);
        return event;
    }

    /**
     * Store an embedding for a reasoning step (no-op if embedding infrastructure is absent).
     */
    private void embedReasoningStep(UUID eventId, int stepNumber,
                                     String thought, String action, String observation) {
        if (embeddingStore == null || embeddingModel == null) {
            return;
        }
        try {
            String text = buildEmbeddingText(thought, action, observation);
            dev.langchain4j.data.document.Metadata metadata = dev.langchain4j.data.document.Metadata.from(
                    "eventId", eventId.toString());
            metadata.put("stepNumber", String.valueOf(stepNumber));
            TextSegment segment = TextSegment.from(text, metadata);
            Embedding embedding = embeddingModel.embed(segment.text()).content();
            embeddingStore.add(embedding, segment);
            log.debug("Stored embedding for reasoning step {} of event {}", stepNumber, eventId);
        } catch (Exception e) {
            // Embedding failure must never break audit persistence
            log.warn("Failed to store embedding for step {} of event {}: {}", stepNumber, eventId, e.getMessage());
        }
    }

    private static String buildEmbeddingText(String thought, String action, String observation) {
        StringBuilder sb = new StringBuilder();
        if (thought != null && !thought.isBlank())       sb.append("Thought: ").append(thought).append(' ');
        if (action != null && !action.isBlank())         sb.append("Action: ").append(action).append(' ');
        if (observation != null && !observation.isBlank()) sb.append("Observation: ").append(observation);
        return sb.toString().trim();
    }

    /**
     * Semantic search over stored reasoning steps using natural-language query.
     *
     * @param query      free-text query (e.g. "credit score evaluation")
     * @param maxResults maximum number of similar steps to return
     * @return list of matching text segments with similarity scores
     */
    @Transactional(readOnly = true)
    public List<EmbeddingMatch<TextSegment>> searchSimilarReasoningSteps(String query, int maxResults) {
        if (embeddingStore == null || embeddingModel == null) {
            log.warn("Embedding infrastructure not available — semantic search returning empty result");
            return List.of();
        }
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(0.5)
                .build();
        return embeddingStore.search(request).matches();
    }

    /**
     * Log the start of a tool call (BEFORE execution).
     * Returns the ToolCallEntity ID for later completion.
     */
    public ToolCallEntity logToolCallStart(UUID sessionId, UUID applicationId,
                                            String toolName, Object[] args) {
        AuditEventEntity event = new AuditEventEntity(sessionId, applicationId, "TOOL_CALL", null);
        event = auditEventRepository.save(event);

        String inputJson = serializeToJson(args);
        ToolCallEntity toolCall = new ToolCallEntity(event.getEventId(), toolName, inputJson);
        toolCall = toolCallRepository.save(toolCall);

        log.debug("Logged tool call start: tool={}, toolCallId={}", toolName, toolCall.getToolCallId());
        return toolCall;
    }

    /**
     * Log successful completion of a tool call.
     */
    public void logToolCallComplete(UUID toolCallId, Object result, long executionTimeMs) {
        ToolCallEntity toolCall = toolCallRepository.findById(toolCallId)
                .orElseThrow(() -> new IllegalArgumentException("Tool call not found: " + toolCallId));

        String resultJson = serializeToJson(result);
        toolCall.complete(resultJson, executionTimeMs);
        toolCallRepository.save(toolCall);

        log.debug("Tool call completed: toolCallId={}, executionTimeMs={}", toolCallId, executionTimeMs);
    }

    /**
     * Log failed tool call.
     */
    public void logToolCallError(UUID toolCallId, Exception error, long executionTimeMs) {
        ToolCallEntity toolCall = toolCallRepository.findById(toolCallId)
                .orElseThrow(() -> new IllegalArgumentException("Tool call not found: " + toolCallId));

        toolCall.fail(error.getMessage(), executionTimeMs);
        toolCallRepository.save(toolCall);

        log.warn("Tool call failed: toolCallId={}, error={}", toolCallId, error.getMessage());
    }

    /**
     * Log the final loan decision.
     */
    public LoanDecisionEntity logDecision(UUID sessionId, UUID applicationId, String outcome,
                                          String reasoning, double confidenceScore) {
        AuditEventEntity event = new AuditEventEntity(sessionId, applicationId, "DECISION", null);
        auditEventRepository.save(event);

        LoanDecisionEntity decision = new LoanDecisionEntity(
                applicationId, sessionId, outcome, reasoning, confidenceScore);
        decision = loanDecisionRepository.save(decision);

        log.info("Logged decision: applicationId={}, outcome={}, confidence={}",
                applicationId, outcome, confidenceScore);
        return decision;
    }

    /**
     * Retrieve the complete audit trail for a loan application.
     */
    @Transactional(readOnly = true)
    public AuditReport getAuditTrail(UUID applicationId) {
        List<AuditEventEntity> events = auditEventRepository
                .findByApplicationIdOrderByCreatedAtAsc(applicationId);

        List<UUID> eventIds = events.stream()
                .map(AuditEventEntity::getEventId)
                .toList();

        List<ReasoningStepEntity> steps = reasoningStepRepository
                .findByEventIdInOrderByStepNumberAsc(eventIds);

        List<ToolCallEntity> toolCalls = toolCallRepository
                .findByEventIdIn(eventIds);

        LoanDecisionEntity decision = loanDecisionRepository
                .findByApplicationId(applicationId)
                .orElse(null);

        log.debug("Retrieved audit trail for applicationId={}: events={}, steps={}, toolCalls={}",
                applicationId, events.size(), steps.size(), toolCalls.size());

        return new AuditReport(applicationId, events, steps, toolCalls, decision);
    }

    /**
     * Retrieve audit trail by session ID.
     */
    @Transactional(readOnly = true)
    public AuditReport getAuditTrailBySession(UUID sessionId) {
        List<AuditEventEntity> events = auditEventRepository
                .findBySessionIdOrderByCreatedAtAsc(sessionId);

        if (events.isEmpty()) {
            return new AuditReport(null, List.of(), List.of(), List.of(), null);
        }

        UUID applicationId = events.get(0).getApplicationId();
        return getAuditTrail(applicationId);
    }

    /**
     * Check if audit trail exists for an application.
     */
    @Transactional(readOnly = true)
    public boolean hasAuditTrail(UUID applicationId) {
        return auditEventRepository.countByApplicationId(applicationId) > 0;
    }

    /**
     * Serialize an object to JSON for storage.
     */
    private String serializeToJson(Object obj) {
        if (obj == null) {
            return "null";
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to serialize object to JSON: {}", e.getMessage());
            return obj.toString();
        }
    }
}

