package com.bank.ata.agent;

import com.bank.ata.core.domain.DecisionOutcome;
import com.bank.ata.core.domain.LoanApplication;
import com.bank.ata.core.domain.LoanDecision;

/**
 * Interface for the AI assistant that evaluates loan applications.
 * This will be implemented by LangChain4j AiServices.
 */
public interface LoanAssistant {

    /**
     * Evaluate a loan application and provide a decision with reasoning.
     * The assistant will use available tools to gather information and make a decision.
     *
     * @param prompt The evaluation prompt containing loan details
     * @return The assistant's response with decision and reasoning
     */
    String chat(String prompt);
}

