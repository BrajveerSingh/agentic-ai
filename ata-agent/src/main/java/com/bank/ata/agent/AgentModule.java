package com.bank.ata.agent;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Agent module configuration.
 * This module contains the LangChain4j agent and loan evaluation tools.
 */
@Configuration
@ComponentScan(basePackages = "com.bank.ata.agent")
public class AgentModule {
    // Configuration for agent components
}

