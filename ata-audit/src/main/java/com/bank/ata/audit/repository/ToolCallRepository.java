package com.bank.ata.audit.repository;

import com.bank.ata.audit.entity.ToolCallEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for tool calls.
 */
@Repository
public interface ToolCallRepository extends JpaRepository<ToolCallEntity, UUID> {

    /**
     * Find all tool calls for an event.
     */
    List<ToolCallEntity> findByEventIdOrderByToolCallIdAsc(UUID eventId);

    /**
     * Find tool calls for multiple events.
     */
    List<ToolCallEntity> findByEventIdIn(List<UUID> eventIds);

    /**
     * Find tool calls by name for reporting.
     */
    List<ToolCallEntity> findByToolNameOrderByToolCallIdDesc(String toolName);

    /**
     * Find failed tool calls for monitoring.
     */
    List<ToolCallEntity> findBySuccessFalse();
}

