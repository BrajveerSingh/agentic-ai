# Audit Trail Agent (ATA) - Development Plan

## Executive Summary

This document outlines a **phase-wise incremental development plan** for the Audit Trail Agent. Each phase is **independently testable** and delivers working functionality that builds toward the complete system.

**Total Duration**: 20-24 weeks (5-6 months)  
**Team Size**: 3-5 developers + 1 ML engineer + 1 DevOps

---

## Development Phases Overview

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                           DEVELOPMENT TIMELINE                                           │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│  Week  1  2  3  4  5  6  7  8  9  10 11 12 13 14 15 16 17 18 19 20 21 22 23 24        │
│        ├──────────┼──────────┼──────────┼──────────┼──────────┼──────────┼──────────┤  │
│        │ Phase 0  │ Phase 1  │ Phase 2  │ Phase 3  │ Phase 4  │ Phase 5  │ Phase 6  │  │
│        │  Setup   │  Core    │  Audit   │   MCP    │   A2A    │  Prod    │  Cloud   │  │
│        │  (2wk)   │  (4wk)   │  (3wk)   │  (3wk)   │  (3wk)   │  (4wk)   │  (3wk)   │  │
│        └──────────┴──────────┴──────────┴──────────┴──────────┴──────────┴──────────┘  │
│                                                                                         │
│  Milestones:                                                                           │
│  M1 (Week 2)  - Dev environment ready                                                 │
│  M2 (Week 6)  - Basic agent working with Ollama                                       │
│  M3 (Week 9)  - Full audit trail with database                                        │
│  M4 (Week 12) - MCP server/client operational                                         │
│  M5 (Week 15) - Multi-agent collaboration working                                     │
│  M6 (Week 19) - Production-ready with vLLM                                            │
│  M7 (Week 22) - Cloud deployment complete                                             │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Phase 0: Project Setup & Infrastructure (Weeks 1-2)

### Objectives
- Set up development environment
- Establish project structure and coding standards
- Configure CI/CD pipeline
- Provision local Ollama for development

### Features & Components

| Component | Description | Priority |
|-----------|-------------|----------|
| Project skeleton | Spring Boot 3.x + Java 25 | P0 |
| Build system | Maven/Gradle with multi-module | P0 |
| CI/CD | GitHub Actions / Jenkins | P0 |
| Local LLM | Ollama + Llama 3 8B | P0 |
| Dev database | H2 in-memory | P0 |
| Code quality | SonarQube, Checkstyle | P1 |
| Documentation | ADR templates, README | P1 |

### Deliverables

```
audit-trail-agent/
├── pom.xml (or build.gradle.kts)
├── docker-compose.yml          # Local dev environment
├── .github/
│   └── workflows/
│       └── ci.yml              # CI pipeline
├── docs/
│   ├── adr/                    # Architecture Decision Records
│   └── setup.md                # Dev setup guide
├── ata-core/                   # Core module
│   └── src/main/java/
│       └── com/bank/ata/
│           └── Application.java
├── ata-agent/                  # Agent module (empty for now)
├── ata-audit/                  # Audit module (empty for now)
├── ata-mcp/                    # MCP module (empty for now)
└── ata-a2a/                    # A2A module (empty for now)
```

### Test Plan

| Test Type | Scope | Pass Criteria |
|-----------|-------|---------------|
| Build | Full project | `mvn clean install` succeeds |
| Ollama connectivity | Health check | Can call Ollama API |
| CI pipeline | Push to main | All stages pass |
| Code quality | SonarQube | No blockers, 80% coverage gate |

### Acceptance Criteria

- [ ] Project builds successfully with Java 25
- [ ] Ollama running locally with Llama 3 8B
- [ ] CI pipeline runs on every push
- [ ] Team can clone and run in <15 minutes
- [ ] ADR-001: Technology choices documented

### Commands to Verify

