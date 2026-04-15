package com.bank.ata.agent.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies Ollama is reachable and the configured LangChain4j integration is functional.
 *
 * Tests use assumeTrue() to skip gracefully when Ollama is not running —
 * replacing the previous hard @Disabled approach that hid the connectivity status.
 *
 * How to enable:
 *   docker-compose up -d ollama
 *   docker-compose exec ollama ollama pull llama3:8b   (first time only)
 *
 * These tests are self-reporting: when skipped they print the reason
 * ("Ollama not running"), making the gap visible in CI reports rather than silent.
 */
class OllamaConnectivityTest {

    private static final String OLLAMA_BASE_URL = "http://localhost:11434";
    private static final String REQUIRED_MODEL   = "llama3:8b";

    private static boolean ollamaReachable = false;
    private static boolean modelAvailable  = false;

    @BeforeAll
    static void probeOllama() {
        try {
            HttpURLConnection conn = (HttpURLConnection)
                    new URL(OLLAMA_BASE_URL + "/api/tags").openConnection();
            conn.setConnectTimeout(2_000);
            conn.setReadTimeout(2_000);
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() == 200) {
                ollamaReachable = true;
                String body = new String(conn.getInputStream().readAllBytes());
                modelAvailable = body.contains(REQUIRED_MODEL);
            }
            conn.disconnect();
        } catch (Exception ignored) {
            // Ollama not running — all tests will be skipped via assumeTrue
        }
    }

    // ============================================================
    // Connectivity
    // ============================================================

    @Test
    @DisplayName("Ollama: HTTP /api/tags should return 200 (service is up)")
    void shouldRespondToHealthProbe() {
        assumeTrue(ollamaReachable,
                "Ollama not running at " + OLLAMA_BASE_URL + " — start with: docker-compose up -d ollama");

        assertThat(ollamaReachable).isTrue();
    }

    @Test
    @DisplayName("Ollama: /api/tags response should list at least one model")
    void shouldListAtLeastOneModel() throws Exception {
        assumeTrue(ollamaReachable,
                "Ollama not running — start with: docker-compose up -d ollama");

        HttpURLConnection conn = (HttpURLConnection)
                new URL(OLLAMA_BASE_URL + "/api/tags").openConnection();
        conn.setConnectTimeout(3_000);
        conn.setReadTimeout(3_000);
        String body = new String(conn.getInputStream().readAllBytes());
        conn.disconnect();

        assertThat(body)
                .as("Response from /api/tags")
                .contains("models");
    }

    @Test
    @DisplayName("Ollama: configured model '" + REQUIRED_MODEL + "' should be pulled and available")
    void requiredModelShouldBePulled() {
        assumeTrue(ollamaReachable,
                "Ollama not running — start with: docker-compose up -d ollama");
        assumeTrue(modelAvailable,
                "Model '" + REQUIRED_MODEL + "' not pulled — run: " +
                "docker-compose exec ollama ollama pull " + REQUIRED_MODEL);

        assertThat(modelAvailable).isTrue();
    }

    // ============================================================
    // LangChain4j Integration
    // ============================================================

    @Test
    @DisplayName("Ollama + LangChain4j: OllamaChatModel should generate a non-blank response")
    void shouldGenerateResponseViaLangChain4j() {
        assumeTrue(ollamaReachable,
                "Ollama not running — start with: docker-compose up -d ollama");
        assumeTrue(modelAvailable,
                "Model '" + REQUIRED_MODEL + "' not pulled");

        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(OLLAMA_BASE_URL)
                .modelName(REQUIRED_MODEL)
                .timeout(Duration.ofSeconds(60))
                .temperature(0.0)
                .build();

        String response = model.generate("Reply with exactly one word: HEALTHY");

        assertThat(response)
                .as("LangChain4j OllamaChatModel response")
                .isNotBlank();
        assertThat(response.toUpperCase()).contains("HEALTHY");
    }

    @Test
    @DisplayName("Ollama + LangChain4j: loan evaluation prompt should produce a structured decision")
    void shouldProduceLoanDecisionFormat() {
        assumeTrue(ollamaReachable,
                "Ollama not running — start with: docker-compose up -d ollama");
        assumeTrue(modelAvailable,
                "Model '" + REQUIRED_MODEL + "' not pulled");

        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(OLLAMA_BASE_URL)
                .modelName(REQUIRED_MODEL)
                .timeout(Duration.ofSeconds(90))
                .temperature(0.0)
                .build();

        String prompt = """
                You are a loan evaluation assistant.
                A customer has credit score 750, verified KYC, requesting $40,000 for home improvement.
                Respond in this exact format only:
                DECISION: APPROVED
                CONFIDENCE: 0.9
                REASONING: [one sentence]
                """;

        String response = model.generate(prompt);

        assertThat(response)
                .as("Loan evaluation response from Ollama")
                .isNotBlank()
                .containsIgnoringCase("DECISION");
    }
}

