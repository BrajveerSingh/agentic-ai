package com.bank.ata.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configuration for RestClient used by the gateway.
 */
@Configuration
public class RestClientConfig {

    /**
     * Provides the RestClient.Builder bean.
     * In Spring Boot 4.x, this might not be auto-configured for simple apps.
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}

