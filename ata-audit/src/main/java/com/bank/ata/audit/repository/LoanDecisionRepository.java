package com.bank.ata.audit.repository;

import com.bank.ata.audit.entity.LoanDecisionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for loan decisions.
 */
@Repository
public interface LoanDecisionRepository extends JpaRepository<LoanDecisionEntity, UUID> {

    /**
     * Find decision by application ID.
     */
    Optional<LoanDecisionEntity> findByApplicationId(UUID applicationId);

    /**
     * Find decision by session ID.
     */
    Optional<LoanDecisionEntity> findBySessionId(UUID sessionId);

    /**
     * Check if a decision exists for an application.
     */
    boolean existsByApplicationId(UUID applicationId);
}

