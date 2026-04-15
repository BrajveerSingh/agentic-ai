# Audit Trail Agent — Development Status

> Last updated: 2026-04-11

---

## Phase Completion Summary

| Phase | Status | Notes |
|-------|--------|-------|
| **Phase 0 – Setup**          | ✅ Complete | Multi-module Maven, Docker Compose, ADR-001 |
| **Phase 1 – Core Agent**     | ✅ Complete | LangChain4j agent, 5 tools, REST API |
| **Phase 2 – Audit Trail**    | ✅ Complete | PostgreSQL/H2 persistence, AOP interceptor, vector search |
| **Phase 3 – MCP Integration**| ✅ Complete | See below |
| **Phase 4 – A2A**            | ✅ Complete | See below |
| **Phase 5 – Production**     | 🔄 In Progress | Distributed container architecture |
| **Phase 6 – Cloud**          | 🔲 Not started | |

---

## Phase 5 – Distributed Container Architecture — In Progress 🔄

### Work Done (2026-04-11)

Implemented multi-container distributed architecture to run each agent, MCP server, and mock service in its own Docker container.

#### New Runnable App Modules

| Module | Purpose | Port | Dockerfile |
|--------|---------|------|------------|
| `ata-gateway-app` | External REST API gateway | 8080 | `docker/Dockerfile.gateway` |
| `ata-orchestrator-app` | Primary agent + audit + outbound A2A/MCP | 8081 | `docker/Dockerfile.orchestrator` |
| `ata-mock-credit-bureau-app` | Standalone mock MCP server for credit bureau | 18081 | `docker/Dockerfile.mock-credit-bureau` |

#### Architecture Pattern

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────────┐
│  External       │────▶│    Gateway       │────▶│   Orchestrator      │
│  Clients        │     │    (8080)        │     │   (8081)            │
└─────────────────┘     └──────────────────┘     └─────────┬───────────┘
                                                           │
                        ┌──────────────────────────────────┼──────────────────┐
                        │                                  │                  │
                        ▼                                  ▼                  ▼
              ┌─────────────────┐              ┌─────────────────┐  ┌─────────────────┐
              │ Credit Bureau   │              │   Risk Agent    │  │  Fraud Agent    │
              │ MCP (18081)     │              │   (planned)     │  │  (planned)      │
              └─────────────────┘              └─────────────────┘  └─────────────────┘
