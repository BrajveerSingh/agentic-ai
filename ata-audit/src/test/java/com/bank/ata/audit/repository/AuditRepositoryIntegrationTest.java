package com.bank.ata.audit.repository;

import com.bank.ata.audit.entity.*;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying the four audit repositories against a real PostgreSQL instance.
 *
 * Uses Testcontainers to spin up postgres:16-alpine automatically.
 * Tests are skipped gracefully when Docker is not available (CI without Docker socket, etc.).
 * Uses plain Spring JPA infrastructure (no Spring Boot test-autoconfigure slices).
 *
 * To run locally: ensure Docker is running — Testcontainers pulls the image automatically.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringJUnitConfig(AuditRepositoryIntegrationTest.JpaTestConfig.class)
@Transactional
class AuditRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("atadb_test")
                    .withUsername("ata")
                    .withPassword("ata_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("test.datasource.url",      postgres::getJdbcUrl);
        registry.add("test.datasource.username",  postgres::getUsername);
        registry.add("test.datasource.password",  postgres::getPassword);
    }

    // ── Minimal JPA configuration ──────────────────────────────────────────
    // Avoids spring-boot-test-autoconfigure (@DataJpaTest); works in plain
    // library modules that have no @SpringBootApplication class.
    @Configuration
    @EnableJpaRepositories(basePackages = "com.bank.ata.audit.repository")
    @EnableTransactionManagement
    static class JpaTestConfig {

        @Bean
        DataSource dataSource(
                @Value("${test.datasource.url}")      String url,
                @Value("${test.datasource.username}") String username,
                @Value("${test.datasource.password}") String password) {
            HikariConfig cfg = new HikariConfig();
            cfg.setJdbcUrl(url);
            cfg.setUsername(username);
            cfg.setPassword(password);
            cfg.setDriverClassName("org.postgresql.Driver");
            return new HikariDataSource(cfg);
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
            emf.setDataSource(dataSource);
            emf.setPackagesToScan("com.bank.ata.audit.entity");
            HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
            adapter.setGenerateDdl(true);
            adapter.setShowSql(false);
            emf.setJpaVendorAdapter(adapter);
            Properties props = new Properties();
            props.setProperty("hibernate.hbm2ddl.auto", "create-drop");
            props.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
            emf.setJpaProperties(props);
            return emf;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
            return new JpaTransactionManager(emf);
        }
    }

    @Autowired private AuditEventRepository     auditEventRepository;
    @Autowired private ReasoningStepRepository  reasoningStepRepository;
    @Autowired private ToolCallRepository       toolCallRepository;
    @Autowired private LoanDecisionRepository   loanDecisionRepository;

    // ============================================================
    // AuditEvent
    // ============================================================

    @Test
    @DisplayName("PostgreSQL: should persist AuditEvent and assign a UUID primary key")
    void shouldPersistAuditEventWithGeneratedId() {
        UUID sessionId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();

        AuditEventEntity saved = auditEventRepository.save(
                new AuditEventEntity(sessionId, applicationId, "SESSION_START", null));

        assertThat(saved.getEventId()).isNotNull();
        assertThat(saved.getSessionId()).isEqualTo(sessionId);
        assertThat(saved.getApplicationId()).isEqualTo(applicationId);
        assertThat(saved.getEventType()).isEqualTo("SESSION_START");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("PostgreSQL: should retrieve audit events ordered by createdAt ascending")
    void shouldRetrieveAuditEventsOrderedByCreatedAt() throws InterruptedException {
        UUID sessionId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();

        // Save in order; tiny sleep ensures distinct timestamps on fast machines
        auditEventRepository.save(new AuditEventEntity(sessionId, applicationId, "SESSION_START", null));
        Thread.sleep(5);
        auditEventRepository.save(new AuditEventEntity(sessionId, applicationId, "TOOL_CALL", null));
        Thread.sleep(5);
        auditEventRepository.save(new AuditEventEntity(sessionId, applicationId, "DECISION", null));

        List<AuditEventEntity> events =
                auditEventRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId);

        assertThat(events).hasSize(3);
        assertThat(events.get(0).getEventType()).isEqualTo("SESSION_START");
        assertThat(events.get(1).getEventType()).isEqualTo("TOOL_CALL");
        assertThat(events.get(2).getEventType()).isEqualTo("DECISION");
    }

    @Test
    @DisplayName("PostgreSQL: countByApplicationId should return accurate event count")
    void shouldCountEventsByApplicationId() {
        UUID sessionId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();

        auditEventRepository.save(new AuditEventEntity(sessionId, applicationId, "SESSION_START", null));
        auditEventRepository.save(new AuditEventEntity(sessionId, applicationId, "REASONING_STEP", null));
        auditEventRepository.save(new AuditEventEntity(sessionId, applicationId, "SESSION_END", null));

        assertThat(auditEventRepository.countByApplicationId(applicationId)).isEqualTo(3);
    }

    // ============================================================
    // ReasoningStep
    // ============================================================

    @Test
    @DisplayName("PostgreSQL: should persist ReasoningStep linked to AuditEvent")
    void shouldPersistReasoningStepLinkedToEvent() {
        UUID sessionId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();

        AuditEventEntity event = auditEventRepository.save(
                new AuditEventEntity(sessionId, applicationId, "REASONING_STEP", null));

        ReasoningStepEntity step = new ReasoningStepEntity(
                event.getEventId(), 1,
                "I need to check the credit score",
                "getCreditScore",
                "Credit score is 720");

        ReasoningStepEntity saved = reasoningStepRepository.save(step);

        assertThat(saved.getStepId()).isNotNull();
        assertThat(saved.getEventId()).isEqualTo(event.getEventId());
        assertThat(saved.getStepNumber()).isEqualTo(1);
        assertThat(saved.getThought()).isEqualTo("I need to check the credit score");
        assertThat(saved.getAction()).isEqualTo("getCreditScore");
        assertThat(saved.getObservation()).isEqualTo("Credit score is 720");
    }

    @Test
    @DisplayName("PostgreSQL: should retrieve reasoning steps ordered by step number")
    void shouldRetrieveReasoningStepsOrderedByStepNumber() {
        UUID sessionId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();

        AuditEventEntity event = auditEventRepository.save(
                new AuditEventEntity(sessionId, applicationId, "REASONING_STEP", null));

        reasoningStepRepository.save(new ReasoningStepEntity(event.getEventId(), 3, "Step 3", "action3", "obs3"));
        reasoningStepRepository.save(new ReasoningStepEntity(event.getEventId(), 1, "Step 1", "action1", "obs1"));
        reasoningStepRepository.save(new ReasoningStepEntity(event.getEventId(), 2, "Step 2", "action2", "obs2"));

        List<ReasoningStepEntity> steps =
                reasoningStepRepository.findByEventIdInOrderByStepNumberAsc(List.of(event.getEventId()));

        assertThat(steps).hasSize(3);
        assertThat(steps.get(0).getStepNumber()).isEqualTo(1);
        assertThat(steps.get(1).getStepNumber()).isEqualTo(2);
        assertThat(steps.get(2).getStepNumber()).isEqualTo(3);
    }

    // ============================================================
    // ToolCall
    // ============================================================

    @Test
    @DisplayName("PostgreSQL: should persist ToolCall and complete it successfully")
    void shouldPersistAndCompleteToolCall() {
        UUID sessionId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();

        AuditEventEntity event = auditEventRepository.save(
                new AuditEventEntity(sessionId, applicationId, "TOOL_CALL", null));

        ToolCallEntity toolCall = toolCallRepository.save(
                new ToolCallEntity(event.getEventId(), "getCreditScore", "{\"customerId\":\"CUST001\"}"));

        assertThat(toolCall.getToolCallId()).isNotNull();
        assertThat(toolCall.isSuccess()).isTrue();

        // Simulate tool completion
        toolCall.complete("{\"score\":720,\"rating\":\"GOOD\"}", 47L);
        toolCallRepository.save(toolCall);

        ToolCallEntity retrieved = toolCallRepository.findById(toolCall.getToolCallId()).orElseThrow();
        assertThat(retrieved.getOutputResult()).isEqualTo("{\"score\":720,\"rating\":\"GOOD\"}");
        assertThat(retrieved.getExecutionTimeMs()).isEqualTo(47L);
        assertThat(retrieved.isSuccess()).isTrue();
        assertThat(retrieved.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("PostgreSQL: should persist ToolCall failure with error message")
    void shouldPersistToolCallFailure() {
        UUID sessionId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();

        AuditEventEntity event = auditEventRepository.save(
                new AuditEventEntity(sessionId, applicationId, "TOOL_CALL", null));

        ToolCallEntity toolCall = toolCallRepository.save(
                new ToolCallEntity(event.getEventId(), "getCreditScore", "{\"customerId\":\"CUST_ERR\"}"));

        toolCall.fail("Service unavailable", 3002L);
        toolCallRepository.save(toolCall);

        ToolCallEntity retrieved = toolCallRepository.findById(toolCall.getToolCallId()).orElseThrow();
        assertThat(retrieved.isSuccess()).isFalse();
        assertThat(retrieved.getErrorMessage()).isEqualTo("Service unavailable");
    }

    @Test
    @DisplayName("PostgreSQL: findByEventIdIn should retrieve all tool calls for given events")
    void shouldFindToolCallsByEventIds() {
        UUID sessionId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();

        AuditEventEntity e1 = auditEventRepository.save(new AuditEventEntity(sessionId, applicationId, "TOOL_CALL", null));
        AuditEventEntity e2 = auditEventRepository.save(new AuditEventEntity(sessionId, applicationId, "TOOL_CALL", null));

        toolCallRepository.save(new ToolCallEntity(e1.getEventId(), "getCreditScore", "{}"));
        toolCallRepository.save(new ToolCallEntity(e1.getEventId(), "verifyKYC", "{}"));
        toolCallRepository.save(new ToolCallEntity(e2.getEventId(), "calculateRiskScore", "{}"));

        List<ToolCallEntity> found = toolCallRepository.findByEventIdIn(
                List.of(e1.getEventId(), e2.getEventId()));

        assertThat(found).hasSize(3);
        assertThat(found).extracting(ToolCallEntity::getToolName)
                .containsExactlyInAnyOrder("getCreditScore", "verifyKYC", "calculateRiskScore");
    }

    // ============================================================
    // LoanDecision
    // ============================================================

    @Test
    @DisplayName("PostgreSQL: should persist LoanDecision and retrieve by applicationId")
    void shouldPersistAndRetrieveLoanDecision() {
        UUID sessionId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();

        LoanDecisionEntity decision = new LoanDecisionEntity(
                applicationId, sessionId, "APPROVED", "All criteria met — credit 720, low risk", 0.92);
        loanDecisionRepository.save(decision);

        Optional<LoanDecisionEntity> found = loanDecisionRepository.findByApplicationId(applicationId);

        assertThat(found).isPresent();
        assertThat(found.get().getDecisionId()).isNotNull();
        assertThat(found.get().getOutcome()).isEqualTo("APPROVED");
        assertThat(found.get().getReasoning()).isEqualTo("All criteria met — credit 720, low risk");
        assertThat(found.get().getConfidenceScore()).isNotNull();
        assertThat(found.get().getDecidedAt()).isNotNull();
    }

    @Test
    @DisplayName("PostgreSQL: existsByApplicationId should return false when no decision exists")
    void shouldReturnFalseWhenNoDecisionExists() {
        assertThat(loanDecisionRepository.existsByApplicationId(UUID.randomUUID())).isFalse();
    }

    // ============================================================
    // Immutability — no delete paths exposed to service layer
    // ============================================================

    @Test
    @DisplayName("PostgreSQL: repositories should not expose delete-all operations to service layer")
    void repositoriesShouldNotExposeUnsafeDeleteAll() {
        // Verify none of the repositories has a custom deleteAll override in their interface
        // (inherited JpaRepository.deleteAll exists but must never be called by AuditService)
        assertThat(AuditEventRepository.class.getDeclaredMethods())
                .as("AuditEventRepository must not declare any custom delete methods")
                .noneMatch(m -> m.getName().toLowerCase().contains("delete"));

        assertThat(ReasoningStepRepository.class.getDeclaredMethods())
                .noneMatch(m -> m.getName().toLowerCase().contains("delete"));

        assertThat(ToolCallRepository.class.getDeclaredMethods())
                .noneMatch(m -> m.getName().toLowerCase().contains("delete"));

        assertThat(LoanDecisionRepository.class.getDeclaredMethods())
                .noneMatch(m -> m.getName().toLowerCase().contains("delete"));
    }
}




