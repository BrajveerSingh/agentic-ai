package com.bank.ata.mcp;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration entry-point for the MCP module (Phase 3).
 * Scans both {@code com.bank.ata.mcp} and the {@code server} sub-package.
 */
@Configuration
@ComponentScan(basePackages = {"com.bank.ata.mcp", "com.bank.ata.mcp.server"})
public class McpModule {
}
