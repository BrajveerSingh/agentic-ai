package com.bank.ata.a2a.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Spring configuration for A2A outbound clients.
 *
 * <p>Uses the modern {@link RestClient} (replacing deprecated {@code RestTemplate}).
 * Two {@link A2aClient} beans are wired — one per peer agent — using URLs
 * from {@code application.yml} (or the dev override in {@code application-dev.yml}).</p>
 *
 * <pre>
 * a2a:
 *   client:
 *     risk-agent:
 *       url: http://localhost:8080/mock/risk-agent/tasks/send
 *     fraud-agent:
 *       url: http://localhost:8080/mock/fraud-agent/tasks/send
 * </pre>
 */
@Configuration
public class A2aClientConfig {

    /**
     * RestClient.Builder bean - provides a builder for RestClient.
     * In Spring Boot 4.x, this should be auto-configured, but we provide
     * it explicitly for reliability across different module setups.
     */
    @Bean
    @ConditionalOnMissingBean(RestClient.Builder.class)
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    /**
     * Shared {@link RestClient} for all A2A outbound calls.
     * 5-second connect timeout, 30-second read timeout.
     */
    @Bean("a2aRestClient")
    public RestClient a2aRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);   // ms
        factory.setReadTimeout(30_000);     // ms
        return builder.requestFactory(factory).build();
    }

    @Bean("riskAgentClient")
    public A2aClient riskAgentClient(
            @Qualifier("a2aRestClient") RestClient a2aRestClient,
            @Value("${a2a.client.risk-agent.url:http://localhost:8080/mock/risk-agent/tasks/send}")
            String url) {
        return new A2aClient(a2aRestClient, url, "risk-agent");
    }

    @Bean("fraudAgentClient")
    public A2aClient fraudAgentClient(
            @Qualifier("a2aRestClient") RestClient a2aRestClient,
            @Value("${a2a.client.fraud-agent.url:http://localhost:8080/mock/fraud-agent/tasks/send}")
            String url) {
        return new A2aClient(a2aRestClient, url, "fraud-agent");
    }
}
