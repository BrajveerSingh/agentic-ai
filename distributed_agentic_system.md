# Distributed Agentic System (Containers: Agents + MCP + Remote Services)

This document describes a **fully distributed** deployment model for the Audit Trail Agent (ATA) system where:

- Each **agent** runs in its own container (primary agent + peer agents).
- Each **MCP server** runs in its own container.
- Each **remote/mock service** (e.g., Credit Bureau MCP) runs in its own container.
- Components communicate only via **network protocols**:
  - REST (entrypoint API)
  - A2A (agent-to-agent task protocol over HTTP)
  - MCP (tool calling via JSON-RPC over HTTP)

The goal is to simulate a real-world deployment with clear failure domains, scalable services, and testable network boundaries.

---

## 1) Container Roles

### 1.1 `ata-app` — Primary Agent + REST API + Audit

**Responsibilities**
- Exposes the main REST entrypoint (confirmed in `LoanController`):
  - `POST /api/loans/evaluate`
- Runs the primary loan evaluation agent.
- Orchestrates peer agent calls using **A2A**.
- Calls tool providers using **MCP** (JSON-RPC over HTTP).
- Persists the audit trail to Postgres.

**Dependencies**
- A2A peer agents (risk, fraud)
- MCP tool servers (internal + external)
- PostgreSQL database
- Optional LLM runtime (Ollama) if you want non-deterministic / real LLM evaluation

---

### 1.2 Peer A2A Agents (separate containers)

#### `risk-agent`
**Responsibilities**
- Implements an A2A server providing risk analysis.
- Returns artifacts like risk score / risk level / flagged status.

**Recommended endpoints (realistic paths)**
- `GET  /.well-known/agent.json`
- `GET  /health`
- `POST /tasks/send`

#### `fraud-agent`
**Responsibilities**
- Implements an A2A server providing fraud detection.
- Returns artifacts like fraudDetected / fraudScore.

**Recommended endpoints (realistic paths)**
- `GET  /.well-known/agent.json`
- `GET  /health`
- `POST /tasks/send`

> Note: In the current repo, the mock A2A peer controllers exist under `/mock/...` paths. For a distributed deployment, it’s best to mount those handlers at the service root paths shown above.

---

### 1.3 MCP Servers (separate containers)

#### `mcp-tools-server` (internal MCP)
**Responsibilities**
- Provides internal ATA tools over MCP.
- Examples: evaluate loan, audit search/retrieval, compliance checks.

**Endpoints**
- `POST /mcp/message`
- `GET  /mcp/sse` (optional)

#### `credit-bureau-mcp` (external mock MCP server)
**Responsibilities**
- Simulates an external credit bureau.
- Exposes MCP tool(s) like `get_credit_score_external`.

**Recommended endpoints**
- `GET  /health`
- `POST /message`

> In the current repo, the mock credit bureau MCP controller is mapped under `/mock/credit-bureau-mcp/message`. In a dedicated container, prefer simplifying to `/message`.

#### Other external MCP servers (examples)
- `compliance-mcp`: policy/compliance tools
- `kyc-mcp`: identity/KYC/AML tools
- `sanctions-mcp`: sanctions screening tools

All follow the same MCP shape:
- `GET /health`
- `POST /message`

---

### 1.4 Persistence + Observability (separate containers)

#### `postgres`
- Stores audit events, decisions, tool calls, and reasoning steps.

#### `prometheus`
- Scrapes metrics from each service (`/actuator/prometheus`).

#### `grafana`
- Visualizes Prometheus metrics dashboards.

#### `ollama` (optional)
- Local LLM runtime if you want the primary agent to use real LLM inference.

For deterministic E2E tests (CI stable), consider running without Ollama and using deterministic evaluation modes.

---

## 2) End-to-End Protocol Flow (E2E Scenario)

### Scenario: “Loan evaluation triggers A2A + MCP + auditing”

1. An integration test (or client) calls:
   - `POST http://ata-app:8080/api/loans/evaluate`

2. `ata-app`:
   - builds a base decision using the primary agent
   - calls peer agents via **A2A**:
     - `risk-agent` → `POST http://risk-agent:8080/tasks/send`
     - `fraud-agent` → `POST http://fraud-agent:8080/tasks/send`

3. `ata-app` calls tool providers via **MCP**:
   - credit bureau:
     - `POST http://credit-bureau-mcp:8080/message` (JSON-RPC: initialize/tools/list/tools/call)
   - internal tools MCP:
     - `POST http://mcp-tools-server:8080/mcp/message`

4. `ata-app` merges results and produces a final decision:
   - e.g., fraud detection or high risk overrides decision to `PENDING_REVIEW` or `REJECTED`

5. `ata-app` persists audit events to `postgres`.

**Success criteria**
- HTTP 200 response from `ata-app` with a decision + reasoning.
- Logs show A2A peer calls and MCP tool calls.
- DB contains audit trail entries for the evaluation session (session/application IDs).

---

## 3) Logical Architecture Diagram (services + protocols)

