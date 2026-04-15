package com.bank.ata.agent.config;

import com.bank.ata.agent.tools.LoanEvaluationTools;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for agent tools.
 */
@Configuration
public class AgentConfig {

    @Bean
    public LoanEvaluationTools loanEvaluationTools() {
        return new LoanEvaluationTools();
    }
}

