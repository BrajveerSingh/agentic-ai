package com.bank.ata.audit.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Base audit event entity.
 * All audit events are immutable once created.
 */
@Entity
@Table(name = "audit_event", indexes = {
    @Index(name = "idx_audit_session", columnList = "session_id"),
    @Index(name = "idx_audit_application", columnList = "application_id"),
    @Index(name = "idx_audit_created", columnList = "created_at")
})
public class AuditEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "event_id", updatable = false, nullable = false)
    private UUID eventId;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "application_id", updatable = false)
    private UUID applicationId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 50)
    private String eventType;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditEventEntity() {
        // JPA required
    }

    public AuditEventEntity(UUID sessionId, UUID applicationId, String eventType, UUID userId) {
        this.sessionId = sessionId;
        this.applicationId = applicationId;
        this.eventType = eventType;
        this.userId = userId;
        this.createdAt = Instant.now();
    }

    // Getters only - immutable entity
    public UUID getEventId() { return eventId; }
    public UUID getSessionId() { return sessionId; }
    public UUID getApplicationId() { return applicationId; }
    public String getEventType() { return eventType; }
    public UUID getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public String toString() {
        return "AuditEventEntity{" +
                "eventId=" + eventId +
                ", sessionId=" + sessionId +
                ", eventType='" + eventType + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}

