package com.bank.ata.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * External-facing REST gateway.
 *
 * <p>In the distributed architecture this app exposes the public REST API and
 * forwards loan evaluation requests to the orchestrator service.</p>
 */
@SpringBootApplication(scanBasePackages = "com.bank.ata.gateway")
public class AtaGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(AtaGatewayApplication.class, args);
    }
}