```bash
# Clone and build
git clone git@github.com:bank/audit-trail-agent.git
cd audit-trail-agent
mvn clean install -DskipTests

# Start local environment
docker-compose up -d

# Verify Ollama
curl http://localhost:11434/api/tags

# Run tests
mvn test
```

---

## Phase 1: Core Agent with Basic Tools (Weeks 3-6)

### Objectives
- Implement basic LangChain4j agent
- Create first set of loan evaluation tools
- Enable simple loan decision flow
- Test agent reasoning with Ollama

### Features & Components

| Component | Description | Priority |
|-----------|-------------|----------|
| LangChain4j integration | Chat model + agent | P0 |
| Loan evaluation tool | getCreditScore() | P0 |
| Policy compliance tool | checkPolicyCompliance() | P0 |
| KYC verification tool | verifyKYC() | P0 |
| Risk calculation tool | calculateRiskScore() | P0 |
| Agent orchestrator | ReAct loop implementation | P0 |
| Conversation memory | Message history management | P1 |
| REST API | /api/loans/evaluate endpoint | P0 |

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      PHASE 1 ARCHITECTURE                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────┐     ┌─────────────────────────────────────┐   │
│  │   REST API  │────▶│         Agent Orchestrator          │   │
│  │  /evaluate  │     │  • ReAct Loop                       │   │
│  └─────────────┘     │  • Tool Selection                   │   │
│                      │  • Response Generation              │   │
│                      └──────────────┬──────────────────────┘   │
│                                     │                          │
│                      ┌──────────────┴──────────────┐          │
│                      ▼                             ▼          │
│               ┌─────────────┐              ┌─────────────┐    │
│               │ Tool Registry│              │   Ollama    │    │
│               │ • Credit     │              │  Llama 3 8B │    │
│               │ • KYC        │              └─────────────┘    │
│               │ • Policy     │                                 │
│               │ • Risk       │                                 │
│               └─────────────┘                                  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Key Implementation

```java
// LoanEvaluationTools.java
@Component
public class LoanEvaluationTools {

    @Tool("Get credit score for a customer")
    public CreditScore getCreditScore(
            @P("Customer ID") String customerId) {
        // Mock implementation for Phase 1
        return new CreditScore(customerId, 720, "GOOD");
    }

    @Tool("Check if loan application complies with bank policies")
    public PolicyResult checkPolicyCompliance(
            @P("Loan amount") BigDecimal amount,
            @P("Loan purpose") String purpose,
            @P("Customer credit score") int creditScore) {
        // Policy rules implementation
        boolean compliant = creditScore >= 650 && amount.compareTo(MAX_LOAN) <= 0;
        return new PolicyResult(compliant, List.of("Rule 1: OK", "Rule 2: OK"));
    }

    @Tool("Verify customer KYC status")
    public KycResult verifyKYC(@P("Customer ID") String customerId) {
        return new KycResult(customerId, true, LocalDate.now().minusMonths(6));
    }

    @Tool("Calculate risk score for loan application")
    public RiskScore calculateRiskScore(
            @P("Credit score") int creditScore,
            @P("Loan amount") BigDecimal amount,
            @P("Employment years") int employmentYears) {
        double risk = calculateRisk(creditScore, amount, employmentYears);
        return new RiskScore(risk, risk < 0.3 ? "LOW" : risk < 0.6 ? "MEDIUM" : "HIGH");
    }
}

// AuditTrailAgent.java
@Service
public class AuditTrailAgent {

    private final Assistant assistant;

    public AuditTrailAgent(ChatLanguageModel model, LoanEvaluationTools tools) {
        this.assistant = AiServices.builder(Assistant.class)
            .chatLanguageModel(model)
            .tools(tools)
            .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
            .build();
    }

    public LoanDecision evaluateLoan(LoanApplication application) {
        String prompt = buildEvaluationPrompt(application);
        String response = assistant.chat(prompt);
        return parseDecision(response);
    }
}
```

### Test Plan

