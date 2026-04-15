package com.bank.ata.agent;

import com.bank.ata.agent.tools.LoanEvaluationTools;
import com.bank.ata.core.domain.DecisionOutcome;
import com.bank.ata.core.domain.LoanApplication;
import com.bank.ata.core.domain.LoanDecision;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Core AI Agent for loan evaluation.
 * Uses LangChain4j to orchestrate tool calls and reasoning.
 */
@Service
public class AuditTrailAgent {

    private static final Logger log = LoggerFactory.getLogger(AuditTrailAgent.class);

    private final ChatLanguageModel chatModel;
    private final LoanEvaluationTools tools;
    private LoanAssistant assistant;

    // System prompt that defines the agent's behavior
    private static final String SYSTEM_PROMPT = """
        You are an AI loan evaluation assistant for a bank. Your role is to evaluate loan applications 
        by gathering information and making fair, transparent decisions.
        
        EVALUATION PROCESS:
        1. First, get the customer's credit score using the getCreditScore tool
        2. Get the customer's employment information using getEmploymentInfo tool
        3. Verify the customer's KYC status using verifyKYC tool
        4. Check policy compliance using checkPolicyCompliance tool with the gathered information
        5. Calculate the risk score using calculateRiskScore tool
        6. Based on all gathered information, make a final decision
        
        DECISION CRITERIA:
        - APPROVED: Credit score >= 650, KYC verified and current, risk score < 0.5, all policies compliant
        - PENDING_REVIEW: Risk score between 0.5-0.7, or minor policy issues
        - REQUIRES_ADDITIONAL_INFO: KYC not current, missing employment info
        - REJECTED: Credit score < 600, risk score > 0.7, or major policy violations
        
        RESPONSE FORMAT:
        After gathering all information, provide your decision in this exact format:
        
        DECISION: [APPROVED/REJECTED/PENDING_REVIEW/REQUIRES_ADDITIONAL_INFO]
        CONFIDENCE: [0.0-1.0]
        REASONING: [Detailed explanation citing specific data points]
        
        Always use the tools to gather real data. Never make up information.
        Be thorough but concise in your reasoning.
        """;

    public AuditTrailAgent(ChatLanguageModel chatModel, LoanEvaluationTools tools) {
        this.chatModel = chatModel;
        this.tools = tools;
    }

    @PostConstruct
    public void init() {
        log.info("Initializing AuditTrailAgent with LangChain4j...");

        // Build the AI assistant with tools and memory
        this.assistant = AiServices.builder(LoanAssistant.class)
                .chatLanguageModel(chatModel)
                .tools(tools)
                .chatMemory(MessageWindowChatMemory.builder()
                        .maxMessages(20)
                        .build())
                .systemMessageProvider(chatMemoryId -> SYSTEM_PROMPT)
                .build();

        log.info("AuditTrailAgent initialized successfully");
    }

    /**
     * Evaluate a loan application.
     *
     * @param application The loan application to evaluate
     * @return The loan decision with reasoning
     */
    public LoanDecision evaluateLoan(LoanApplication application) {
        log.info("Starting loan evaluation for application: {}", application.applicationId());

        String prompt = buildEvaluationPrompt(application);
        log.debug("Evaluation prompt: {}", prompt);

        // Call the AI assistant
        String response = assistant.chat(prompt);
        log.debug("AI response: {}", response);

        // Parse the response into a LoanDecision
        LoanDecision decision = parseDecision(application.applicationId(), response);

        log.info("Loan evaluation complete: applicationId={}, outcome={}",
                application.applicationId(), decision.outcome());

        return decision;
    }

    /**
     * Build the evaluation prompt for a loan application.
     */
    private String buildEvaluationPrompt(LoanApplication application) {
        return String.format("""
            Please evaluate the following loan application:
            
            Application ID: %s
            Customer ID: %s
            Loan Amount: $%s
            Purpose: %s
            Loan Type: %s
            Application Date: %s
            
            Please use the available tools to:
            1. Get the customer's credit score
            2. Get employment information
            3. Verify KYC status
            4. Check policy compliance
            5. Calculate risk score
            
            Then provide your final decision with reasoning.
            """,
                application.applicationId(),
                application.customerId(),
                application.amount(),
                application.purpose(),
                application.loanType(),
                application.applicationDate()
        );
    }

    /**
     * Parse the AI response into a LoanDecision.
     */
    private LoanDecision parseDecision(UUID applicationId, String response) {
        DecisionOutcome outcome = parseOutcome(response);
        double confidence = parseConfidence(response);
        String reasoning = parseReasoning(response);

        return LoanDecision.create(applicationId, outcome, reasoning, confidence);
    }

    private DecisionOutcome parseOutcome(String response) {
        String upperResponse = response.toUpperCase();

        // Look for explicit DECISION: pattern first
        Pattern pattern = Pattern.compile("DECISION:\\s*(APPROVED|REJECTED|PENDING_REVIEW|REQUIRES_ADDITIONAL_INFO)");
        Matcher matcher = pattern.matcher(upperResponse);

        if (matcher.find()) {
            return DecisionOutcome.valueOf(matcher.group(1));
        }

        // Fallback: look for keywords in the response
        if (upperResponse.contains("APPROVED") && !upperResponse.contains("NOT APPROVED")) {
            return DecisionOutcome.APPROVED;
        } else if (upperResponse.contains("REJECTED") || upperResponse.contains("DENY") || upperResponse.contains("DECLINE")) {
            return DecisionOutcome.REJECTED;
        } else if (upperResponse.contains("PENDING") || upperResponse.contains("REVIEW")) {
            return DecisionOutcome.PENDING_REVIEW;
        } else if (upperResponse.contains("ADDITIONAL") || upperResponse.contains("MORE INFO")) {
            return DecisionOutcome.REQUIRES_ADDITIONAL_INFO;
        }

        // Default to pending review if we can't determine
        log.warn("Could not parse decision outcome from response, defaulting to PENDING_REVIEW");
        return DecisionOutcome.PENDING_REVIEW;
    }

    private double parseConfidence(String response) {
        Pattern pattern = Pattern.compile("CONFIDENCE:\\s*([0-9]*\\.?[0-9]+)");
        Matcher matcher = pattern.matcher(response.toUpperCase());

        if (matcher.find()) {
            try {
                double confidence = Double.parseDouble(matcher.group(1));
                return Math.max(0, Math.min(1, confidence));
            } catch (NumberFormatException e) {
                log.warn("Could not parse confidence value: {}", matcher.group(1));
            }
        }

        // Default confidence based on decision clarity
        return 0.75;
    }

    private String parseReasoning(String response) {
        // Try to extract REASONING section
        Pattern pattern = Pattern.compile("REASONING:\\s*(.+?)(?=DECISION:|CONFIDENCE:|$)",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(response);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // If no explicit REASONING section, use the whole response
        // Remove DECISION and CONFIDENCE lines
        String reasoning = response
                .replaceAll("(?i)DECISION:.*?\\n", "")
                .replaceAll("(?i)CONFIDENCE:.*?\\n", "")
                .trim();

        return reasoning.isEmpty() ? response : reasoning;
    }

    /**
     * Simple chat method for testing.
     */
    public String chat(String message) {
        return assistant.chat(message);
    }
}

