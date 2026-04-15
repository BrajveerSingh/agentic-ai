package com.bank.ata.mockcreditbureau;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Standalone Spring Boot application that exposes the Mock Credit Bureau MCP endpoints.
 */
@SpringBootApplication(scanBasePackages = {
        "com.bank.ata.mockcreditbureau",
        "com.bank.ata.mcp.mock" // MockCreditBureauController lives in ata-mcp
})
public class MockCreditBureauApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockCreditBureauApplication.class, args);
    }
}