| Test Type | Scope | Pass Criteria |
|-----------|-------|---------------|
| Unit tests | Each tool | All tools return expected format |
| Integration | Agent + Ollama | Agent completes reasoning loop |
| API test | POST /evaluate | Returns valid LoanDecision |
| Tool chain | Multi-tool scenario | Agent calls tools in logical order |

### Test Cases

```java
@SpringBootTest
class AgentIntegrationTest {

    @Test
    void shouldApproveSimpleLoan() {
        // Given: Good credit, reasonable amount
        LoanApplication app = new LoanApplication(
            "CUST001", new BigDecimal("50000"), "HOME_IMPROVEMENT");

        // When
        LoanDecision decision = agent.evaluateLoan(app);

        // Then
        assertThat(decision.outcome()).isEqualTo(DecisionOutcome.APPROVED);
        assertThat(decision.reasoning()).contains("credit score");
    }

    @Test
    void shouldRejectHighRiskLoan() {
        // Given: Low credit, high amount
        LoanApplication app = new LoanApplication(
            "CUST002", new BigDecimal("500000"), "SPECULATION");

        // When
        LoanDecision decision = agent.evaluateLoan(app);

        // Then
        assertThat(decision.outcome()).isEqualTo(DecisionOutcome.REJECTED);
    }

    @Test
    void shouldCallMultipleTools() {
        // Verify agent uses getCreditScore, checkPolicy, calculateRisk
        LoanApplication app = new LoanApplication(...);
        
        LoanDecision decision = agent.evaluateLoan(app);
        
        // Verify via logs or mock verification
        verify(toolRegistry, atLeast(3)).invokeTool(any(), any());
    }
}
```

### Acceptance Criteria

- [ ] Agent successfully reasons through loan evaluation
- [ ] All 4 tools callable and return valid responses
- [ ] REST API /api/loans/evaluate returns LoanDecision
- [ ] Agent handles edge cases gracefully
- [ ] Response time < 30 seconds on local Ollama
- [ ] 80% test coverage on core module

---

## Phase 2: Audit Trail & Persistence (Weeks 7-9)

### Objectives
- Implement immutable audit logging
- Add PostgreSQL persistence
- Intercept all tool calls and reasoning steps
- Create audit retrieval API

### Features & Components

| Component | Description | Priority |
|-----------|-------------|----------|
| PostgreSQL schema | All audit tables | P0 |
| AuditInterceptor | AOP-based interception | P0 |
| AuditService | Logging service | P0 |
| Reasoning step capture | Thought/Action/Observation | P0 |
| Tool call logging | Input/Output/Duration | P0 |
| Decision logging | Final decision + reasoning | P0 |
| Audit retrieval API | GET /api/audit/{appId} | P0 |
| JVector integration | Vector store setup | P1 |

### Database Schema

```sql
-- Phase 2 Database Setup
-- Run: docker-compose up -d postgres

CREATE TABLE loan_application (
    application_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id VARCHAR(50) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    purpose VARCHAR(500),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE audit_event (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL,
    application_id UUID REFERENCES loan_application(application_id),
    event_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reasoning_step (
    step_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES audit_event(event_id),
    step_number INTEGER NOT NULL,
    thought TEXT,
    action TEXT,
    observation TEXT
);

CREATE TABLE tool_call (
    tool_call_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES audit_event(event_id),
    tool_name VARCHAR(100) NOT NULL,
    input_params JSONB,
    output_result JSONB,
    execution_time_ms BIGINT,
    success BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE loan_decision (
    decision_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL REFERENCES loan_application(application_id),
    outcome VARCHAR(50) NOT NULL,
    reasoning TEXT,
    confidence_score DECIMAL(5, 4),
    decided_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
```

### Key Implementation

