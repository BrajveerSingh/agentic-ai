package com.bank.ata.audit.repository;

import com.bank.ata.audit.entity.AuditEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for audit events.
 * Note: No delete operations are exposed - audit events are immutable.
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {

    /**
     * Find all events for a loan application, ordered by creation time.
     */
    List<AuditEventEntity> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);

    /**
     * Find all events for a session, ordered by creation time.
     */
    List<AuditEventEntity> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    /**
     * Count events for a specific application.
     */
    long countByApplicationId(UUID applicationId);

    /**
     * Find events by type for an application.
     */
    List<AuditEventEntity> findByApplicationIdAndEventType(UUID applicationId, String eventType);
}