```mermaid
flowchart LR
  %% Client / Entry
  User[Client / Integration Test\n(curl / JUnit)] -->|HTTP POST /api/loans/evaluate| ATA[ata-app container\nPrimary Audit Trail Agent\nREST API + Orchestrator]

  %% A2A Peer Agents
  ATA -->|A2A HTTP\nPOST /tasks/send\nGET /.well-known/agent.json\nGET /health| Risk[risk-agent container\nA2A Peer Agent]
  ATA -->|A2A HTTP\nPOST /tasks/send\nGET /.well-known/agent.json\nGET /health| Fraud[fraud-agent container\nA2A Peer Agent]

  %% MCP Servers
  ATA -->|MCP JSON-RPC over HTTP\nPOST /mcp/message| ToolsMCP[mcp-tools-server container\nMCP Server (internal tools)]
  ATA -->|MCP JSON-RPC over HTTP\nPOST /message| CreditMCP[credit-bureau-mcp container\nExternal MCP Server]
  ATA -->|MCP JSON-RPC over HTTP\nPOST /message| ComplianceMCP[compliance-mcp container\nExternal MCP Server]
  ATA -->|MCP JSON-RPC over HTTP\nPOST /message| KycMCP[kyc-mcp container\nExternal MCP Server]

  %% Optional: peer agents also use MCP
  Risk -->|MCP JSON-RPC over HTTP| CreditMCP
  Fraud -->|MCP JSON-RPC over HTTP| KycMCP

  %% Persistence / Audit
  ATA -->|JPA / JDBC| DB[(postgres container\nAudit DB)]
  ToolsMCP -->|Optional: persist audit/tool events| DB

  %% Observability
  Prom[Prometheus container] <-->|scrape /actuator/prometheus| ATA
  Prom <-->|scrape /actuator/prometheus| Risk
  Prom <-->|scrape /actuator/prometheus| Fraud
  Prom <-->|scrape /actuator/prometheus| ToolsMCP
  Prom <-->|scrape /actuator/prometheus| CreditMCP
  Graf[Grafana container] -->|queries| Prom

  %% LLM Runtime (optional)
  ATA -->|HTTP| Ollama[ollama container\nLLM runtime (optional)]
```

---

## 4) Deployment Diagram (Docker network + ports + concrete URLs)

This emphasizes:
- Docker Compose service DNS names
- Ports
- Exactly what URL each call uses

```mermaid
flowchart TB
  Test[Integration Test / Client\nHost machine] -->|HTTP :8080\nPOST /api/loans/evaluate| ATA

  subgraph Net[Docker network: ata-network]
    ATA[ata-app\nContainer\n:8080]
    Risk[risk-agent\nContainer\n:8080]
    Fraud[fraud-agent\nContainer\n:8080]

    McpTools[mcp-tools-server\nContainer\n:8080]
    CreditMcp[credit-bureau-mcp\nContainer\n:8080]
    ComplianceMcp[compliance-mcp\nContainer\n:8080]
    KycMcp[kyc-mcp\nContainer\n:8080]

    DB[(postgres\nContainer\n:5432)]

    Prom[prometheus\nContainer\n:9090]
    Graf[grafana\nContainer\n:3000]

    Ollama[ollama (optional)\nContainer\n:11434]
  end

  %% A2A
  ATA -->|A2A HTTP\nhttp://risk-agent:8080/tasks/send| Risk
  ATA -->|A2A HTTP\nhttp://fraud-agent:8080/tasks/send| Fraud

  %% MCP
  ATA -->|MCP JSON-RPC\nhttp://mcp-tools-server:8080/mcp/message| McpTools
  ATA -->|MCP JSON-RPC\nhttp://credit-bureau-mcp:8080/message| CreditMcp
  ATA -->|MCP JSON-RPC\nhttp://compliance-mcp:8080/message| ComplianceMcp
  ATA -->|MCP JSON-RPC\nhttp://kyc-mcp:8080/message| KycMcp

  %% Persistence
  ATA -->|JDBC\njdbc:postgresql://postgres:5432/atadb| DB

  %% Observability
  Prom -->|Scrape /actuator/prometheus| ATA
  Prom -->|Scrape /actuator/prometheus| Risk
  Prom -->|Scrape /actuator/prometheus| Fraud
  Prom -->|Scrape /actuator/prometheus| McpTools
  Prom -->|Scrape /actuator/prometheus| CreditMcp
  Graf -->|Query| Prom

  %% Optional LLM
  ATA -->|HTTP\nhttp://ollama:11434| Ollama
```

---

## 5) Required Configuration (high-level)

### `ata-app` (must be configured to call other containers)

**A2A**
- `a2a.client.risk-agent.url=http://risk-agent:8080/tasks/send`
- `a2a.client.fraud-agent.url=http://fraud-agent:8080/tasks/send`

**MCP**
- `mcp.client.credit-bureau.url=http://credit-bureau-mcp:8080/message`
- Internal tools MCP (property name depends on wiring):
  - `mcp.tools.url=http://mcp-tools-server:8080/mcp/message`

**DB**
- `spring.datasource.url=jdbc:postgresql://postgres:5432/atadb`
- `spring.datasource.username=...`
- `spring.datasource.password=...`

**Optional LLM**
- `ollama.base-url=http://ollama:11434` (exact property depends on the LangChain4j config you are using)

### Peer agents + MCP servers
- Typically only `server.port=8080`, logging, and actuator metrics settings.

---

## 6) Notes on “production-like” endpoint paths

For a realistic distributed environment:
- A2A peers expose `/.well-known/agent.json`, `/tasks/send`, `/health` at the service root.
- External MCP servers expose `/message` at the service root.
- Avoid `/mock/...` prefixes in distributed mode; keep them only for in-process dev mode.