```java
// AuditInterceptor.java
@Aspect
@Component
public class AuditInterceptor {

    @Autowired
    private AuditService auditService;

    @Around("@annotation(Tool)")
    public Object interceptToolCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String toolName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();
        
        // Log BEFORE execution (immutable audit)
        ToolCallEvent event = auditService.logToolCallStart(
            toolName, 
            args, 
            getCurrentSessionId()
        );
        
        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            
            // Log success
            auditService.logToolCallComplete(event.getId(), result, duration);
            return result;
            
        } catch (Exception e) {
            // Log failure
            auditService.logToolCallError(event.getId(), e);
            throw e;
        }
    }
}

// AuditService.java
@Service
@Transactional
public class AuditService {

    public void logReasoningStep(UUID sessionId, int stepNumber, 
                                  String thought, String action, String observation) {
        AuditEvent event = createEvent(sessionId, "REASONING_STEP");
        auditEventRepository.save(event);
        
        ReasoningStep step = new ReasoningStep(
            event.getId(), stepNumber, thought, action, observation);
        reasoningStepRepository.save(step);
    }

    public AuditReport getAuditTrail(UUID applicationId) {
        List<AuditEvent> events = auditEventRepository
            .findByApplicationIdOrderByCreatedAt(applicationId);
        
        List<ReasoningStep> steps = reasoningStepRepository
            .findByEventIdIn(events.stream().map(AuditEvent::getId).toList());
        
        List<ToolCall> toolCalls = toolCallRepository
            .findByEventIdIn(events.stream().map(AuditEvent::getId).toList());
        
        return new AuditReport(applicationId, events, steps, toolCalls);
    }
}
```

### Test Plan

| Test Type | Scope | Pass Criteria |
|-----------|-------|---------------|
| Unit tests | AuditService | All audit methods work |
| Integration | Full flow | Audit captured for loan eval |
| Data integrity | Immutability | Cannot modify past audits |
| API test | GET /api/audit | Returns complete trail |
| Performance | 100 concurrent | No audit data loss |

### Test Cases

```java
@SpringBootTest
class AuditTrailTest {

    @Test
    void shouldCaptureAllToolCalls() {
        // Given
        LoanApplication app = createApplication();
        
        // When
        agent.evaluateLoan(app);
        
        // Then
        AuditReport report = auditService.getAuditTrail(app.getId());
        assertThat(report.getToolCalls()).hasSizeGreaterThan(0);
        assertThat(report.getToolCalls())
            .extracting(ToolCall::getToolName)
            .contains("getCreditScore", "checkPolicyCompliance");
    }

    @Test
    void shouldCaptureReasoningSteps() {
        LoanApplication app = createApplication();
        
        agent.evaluateLoan(app);
        
        AuditReport report = auditService.getAuditTrail(app.getId());
        assertThat(report.getReasoningSteps()).isNotEmpty();
        assertThat(report.getReasoningSteps().get(0).getThought()).isNotBlank();
    }

    @Test
    void auditShouldBeImmutable() {
        LoanApplication app = createApplication();
        agent.evaluateLoan(app);
        
        // Attempt to delete should fail
        assertThatThrownBy(() -> auditService.deleteAuditTrail(app.getId()))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
```

### Acceptance Criteria

- [ ] All tool calls logged with input/output
- [ ] Reasoning steps captured (thought/action/observation)
- [ ] PostgreSQL persistence working
- [ ] GET /api/audit/{id} returns complete audit trail
- [ ] Audit data cannot be deleted or modified
- [ ] 100% of loan evaluations have audit records

---

## Phase 3: MCP Integration (Weeks 10-12)

### Objectives
- Implement MCP Server to expose ATA tools
- Implement MCP Client to consume external data
- Enable LLM clients to use ATA via MCP
- Test with Claude Desktop / other MCP clients

### Features & Components

| Component | Description | Priority |
|-----------|-------------|----------|
| MCP Server | Expose tools via MCP | P0 |
| MCP tools | evaluateLoan, getCreditScore, etc. | P0 |
| MCP resources | loan://policies, audit://trail | P1 |
| MCP Client | Connect to external MCP servers | P0 |
| Credit Bureau MCP | Mock external credit service | P1 |
| Authentication | OAuth 2.0 for MCP | P0 |
| Transport | HTTP/SSE with mTLS | P0 |

### Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        PHASE 3 ARCHITECTURE                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────┐         ┌─────────────────────────────────────────┐   │
│  │  Claude Desktop │────────▶│           ATA MCP SERVER                │   │
│  │  (MCP Client)   │   MCP   │  ┌─────────────────────────────────┐   │   │
│  └─────────────────┘         │  │  Tools:                          │   │   │
│                              │  │  • evaluate_loan                 │   │   │
│  ┌─────────────────┐         │  │  • get_credit_score              │   │   │
│  │   IDE Plugin    │────────▶│  │  • check_compliance              │   │   │
│  │  (MCP Client)   │   MCP   │  │  • get_audit_trail               │   │   │
│  └─────────────────┘         │  └─────────────────────────────────┘   │   │
│                              │  ┌─────────────────────────────────┐   │   │
│                              │  │  Resources:                      │   │   │
│                              │  │  • loan://policies/{id}          │   │   │
│                              │  │  • audit://trail/{id}            │   │   │
│                              │  └─────────────────────────────────┘   │   │
│                              └─────────────────────────────────────────┘   │
│                                              │                              │
│                                              │ Uses                         │
│                                              ▼                              │
│                              ┌─────────────────────────────────────────┐   │
│                              │           ATA MCP CLIENT                │   │
│                              │  Connects to:                           │   │
│                              │  • Credit Bureau MCP Server             │   │
│                              │  • KYC Provider MCP Server              │   │
│                              └─────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Key Implementation

```java
// McpServerConfig.java
@Configuration
public class McpServerConfig {

    @Bean
    public McpServer mcpServer(LoanToolProvider toolProvider) {
        return McpServer.builder()
            .name("audit-trail-agent")
            .version("1.0.0")
            .capabilities(Capabilities.builder()
                .tools(true)
                .resources(true)
                .build())
            .tools(List.of(
                Tool.builder()
                    .name("evaluate_loan")
                    .description("Evaluate a loan application with full audit trail")
                    .inputSchema(LoanEvaluationInput.schema())
                    .handler(toolProvider::evaluateLoan)
                    .build(),
                Tool.builder()
                    .name("get_audit_trail")
                    .description("Retrieve audit trail for a loan application")
                    .inputSchema(AuditTrailInput.schema())
                    .handler(toolProvider::getAuditTrail)
                    .build()
            ))
            .resources(List.of(
                Resource.builder()
                    .uri("loan://policies/{policyId}")
                    .name("Loan Policy Document")
                    .handler(resourceProvider::getPolicy)
                    .build()
            ))
            .transport(HttpTransport.builder()
                .port(3000)
                .path("/mcp")
                .build())
            .build();
    }
}

// McpClientConfig.java
@Configuration
@Profile("prod")
public class McpClientConfig {

    @Bean
    public McpClient creditBureauClient() {
        return McpClient.builder()
            .serverUrl("http://credit-bureau-mcp:3000/mcp")
            .authentication(OAuth2.clientCredentials()
                .tokenUrl("https://auth.bank.internal/token")
                .clientId("ata-agent")
                .clientSecret("${CREDIT_MCP_SECRET}")
                .build())
            .build();
    }
}
```

### MCP Server Manifest

```json
{
  "name": "audit-trail-agent",
  "version": "1.0.0",
  "description": "Bank-grade loan evaluation agent with immutable audit trail",
  "tools": [
    {
      "name": "evaluate_loan",
      "description": "Evaluate a loan application with full audit trail",
      "inputSchema": {
        "type": "object",
        "properties": {
          "customerId": { "type": "string" },
          "amount": { "type": "number" },
          "purpose": { "type": "string" }
        },
        "required": ["customerId", "amount", "purpose"]
      }
    },
    {
      "name": "get_audit_trail",
      "description": "Retrieve complete audit trail for a loan application",
      "inputSchema": {
        "type": "object",
        "properties": {
          "applicationId": { "type": "string", "format": "uuid" }
        },
        "required": ["applicationId"]
      }
    }
  ],
  "resources": [
    {
      "uri": "loan://policies/{policyId}",
      "name": "Loan Policy Document",
      "mimeType": "application/json"
    }
  ]
}
```