```

#### Components Implemented

| Component | File(s) | Notes |
|-----------|---------|-------|
| **Gateway App** | `ata-gateway-app/` | Public REST endpoints, forwards to orchestrator via `RestClient` |
| **Gateway Controller** | `GatewayLoanController.java`, `GatewayAuditController.java` | `/api/loans/evaluate`, `/api/audit/**` |
| **Orchestrator Client** | `OrchestratorClient.java` | RestClient-based forwarding to orchestrator |
| **Orchestrator App** | `ata-orchestrator-app/` | Runs agent, audit, A2A/MCP clients |
| **Internal Controller** | `InternalOrchestratorController.java` | `/internal/loans/evaluate`, `/internal/audit/**` |
| **Mock Credit Bureau App** | `ata-mock-credit-bureau-app/` | Standalone MCP server for credit bureau tool |
| **Conditional Controller** | `MockCreditBureauController.java` updated | `@ConditionalOnProperty` gate to avoid duplicate in-process beans |
| **Spring Boot Fat Jar Fix** | All app `pom.xml` files | Added `<executions><goal>repackage</goal>` for executable jars |
| **JPA Entity Scan** | `AtaOrchestratorApplication.java` | `@EntityScan` + `@EnableJpaRepositories` for cross-module entities |
| **LLM Profile Fix** | `LlmConfig.java` | Added `compose` profile to `@Profile({"dev", "default", "compose"})` |
| **PostgreSQL Dialect** | `application-compose.yml` | Added `driver-class-name` and `database-platform` for Hibernate |
| **Ollama Config** | `application-compose.yml` | `ollama.base-url: http://ollama:11434` for Docker DNS |

#### Docker Compose Services

| Service | Image | Port Mapping | Dependencies |
|---------|-------|--------------|--------------|
| `gateway` | `audit-trail-agent-gateway` | 8080:8080 | orchestrator |
| `orchestrator` | `audit-trail-agent-orchestrator` | 8081:8081 | postgres, ollama, credit-bureau-mcp |
| `credit-bureau-mcp` | `audit-trail-agent-credit-bureau-mcp` | 18081:8080 | — |
| `postgres` | `postgres:16-alpine` | 5432:5432 | — |
| `ollama` | `ollama/ollama:latest` | 11434:11434 | — |

#### Configuration Files

| File | Purpose |
|------|---------|
| `ata-orchestrator-app/src/main/resources/application.yml` | Default H2, port 8081 |
| `ata-orchestrator-app/src/main/resources/application-compose.yml` | PostgreSQL + Docker DNS names |
| `ata-gateway-app/src/main/resources/application.yml` | Orchestrator base URL config |
| `distributed_agentic_system.md` | Architecture documentation with Mermaid diagrams |

#### Issues Fixed

| Issue | Resolution |
|-------|------------|
| `no main manifest attribute` in Docker | Added `spring-boot-maven-plugin` `<executions><goal>repackage</goal>` |
| `ChatLanguageModel` bean not found | Added `compose` to `@Profile` in `LlmConfig` |
| `Unable to determine Dialect` | Added `driver-class-name` and `database-platform` to compose config |
| `Not a managed type: AuditEventEntity` | Added `@EntityScan` and `@EnableJpaRepositories` to orchestrator app |

#### Remaining Work

- [ ] Start orchestrator container successfully
- [ ] Start gateway container  
- [ ] End-to-end test: `POST http://localhost:8080/api/loans/evaluate`
- [ ] Implement standalone `risk-agent` app/container
- [ ] Implement standalone `fraud-agent` app/container
- [ ] Implement standalone MCP tools server app/container
- [ ] Integration test suite for distributed architecture

---

## Phase 3 – MCP Integration — Complete ✅

### What was already done (before this session)
| Component | File(s) |
|-----------|---------|
| `@McpTool` annotation | `annotation/McpTool.java` |
| `@McpParam` annotation | `annotation/McpParam.java` |
| `McpToolRegistry` – discovers & invokes `@McpTool` methods | `server/McpToolRegistry.java` |
| `McpJsonRpcHandler` – JSON-RPC 2.0 routing (initialize / tools/\*) | `server/McpJsonRpcHandler.java` |
| `McpController` – HTTP+SSE transport (`GET /mcp/sse`, `POST /mcp/message`) | `server/McpController.java` |
| `McpServerConfig` – registers tools at startup | `McpServerConfig.java` |
| `McpModule` – Spring component-scan | `McpModule.java` |
| `AuditMcpTools` – 3 tools: `evaluate_loan`, `get_audit_trail`, `search_audit_reasoning` | `AuditMcpTools.java` |
| 8 unit tests | `McpToolsTest.java` |

### What was implemented in this session
| Component | File(s) | Notes |
|-----------|---------|-------|
| **`@McpResource` annotation** | `annotation/McpResource.java` | Static URI or `{template}` |
| **`McpResourceRegistry`** | `server/McpResourceRegistry.java` | Pattern-based URI matching |
| **`LoanPolicyResources`** | `LoanPolicyResources.java` | `loan://policies`, `loan://policies/{policyId}` (standard \| credit \| kyc \| risk) |
| **`AuditTrailResources`** | `AuditTrailResources.java` | `audit://trail/{applicationId}` |
| **`resources/list` + `resources/read`** in handler | `server/McpJsonRpcHandler.java` | Updated capabilities to include `resources: {}` |
| **`McpServerConfig`** updated | `McpServerConfig.java` | Registers resource providers at startup |
| **2 new MCP tools** | `AuditMcpTools.java` | `get_credit_score`, `check_compliance` (total: **5 tools**) |
| **`McpClientService`** | `client/McpClientService.java` | HTTP JSON-RPC 2.0 client (initialize / listTools / callTool) |
| **`McpClientConfig`** | `client/McpClientConfig.java` | `creditBureauMcpClient` bean, custom RestTemplate |
| **`MockCreditBureauController`** | `mock/MockCreditBureauController.java` | Dev-mode MCP server at `/mock/credit-bureau-mcp/message` |
| **`McpJsonRpcHandlerTest`** | `test/McpJsonRpcHandlerTest.java` | 12 tests – all protocol methods |
| **`McpClientServiceTest`** | `test/McpClientServiceTest.java` | 9 tests – initialize / listTools / callTool |
| **`McpToolsTest`** updated | `test/McpToolsTest.java` | 3 new tests; tool count updated to 5 |

### Test Results

```
Tests run: 32, Failures: 0, Errors: 0, Skipped: 0
  McpClientServiceTest   :  9 tests
  McpJsonRpcHandlerTest  : 12 tests
  McpToolsTest           : 11 tests
```

### MCP Server Capabilities (after Phase 3)

```
GET  /mcp/sse            — SSE stream (Claude Desktop, IDE plugins)
POST /mcp/message        — JSON-RPC 2.0 message endpoint
GET  /mcp/health         — liveness probe

Supported methods:
  initialize                → protocol negotiation; advertises tools + resources
  notifications/initialized → client ACK
  tools/list                → returns 5 tools
  tools/call                → invokes a tool
  resources/list            → returns 2 static resources + 2 URI templates
  resources/read            → reads a resource by full URI
```

### MCP Tools

| Tool name | Description |
|-----------|-------------|
| `evaluate_loan` | Full AI loan evaluation with audit trail |
| `get_audit_trail` | Retrieve audit trail by application UUID |
| `search_audit_reasoning` | Semantic search over reasoning history |
| `get_credit_score` | Look up customer credit score |
| `check_compliance` | Check loan scenario against bank policies |

### MCP Resources

| URI | Type | Description |
|-----|------|-------------|
| `loan://policies` | Static | Index of all policy documents |
| `loan://policies/{policyId}` | Template | Policy document (`standard` \| `credit` \| `kyc` \| `risk`) |
| `audit://trail/{applicationId}` | Template | Audit trail for a loan application UUID |

### MCP Client

| Bean | URL (default) | Purpose |
|------|---------------|---------|
| `creditBureauMcpClient` | `http://localhost:8080/mock/credit-bureau-mcp/message` | Calls external credit bureau |

### Mock Credit Bureau

Available at `POST /mock/credit-bureau-mcp/message` — implements `get_credit_score_external` tool for development/testing.

### Claude Desktop Integration

Add to `claude_desktop_config.json`:
```json
{
  "mcpServers": {
    "audit-trail-agent": {
      "url": "http://localhost:8080/mcp/sse"
    }
  }
}
```

---

## Phase 3 Acceptance Criteria Status

| Criterion | Status |
|-----------|--------|
| MCP Server running (`/mcp/sse`, `/mcp/message`) | ✅ |
| All 5 tools accessible via `tools/list` + `tools/call` | ✅ |
| Resources accessible via `resources/list` + `resources/read` | ✅ |
| URI templates: `loan://policies/{id}`, `audit://trail/{id}` | ✅ |
| MCP Client connects to external credit MCP server | ✅ |
| Mock Credit Bureau MCP at `/mock/credit-bureau-mcp/message` | ✅ |
| 32 unit tests passing, 0 failures | ✅ |
| OAuth 2.0 authentication | ⏳ Deferred to Phase 5 (security hardening) |
| mTLS transport | ⏳ Deferred to Phase 5 |
| Claude Desktop manual test | ⏳ Manual (start app + configure Claude Desktop) |

---

## Phase 4 – A2A Integration — Complete ✅

### Components Implemented

| Component | File(s) | Notes |
|-----------|---------|-------|
| **Model layer** (9 records) | `model/A2aPart.java`, `A2aMessage.java`, `A2aArtifact.java`, `A2aTask.java`, `TaskState.java`, `TaskStatus.java`, `AgentCapabilities.java`, `AgentSkill.java`, `AgentCard.java` | Zero external dependencies; pure Java 21 records |
| **`A2aTaskHandler`** | `server/A2aTaskHandler.java` | Handles `evaluate_loan` skill; bridges A2A → `AuditTrailAgent` |
| **`A2aController`** | `server/A2aController.java` | `GET /.well-known/agent.json`, `POST /tasks/send`, `GET /health` |
| **`A2aClient`** | `client/A2aClient.java` | `RestClient`-based (non-deprecated); `sendTask`, `fetchAgentCard`, `isAvailable` |
| **`A2aClientConfig`** | `client/A2aClientConfig.java` | `riskAgentClient` + `fraudAgentClient` beans; 5s/30s timeouts |
| **`MultiAgentLoanOrchestrator`** | `orchestration/MultiAgentLoanOrchestrator.java` | Local agent + Risk peer + Fraud peer; best-effort degradation |
| **`MockRiskAgentController`** | `mock/MockRiskAgentController.java` | `POST /mock/risk-agent/tasks/send`; deterministic by loan amount |
| **`MockFraudAgentController`** | `mock/MockFraudAgentController.java` | `POST /mock/fraud-agent/tasks/send`; flags "FRAUD" customers |
| **`A2aModule`** | `A2aModule.java` | `@Configuration @ComponentScan("com.bank.ata.a2a")` |

### A2A Server Endpoints

```
GET  /a2a/.well-known/agent.json  — Agent Card (capabilities + skills)
POST /a2a/tasks/send              — Submit a task synchronously
GET  /a2a/health                  — Liveness probe

Mock peer agents (dev only):
POST /mock/risk-agent/tasks/send
GET  /mock/risk-agent/.well-known/agent.json
GET  /mock/risk-agent/health

POST /mock/fraud-agent/tasks/send
GET  /mock/fraud-agent/.well-known/agent.json
GET  /mock/fraud-agent/health
```

### Multi-Agent Orchestration Logic

| Scenario | Outcome |
|----------|---------|
| No peer concerns | Base decision preserved |
| Risk Agent returns `riskLevel: HIGH` | Override to `PENDING_REVIEW` |
| Fraud Agent returns `fraudDetected: true` | Override to `PENDING_REVIEW` |
| Base decision already `REJECTED` | Stays `REJECTED` regardless of peers |
| Any peer unavailable or throws | Graceful skip; base decision used |

### Test Results

```
Tests run: 27, Failures: 0, Errors: 0, Skipped: 0
  A2aTaskHandlerTest           :  7 tests
  A2aControllerTest            :  5 tests
  A2aClientTest                :  8 tests
  MultiAgentLoanOrchestratorTest:  7 tests
```

### Phase 4 Acceptance Criteria

| Criterion | Status |
|-----------|--------|
| Agent Card at `GET /a2a/.well-known/agent.json` | ✅ |
| Task submission at `POST /a2a/tasks/send` | ✅ |
| `A2aClient` using `RestClient` (non-deprecated) | ✅ |
| Mock Risk Agent at `/mock/risk-agent/tasks/send` | ✅ |
| Mock Fraud Agent at `/mock/fraud-agent/tasks/send` | ✅ |
| Multi-agent orchestration with graceful degradation | ✅ |
| 27 unit tests passing, 0 failures | ✅ |
| A2A config in `application-dev.yml` | ✅ |
| No external A2A SDK dependency (custom records only) | ✅ |

---

## Next Steps

### Immediate (Phase 5 Completion)
- Complete Docker container startup validation
- End-to-end test of gateway → orchestrator → MCP flow
- Implement standalone risk-agent and fraud-agent containers
- Add health checks and readiness probes

### Planned (Phase 5 Hardening)
- OAuth 2.0 / mTLS authentication for A2A + MCP endpoints
- PostgreSQL production config + Flyway migrations
- Prometheus metrics + Grafana dashboards
- Load testing & performance benchmarks

### Phase 6 – Cloud Deployment
- Kubernetes manifests
- Helm charts
- Cloud-native observability (OpenTelemetry)

---

*End of development status*
