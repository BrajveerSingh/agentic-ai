package com.bank.ata.agent.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Duration;

/**
 * Configuration for LLM backends.
 * Uses Ollama for development, vLLM (OpenAI-compatible) for production.
 */
@Configuration
public class LlmConfig {

    /**
     * Development configuration using local Ollama.
     */
    @Bean
    @Profile({"dev", "default", "compose"})
    public ChatLanguageModel ollamaModel(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.model:llama3:8b}") String modelName,
            @Value("${ollama.timeout:120}") int timeoutSeconds) {

        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .temperature(0.1)  // Low temperature for deterministic responses
                .build();
    }

    /**
     * Production configuration using vLLM with OpenAI-compatible API.
     */
    @Bean
    @Profile("prod")
    public ChatLanguageModel vllmModel(
            @Value("${vllm.base-url}") String baseUrl,
            @Value("${vllm.model:meta-llama/Llama-3-70B-Instruct}") String modelName,
            @Value("${vllm.timeout:60}") int timeoutSeconds) {

        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey("not-required")  // vLLM doesn't require API key
                .modelName(modelName)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .temperature(0.1)
                .maxTokens(4096)
                .build();
    }
}