### Test Plan

| Test Type | Scope | Pass Criteria |
|-----------|-------|---------------|
| MCP protocol | Server compliance | Passes MCP test suite |
| Tool invocation | Via MCP client | Tools callable via MCP |
| Resource access | loan://policies | Returns policy documents |
| Authentication | OAuth 2.0 | Rejects unauthorized |
| Claude Desktop | Manual test | Can use ATA tools |

### Acceptance Criteria

- [ ] MCP Server running on port 3000
- [ ] All tools accessible via MCP protocol
- [ ] Resources accessible via URI scheme
- [ ] OAuth 2.0 authentication working
- [ ] Claude Desktop can call evaluate_loan
- [ ] MCP Client connects to external credit MCP

---

## Phase 4: A2A Integration (Weeks 13-15)

### Objectives
- Implement A2A Agent interface
- Enable multi-agent collaboration
- Connect to Risk, Fraud, Compliance agents
- Implement agent orchestration patterns

### Features & Components

| Component | Description | Priority |
|-----------|-------------|----------|
| A2A Agent interface | Agent Card + endpoints | P0 |
| Agent discovery | Register with directory | P0 |
| Task delegation | Send tasks to other agents | P0 |
| Task handling | Receive tasks from orchestrator | P0 |
| Mock Risk Agent | For testing | P1 |
| Mock Fraud Agent | For testing | P1 |
| Orchestration | Multi-agent loan evaluation | P0 |

### Key Implementation

```java
// A2aAgentConfig.java
@Configuration
public class A2aAgentConfig {

    @Bean
    public A2aAgent auditTrailAgent() {
        return A2aAgent.builder()
            .agentCard(AgentCard.builder()
                .name("Audit Trail Agent")
                .description("Loan evaluation with audit trail")
                .url("https://ata.bank.internal/a2a")
                .version("1.0.0")
                .skills(List.of(
                    Skill.builder()
                        .id("loan-evaluation")
                        .name("Loan Application Evaluation")
                        .description("Evaluates loan applications with transparency")
                        .build()
                ))
                .build())
            .taskHandler(this::handleTask)
            .build();
    }

    private TaskResult handleTask(Task task) {
        return switch (task.getSkillId()) {
            case "loan-evaluation" -> {
                LoanApplication app = task.getInput(LoanApplication.class);
                LoanDecision decision = agent.evaluateLoan(app);
                yield TaskResult.completed(decision);
            }
            default -> TaskResult.failed("Unknown skill: " + task.getSkillId());
        };
    }
}

// MultiAgentOrchestrator.java
@Service
public class MultiAgentOrchestrator {

    @Autowired private A2aClient a2aClient;
    
    private static final String RISK_AGENT = "urn:agent:risk-assessment";
    private static final String FRAUD_AGENT = "urn:agent:fraud-detection";

    public EnhancedLoanDecision evaluateWithMultiAgent(LoanApplication app) {
        // 1. Get basic evaluation from ATA
        LoanDecision basicDecision = auditTrailAgent.evaluateLoan(app);
        
        // 2. Parallel calls to specialist agents
        CompletableFuture<RiskAssessment> riskFuture = a2aClient
            .sendTask(RISK_AGENT, "risk-assessment", app);
        
        CompletableFuture<FraudCheck> fraudFuture = a2aClient
            .sendTask(FRAUD_AGENT, "fraud-check", app);
        
        // 3. Wait for results
        RiskAssessment risk = riskFuture.join();
        FraudCheck fraud = fraudFuture.join();
        
        // 4. Combine decisions
        return combineDecisions(basicDecision, risk, fraud);
    }
}
```

### Test Plan

