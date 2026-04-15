package com.bank.ata.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Orchestrator runtime: primary agent, audit persistence, and outbound A2A/MCP clients.
 */
@SpringBootApplication(scanBasePackages = {
        "com.bank.ata.orchestrator",
        "com.bank.ata.agent",
        "com.bank.ata.audit",
        "com.bank.ata.mcp",
        "com.bank.ata.a2a"
})
public class AtaOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AtaOrchestratorApplication.class, args);
    }
}

