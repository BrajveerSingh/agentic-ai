package com.bank.ata.mcp.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Spring configuration for the MCP client(s) used by ATA to call external MCP servers.
 *
 * <p>Uses the modern {@link RestClient} (replacing deprecated {@code RestTemplate}).</p>
 *
 * <h3>Credit Bureau MCP client</h3>
 * <p>In development the client points at the built-in mock credit bureau endpoint
 * ({@code /mock/credit-bureau-mcp/message} on the same application). In production
 * set {@code mcp.client.credit-bureau.url} to the real service URL.</p>
 */
@Configuration
public class McpClientConfig {

    private static final Logger log = LoggerFactory.getLogger(McpClientConfig.class);

    /**
     * RestClient.Builder bean - provides a builder for RestClient.
     */
    @Bean
    @ConditionalOnMissingBean(RestClient.Builder.class)
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    /**
     * Shared {@link RestClient} for outbound MCP calls.
     * 5-second connect timeout, 60-second read timeout (tool calls can be slow).
     */
    @Bean("mcpRestClient")
    public RestClient mcpRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);   // 5 s
        factory.setReadTimeout(60_000);     // 60 s
        return builder.requestFactory(factory).build();
    }

    /**
     * MCP client wired to the Credit Bureau MCP server.
     *
     * <p>The client is <em>not</em> initialised eagerly — call
     * {@link McpClientService#initialize()} before the first tool call.</p>
     *
     * @param url           override via {@code mcp.client.credit-bureau.url}
     * @param mcpRestClient the dedicated RestClient bean
     */
    @Bean("creditBureauMcpClient")
    public McpClientService creditBureauMcpClient(
            @Value("${mcp.client.credit-bureau.url:http://localhost:8080/mock/credit-bureau-mcp/message}")
            String url,
            @Qualifier("mcpRestClient") RestClient mcpRestClient) {

        log.info("Creating MCP client for Credit Bureau: url={}", url);
        return new McpClientService(mcpRestClient, url, "credit-bureau-mcp");
    }
}