| Test Type | Scope | Pass Criteria |
|-----------|-------|---------------|
| Agent Card | A2A compliance | Valid Agent Card served |
| Task send | To mock agents | Tasks delivered successfully |
| Task receive | From orchestrator | Tasks processed correctly |
| Multi-agent | Full orchestration | All agents collaborate |
| Authentication | mTLS + JWT | Agents authenticate |

### Acceptance Criteria

- [ ] Agent Card published at /a2a/.well-known/agent.json
- [ ] Can send tasks to other A2A agents
- [ ] Can receive and process tasks
- [ ] Multi-agent orchestration working
- [ ] mTLS authentication between agents
- [ ] 95% success rate on multi-agent calls

---

## Phase 5: Production Readiness (Weeks 16-19)

### Objectives
- Replace Ollama with vLLM for production
- Implement full security controls
- Add monitoring and observability
- Performance optimization

### Features & Components

| Component | Description | Priority |
|-----------|-------------|----------|
| vLLM integration | Replace Ollama | P0 |
| LLM abstraction | Dev/Prod switching | P0 |
| Security hardening | All layers | P0 |
| Prometheus metrics | Custom metrics | P0 |
| Grafana dashboards | Visualization | P1 |
| Rate limiting | Resilience4j | P0 |
| Circuit breaker | Fault tolerance | P0 |
| Load testing | JMeter/Gatling | P0 |

### Key Implementation

```java
// LlmConfig.java - Abstraction layer
@Configuration
public class LlmConfig {

    @Bean
    @Profile("dev")
    public ChatLanguageModel ollamaModel() {
        return OllamaChatModel.builder()
            .baseUrl("http://localhost:11434")
            .modelName("llama3:8b")
            .build();
    }

    @Bean
    @Profile("prod")
    public ChatLanguageModel vllmModel(
            @Value("${vllm.base-url}") String baseUrl) {
        return OpenAiChatModel.builder()
            .baseUrl(baseUrl)
            .apiKey("not-needed")
            .modelName("meta-llama/Llama-3-70B-Instruct")
            .timeout(Duration.ofSeconds(60))
            .build();
    }
}

// MetricsConfig.java
@Configuration
public class MetricsConfig {

    @Bean
    MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
            .commonTags("application", "audit-trail-agent");
    }

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}

// Metrics in agent
@Service
public class AuditTrailAgent {

    private final Counter evaluationsCounter;
    private final Timer evaluationTimer;

    @Timed(value = "ata.loan.evaluation", description = "Loan evaluation duration")
    public LoanDecision evaluateLoan(LoanApplication app) {
        evaluationsCounter.increment();
        // ... evaluation logic
    }
}
```

### Performance Targets

| Metric | Target | Measurement |
|--------|--------|-------------|
| Latency (p50) | < 3s | Prometheus histogram |
| Latency (p99) | < 10s | Prometheus histogram |
| Throughput | 100 req/min | Load test |
| Error rate | < 1% | Error counter |
| Availability | 99.9% | Uptime monitor |

### Test Plan

| Test Type | Scope | Pass Criteria |
|-----------|-------|---------------|
| Load test | 100 concurrent | No errors, latency OK |
| Stress test | 500 concurrent | Graceful degradation |
| Security scan | Full app | No critical findings |
| Penetration test | External | No vulnerabilities |
| Failover | Kill vLLM node | Auto-recovery |

### Acceptance Criteria

- [ ] vLLM cluster operational (2+ nodes)
- [ ] Profile-based LLM switching works
- [ ] All security controls implemented
- [ ] Prometheus metrics exposed
- [ ] Grafana dashboards created
- [ ] Load test passes (100 req/min)
- [ ] Circuit breaker triggers on failure
- [ ] Security scan: zero critical issues

---

## Phase 6: Cloud Deployment (Weeks 20-22)

### Objectives
- Deploy to cloud (AWS/Azure/GCP)
- Implement infrastructure as code
- Set up production monitoring
- Complete documentation

### Features & Components

