package com.bank.ata.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple unit tests for controller logic.
 */
class HealthControllerTest {

    private final HealthController controller = new HealthController();

    @Test
    void shouldReturnHealthStatus() {
        var response = controller.health();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("status", "UP");
        assertThat(response.getBody()).containsEntry("application", "Audit Trail Agent");
    }

    @Test
    void shouldReturnApplicationInfo() {
        var response = controller.info();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("name", "Audit Trail Agent");
        assertThat(response.getBody()).containsEntry("version", "1.0.0-SNAPSHOT");
    }
}
