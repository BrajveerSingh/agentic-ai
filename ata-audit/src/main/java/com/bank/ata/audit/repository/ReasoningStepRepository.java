package com.bank.ata.audit.repository;

import com.bank.ata.audit.entity.ReasoningStepEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for reasoning steps.
 */
@Repository
public interface ReasoningStepRepository extends JpaRepository<ReasoningStepEntity, UUID> {

    /**
     * Find all reasoning steps for an event, ordered by step number.
     */
    List<ReasoningStepEntity> findByEventIdOrderByStepNumberAsc(UUID eventId);

    /**
     * Find reasoning steps for multiple events.
     */
    List<ReasoningStepEntity> findByEventIdInOrderByStepNumberAsc(List<UUID> eventIds);

    /**
     * Count reasoning steps for an event.
     */
    long countByEventId(UUID eventId);
}