| Component | Description | Priority |
|-----------|-------------|----------|
| Terraform/Pulumi | Infrastructure as Code | P0 |
| Kubernetes manifests | K8s deployment | P0 |
| Helm charts | Package management | P1 |
| Cloud networking | VPC, Private Link | P0 |
| Database migration | Cloud PostgreSQL | P0 |
| Secret management | Vault / Cloud secrets | P0 |
| CI/CD production | Production pipeline | P0 |
| Runbook | Operations guide | P0 |

### Deployment Architecture

```yaml
# kubernetes/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: audit-trail-agent
spec:
  replicas: 3
  selector:
    matchLabels:
      app: ata
  template:
    spec:
      containers:
      - name: ata
        image: bank/audit-trail-agent:${VERSION}
        ports:
        - containerPort: 8080
        - containerPort: 3000  # MCP
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: VLLM_BASE_URL
          valueFrom:
            configMapKeyRef:
              name: ata-config
              key: vllm-url
        resources:
          requests:
            memory: "2Gi"
            cpu: "1000m"
          limits:
            memory: "4Gi"
            cpu: "2000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
---
apiVersion: v1
kind: Service
metadata:
  name: ata-service
spec:
  type: ClusterIP
  ports:
  - name: http
    port: 8080
  - name: mcp
    port: 3000
  selector:
    app: ata
```

### Test Plan

| Test Type | Scope | Pass Criteria |
|-----------|-------|---------------|
| Smoke test | All endpoints | Respond correctly |
| Integration | Cloud services | All connected |
| DR test | Region failover | Recovery < 30min |
| Chaos engineering | Pod kill | Auto-recovery |
| End-to-end | Full loan flow | Complete with audit |

### Acceptance Criteria

- [ ] Deployed to cloud with IaC
- [ ] All environments (dev/staging/prod)
- [ ] CI/CD pipeline to production
- [ ] Monitoring and alerting active
- [ ] Runbook completed
- [ ] DR tested successfully
- [ ] Go-live sign-off obtained

---

## Phase Summary

| Phase | Duration | Key Deliverable | Test Focus |
|-------|----------|-----------------|------------|
| **0: Setup** | 2 weeks | Dev environment | Build, Ollama |
| **1: Core** | 4 weeks | Working agent | Tools, Reasoning |
| **2: Audit** | 3 weeks | Immutable audit trail | Persistence, Integrity |
| **3: MCP** | 3 weeks | MCP Server/Client | Protocol compliance |
| **4: A2A** | 3 weeks | Multi-agent collab | Agent communication |
| **5: Prod** | 4 weeks | Production ready | Performance, Security |
| **6: Cloud** | 3 weeks | Cloud deployment | Infrastructure |

---

## Risk Register

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Ollama performance issues | Medium | High | Move to vLLM early |
| LangChain4j limitations | Low | Medium | Fallback to custom impl |
| MCP SDK immaturity | Medium | Medium | Contribute fixes upstream |
| vLLM memory issues | Medium | High | Quantization, scaling |
| Cloud approval delays | Medium | High | Start process early |
| Security audit findings | Medium | Medium | Continuous scanning |

---

## Team Structure

| Role | Phase 0-1 | Phase 2-4 | Phase 5-6 |
|------|-----------|-----------|-----------|
| **Tech Lead** | Architecture | Architecture | Architecture |
| **Backend Dev 1** | Agent core | MCP Server | Production |
| **Backend Dev 2** | Tools | Audit | Security |
| **Backend Dev 3** | - | MCP Client | Cloud/K8s |
| **ML Engineer** | Ollama setup | - | vLLM setup |
| **DevOps** | CI/CD | - | Infrastructure |
| **QA** | Test framework | Integration tests | E2E, Load tests |

---

## Definition of Done

For each phase to be considered complete:

- [ ] All features implemented and code reviewed
- [ ] Unit tests: 80%+ coverage
- [ ] Integration tests: all passing
- [ ] Documentation updated
- [ ] Security scan: no critical issues
- [ ] Performance within targets
- [ ] Demo to stakeholders completed
- [ ] Sign-off from Tech Lead

