package com.bank.ata;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Verifies that the Spring application context loads without errors.
 *
 * This test does NOT require Ollama — the OllamaChatModel bean is created lazily
 * and no network calls are made during context startup. H2 is used as the
 * in-memory database for the "dev" profile.
 */
@SpringBootTest
@ActiveProfiles("dev")
class AuditTrailAgentApplicationTest {

    @Test
    @DisplayName("Spring context should load without errors (no Ollama needed)")
    void contextLoads() {
        // If we reach here, the context loaded successfully
        assertThatNoException().isThrownBy(() -> { /* context loaded */ });
    }
}
