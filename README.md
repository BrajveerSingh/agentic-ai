# agentic-ai

This repository contains proof of concepts for A2A, MCP, LangChain4j and complete agentic app setup.

---

# Audit Trail Agent (ATA)

[![CI Pipeline](https://github.com/BrajveerSingh/agentic-ai/actions/workflows/ci.yml/badge.svg)](https://github.com/BrajveerSingh/agentic-ai/actions/workflows/ci.yml)
[![Java Version](https://img.shields.io/badge/Java-25_LTS-blue.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-green.svg)](https://spring.io/projects/spring-boot)
[![Phase](https://img.shields.io/badge/Phase-3%20MCP-brightgreen.svg)](#phase-status)

**Bank-Grade AI Agent for Loan Evaluation with Immutable Audit Trail**

## Overview

The Audit Trail Agent (ATA) is a Java-native Agentic AI system designed to automate loan application evaluations while maintaining 100% regulatory compliance. Unlike standard "black-box" AI, the ATA uses Deterministic Tool Calling to provide a transparent, immutable log of every decision step.

### Key Features

- 🔒 **Immutable Audit Trail** — Every reasoning step logged before execution
- 🤖 **ReAct Agent** — LangChain4j powered reasoning and action loop
- 🏠 **Local-First** — LLM runs on-premise via Ollama (dev) or vLLM (prod)
- 🔗 **MCP Server + Client** — 5 tools and 3 resources exposed via Model Context Protocol
- 🗂️ **MCP Resources** — Loan policy documents and audit trails accessible by URI
- 📡 **Mock Credit Bureau** — Built-in external MCP server for development
- ☁️ **Cloud Ready** — Kubernetes deployment with full observability

## Phase Status

| Phase | Status | Key Deliverable |
|-------|--------|-----------------|
| **0 – Setup**         | ✅ Complete | Maven multi-module, Docker Compose, ADR-001 |
| **1 – Core Agent**    | ✅ Complete | LangChain4j ReAct agent, 5 tools, REST API |
| **2 – Audit Trail**   | ✅ Complete | PostgreSQL/H2 persistence, AOP interceptor, JVector semantic search |
| **3 – MCP**           | ✅ Complete | MCP Server (tools + resources), MCP Client, Mock Credit Bureau |
| **4 – A2A**           | ✅ Complete | A2A protocol, RestClient, distributed container architecture |
| **5 – Production**    | 🔲 Planned | vLLM, security hardening, Prometheus |
| **6 – Cloud**         | 🔲 Planned | Kubernetes, Terraform |

## Quick Start

### Prerequisites

- Java 25 LTS or higher
- Docker & Docker Compose
- Maven 3.9+

### 1. Clone and Build

```bash
git clone https://github.com/bank/audit-trail-agent.git
cd audit-trail-agent
mvn clean install -DskipTests
```

### 2. Start Local Environment

```bash
# Start Ollama, PostgreSQL, and monitoring
docker-compose up -d

# Wait for Ollama to pull the model (first time only, ~5 min)
docker-compose logs -f ollama-pull
```

### 3. Run the Application

```bash
# Linux / macOS
cd ata-app
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

```powershell
# Windows PowerShell — quote the -D argument to prevent argument splitting
cd ata-app
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

### 4. Verify

```bash
# Health check
curl http://localhost:8080/api/health

# Application info
curl http://localhost:8080/api/info

# MCP server health
curl http://localhost:8080/mcp/health
```

## Project Structure

```
audit-trail-agent/
├── ata-core/                      # Domain models and shared utilities
├── ata-agent/                     # LangChain4j agent, tools, LLM config
├── ata-audit/                     # Audit trail persistence, AOP interceptor, JVector
├── ata-mcp/                       # MCP Server, MCP Client, resources, mock credit bureau
│   ├── annotation/                #   @McpTool, @McpParam, @McpResource annotations
│   ├── server/                    #   McpController, McpJsonRpcHandler, registries
│   ├── client/                    #   McpClientService, McpClientConfig
│   └── mock/                      #   MockCreditBureauController (dev only)
├── ata-a2a/                       # A2A Protocol implementation
├── ata-app/                       # Main Spring Boot application (monolith mode)
├── ata-gateway-app/               # Gateway service (distributed mode, port 8080)
├── ata-orchestrator-app/          # Orchestrator service (distributed mode, port 8081)
├── ata-mock-credit-bureau-app/    # Mock Credit Bureau MCP (standalone, port 18081)
├── docker/                        # Dockerfiles for each service
├── diagrams/                      # Architecture diagrams (Mermaid)
└── docs/                          # Documentation, SQL scripts
```

## Configuration

### Development (Ollama)

```yaml
# application-dev.yml
ollama:
  base-url: http://localhost:11434
  model: llama3:8b
  timeout: 120
```

### Production (vLLM)

```yaml
# application-prod.yml
vllm:
  base-url: http://vllm-cluster:8000/v1
  model: meta-llama/Llama-3-70B-Instruct
  timeout: 60
```

### MCP Client (optional override)

```yaml
# Override the external credit bureau MCP URL
mcp:
  client:
    credit-bureau:
      url: https://credit-bureau-mcp.bank.internal/mcp/message
```

## API Endpoints

### REST API

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/health` | GET | Health check |
| `/api/info` | GET | Application info |
| `/api/docs` | GET | API documentation |
| `/api/loans/evaluate` | POST | Evaluate loan application (AI agent) |
| `/api/loans/evaluate/quick` | POST | Quick rule-based evaluation (no AI) |
| `/api/audit/application/{id}` | GET | Retrieve audit trail by application UUID |
| `/api/audit/session/{id}` | GET | Retrieve audit trail by session UUID |
| `/api/audit/application/{id}/exists` | GET | Check if audit trail exists |
| `/actuator/prometheus` | GET | Prometheus metrics |

### MCP Endpoints (Phase 3)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/mcp/sse` | GET | SSE stream — connect MCP clients (Claude Desktop, IDE plugins) |
| `/mcp/message` | POST | JSON-RPC 2.0 — send MCP protocol messages |
| `/mcp/health` | GET | MCP server liveness probe |
| `/mock/credit-bureau-mcp/message` | POST | Mock external credit bureau (dev only) |
| `/mock/credit-bureau-mcp/health` | GET | Mock credit bureau health |

#### MCP Protocol Methods

| Method | Description |
|--------|-------------|
| `initialize` | Protocol negotiation — returns `tools` + `resources` capabilities |
| `notifications/initialized` | Client ACK (no response) |
| `tools/list` | List all 5 available tools with JSON Schemas |
| `tools/call` | Invoke a tool by name |
| `resources/list` | List static resources and URI templates |
| `resources/read` | Read a resource by full URI |

#### MCP Tools

| Tool | Description |
|------|-------------|
| `evaluate_loan` | Full AI-powered loan evaluation with complete audit trail |
| `get_audit_trail` | Retrieve audit trail for a loan application UUID |
| `search_audit_reasoning` | Semantic search over historical reasoning steps |
| `get_credit_score` | Look up credit score for a customer |
| `check_compliance` | Check loan scenario against bank policies |

#### MCP Resources

| URI | Type | Description |
|-----|------|-------------|
| `loan://policies` | Static | Index of all loan policy documents |
| `loan://policies/{policyId}` | Template | Policy document: `standard` \| `credit` \| `kyc` \| `risk` |
| `audit://trail/{applicationId}` | Template | Audit trail for a loan application UUID |

## Claude Desktop Integration

Add to `~/Library/Application Support/Claude/claude_desktop_config.json`
(macOS) or `%APPDATA%\Claude\claude_desktop_config.json` (Windows):

```json
{
  "mcpServers": {
    "audit-trail-agent": {
      "url": "http://localhost:8080/mcp/sse"
    }
  }
}
```

Restart Claude Desktop. The 5 ATA tools and 3 resources will appear automatically.

## End-to-End Testing with Docker

The distributed architecture runs each component in its own Docker container, enabling realistic end-to-end testing of the loan evaluation flow with MCP protocols.

### Container Architecture

| Container | Port | Description |
|-----------|------|-------------|
| `ata-gateway` | 8080 | External REST API gateway |
| `ata-orchestrator` | 8081 | Primary agent + audit trail + outbound MCP |
| `ata-credit-bureau-mcp` | 18081 | Mock MCP server for credit bureau |
| `ata-postgres` | 5432 | PostgreSQL database for audit trail |
| `ata-ollama` | 11434 | Local LLM runtime (Llama 3 8B) |

### Start the Distributed System

```bash
# Build all modules
mvn clean install -DskipTests

# Start all containers
docker-compose up -d

# Wait for Ollama to download the model (first time only, ~5 min)
docker exec ata-ollama ollama pull llama3:8b

# Verify model is ready
docker exec ata-ollama ollama list
```

### End-to-End Loan Evaluation Test

**Request Flow:**
1. Gateway (8080) receives loan request
2. Gateway forwards to Orchestrator (8081)
3. Orchestrator's agent queries Ollama LLM for reasoning
4. Agent makes loan decision with full reasoning
5. Audit trail saved to PostgreSQL (5432)

#### Sample Request

```powershell
# Windows PowerShell
$body = @'
{
  "customerId": "CUST-001",
  "amount": 50000,
  "purpose": "Home Improvement",
  "loanType": "PERSONAL"
}
'@
Invoke-RestMethod -Uri "http://localhost:8080/api/loans/evaluate" `
  -Method POST -ContentType "application/json" -Body $body | ConvertTo-Json -Depth 5
```

```bash
# Linux / macOS
curl -X POST http://localhost:8080/api/loans/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-001",
    "amount": 50000,
    "purpose": "Home Improvement",
    "loanType": "PERSONAL"
  }'
```

#### Sample Response

```json
{
  "applicationId": "e1e6564d-6d0e-4f4e-8e42-68490cb0708d",
  "customerId": "CUST-001",
  "amount": 50000.0,
  "purpose": "Home Improvement",
  "outcome": "APPROVED",
  "confidenceScore": 0.75,
  "reasoning": "The customer has a good credit score of 691 (FAIR tier). Based on the loan amount of $50,000 for Home Improvement purposes, the debt-to-income ratio is acceptable. The customer meets KYC requirements and employment verification passed. Recommendation: APPROVE with standard terms."
}
```

### Sample Logs from End-to-End Test

#### 1. Gateway Logs (ata-gateway:8080)
```
INFO  c.b.a.g.controller.GatewayController : Received loan evaluation request: customerId=CUST-001
INFO  c.b.a.g.controller.GatewayController : Forwarding to orchestrator: http://orchestrator:8081/internal/loans/evaluate
INFO  c.b.a.g.controller.GatewayController : Orchestrator response received: applicationId=e1e6564d-6d0e-4f4e-8e42-68490cb0708d
```

#### 2. Orchestrator Logs (ata-orchestrator:8081)
```
INFO  c.bank.ata.audit.service.AuditService    : Logging session start: sessionId=8ff27546-372e-4d00-beee-cc8bc01cb5ee, applicationId=e1e6564d-6d0e-4f4e-8e42-68490cb0708d
INFO  com.bank.ata.agent.AuditTrailAgent       : Starting loan evaluation for application: e1e6564d-6d0e-4f4e-8e42-68490cb0708d
INFO  com.bank.ata.agent.AuditTrailAgent       : Loan evaluation complete: applicationId=e1e6564d-6d0e-4f4e-8e42-68490cb0708d, outcome=APPROVED
INFO  c.bank.ata.audit.service.AuditService    : Logging session end: sessionId=8ff27546-372e-4d00-beee-cc8bc01cb5ee
INFO  c.bank.ata.audit.service.AuditService    : Logged decision: applicationId=e1e6564d-6d0e-4f4e-8e42-68490cb0708d, outcome=APPROVED, confidence=0.75
```

#### 3. MCP Server Startup (registered tools and resources)
```
INFO  c.bank.ata.mcp.server.McpToolRegistry    : Registered MCP tool: 'get_audit_trail' (AuditMcpTools)
INFO  c.bank.ata.mcp.server.McpToolRegistry    : Registered MCP tool: 'evaluate_loan' (AuditMcpTools)
INFO  c.bank.ata.mcp.server.McpToolRegistry    : Registered MCP tool: 'get_credit_score' (AuditMcpTools)
INFO  c.bank.ata.mcp.server.McpToolRegistry    : Registered MCP tool: 'search_audit_reasoning' (AuditMcpTools)
INFO  c.bank.ata.mcp.server.McpToolRegistry    : Registered MCP tool: 'check_compliance' (AuditMcpTools)
INFO  c.b.ata.mcp.server.McpResourceRegistry   : Registered MCP static resource: 'loan://policies'
INFO  c.b.ata.mcp.server.McpResourceRegistry   : Registered MCP resource template: 'loan://policies/{policyId}'
INFO  c.b.ata.mcp.server.McpResourceRegistry   : Registered MCP resource template: 'audit://trail/{applicationId}'
INFO  com.bank.ata.mcp.McpServerConfig         : MCP server ready — 5 tool(s), 3 resource(s) registered. Endpoints: GET /mcp/sse  POST /mcp/message
```

#### 4. MCP Client Configuration
```
INFO  c.bank.ata.mcp.client.McpClientConfig    : Creating MCP client for Credit Bureau: url=http://credit-bureau-mcp:8080/mock/credit-bureau-mcp/message
```

#### 5. A2A Server Ready
```
INFO  com.bank.ata.a2a.server.A2aController    : A2A server ready: baseUrl=http://localhost:8080/a2a skills=1
```

### Test MCP Credit Bureau Tool Directly

```bash
# JSON-RPC request to list available tools
curl -X POST http://localhost:18081/mock/credit-bureau-mcp/message \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "tools": [{
      "name": "get_credit_score_external",
      "description": "Retrieve credit score from the Credit Bureau for a given customer ID.",
      "inputSchema": {
        "type": "object",
        "properties": {
          "customerId": {
            "type": "string",
            "description": "Unique customer identifier"
          }
        },
        "required": ["customerId"]
      }
    }]
  }
}
```

```bash
# JSON-RPC request to call the credit score tool
curl -X POST http://localhost:18081/mock/credit-bureau-mcp/message \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/call",
    "params": {
      "name": "get_credit_score_external",
      "arguments": {"customerId": "CUST-001"}
    }
  }'
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "content": [{
      "type": "text",
      "text": "{\"customerId\":\"CUST-001\",\"score\":691,\"tier\":\"FAIR\",\"source\":\"MOCK_CREDIT_BUREAU\"}"
    }]
  }
}
```

### Verify Audit Trail in PostgreSQL

#### List Tables
```bash
docker exec ata-postgres psql -U ata -d atadb -c "\dt"
```

**Output:**
```
            List of relations
 Schema |      Name       | Type  | Owner 
--------+-----------------+-------+-------
 public | audit_event     | table | ata
 public | loan_decision   | table | ata
 public | policy_document | table | ata
 public | reasoning_step  | table | ata
 public | tool_call       | table | ata
(5 rows)
```

#### Query Audit Events
```bash
docker exec ata-postgres psql -U ata -d atadb -c \
  "SELECT event_id, application_id, event_type, created_at FROM audit_event ORDER BY created_at DESC LIMIT 5;"
```

**Output:**
```
               event_id               |           application_id           |  event_type   |          created_at           
--------------------------------------+--------------------------------------+---------------+-------------------------------
 738b3bb7-ecef-4d56-8101-1482827a6e7a | e1e6564d-6d0e-4f4e-8e42-68490cb0708d | DECISION      | 2026-04-15 13:41:51.768163+00
 40a523db-09ef-4217-9a0b-72631c4095df | e1e6564d-6d0e-4f4e-8e42-68490cb0708d | SESSION_END   | 2026-04-15 13:41:51.744155+00
 22e4be2a-09ed-4918-a0ea-d790cfd8bb59 | e1e6564d-6d0e-4f4e-8e42-68490cb0708d | SESSION_START | 2026-04-15 13:37:44.204594+00
(3 rows)
```

#### Query Loan Decisions
```bash
docker exec ata-postgres psql -U ata -d atadb -c \
  "SELECT decision_id, application_id, outcome, confidence_score, decided_at FROM loan_decision ORDER BY decided_at DESC LIMIT 5;"
```

**Output:**
```
             decision_id              |           application_id           | outcome  | confidence_score |          decided_at           
--------------------------------------+--------------------------------------+----------+------------------+-------------------------------
 117b77d7-1690-44a7-be17-c662a3839527 | e1e6564d-6d0e-4f4e-8e42-68490cb0708d | APPROVED |           0.7500 | 2026-04-15 13:41:51.769541+00
(1 row)
```

#### Query Decision Reasoning
```bash
docker exec ata-postgres psql -U ata -d atadb -c \
  "SELECT LEFT(reasoning, 300) as reasoning FROM loan_decision WHERE application_id = 'e1e6564d-6d0e-4f4e-8e42-68490cb0708d';"
```

**Output:**
```
                                                          reasoning                                                           
------------------------------------------------------------------------------------------------------------------------------
 ** The customer has a good credit score (680), stable employment with a decent income ($120,000.0), and their KYC status 
 is verified. Additionally, all policies are compliant. The risk score of 0.35 indicates low risk. Recommendation: APPROVE.
(1 row)
```

#### Table Schemas

**audit_event** - Tracks all audit events (session start/end, decisions)
```
   Column      |           Type           | Description
---------------+--------------------------+----------------------------------
 event_id      | uuid                     | Primary key
 application_id| uuid                     | Loan application UUID
 event_type    | varchar(50)              | SESSION_START, SESSION_END, DECISION
 session_id    | uuid                     | Session UUID for grouping events
 created_at    | timestamp with time zone | Event timestamp
```

**loan_decision** - Stores final loan decisions with reasoning
```
      Column      |           Type           | Description
------------------+--------------------------+----------------------------------
 decision_id      | uuid                     | Primary key
 application_id   | uuid                     | Loan application UUID
 outcome          | varchar(50)              | APPROVED, DENIED, MANUAL_REVIEW
 confidence_score | numeric(5,4)             | AI confidence (0.0 - 1.0)
 reasoning        | text                     | LLM-generated reasoning
 session_id       | uuid                     | Session UUID
 decided_at       | timestamp with time zone | Decision timestamp
```

**tool_call** - Records all tool invocations during evaluation
```
      Column       |    Type     | Description
-------------------+-------------+----------------------------------
 tool_call_id      | uuid        | Primary key
 event_id          | uuid        | Foreign key to audit_event
 tool_name         | varchar(100)| Name of tool called
 input_params      | text        | JSON input parameters
 output_result     | text        | JSON output result
 success           | boolean     | Whether call succeeded
 execution_time_ms | bigint      | Execution time in milliseconds
```

### Stop and Clean Up

```bash
docker-compose down      # Stop containers
docker-compose down -v   # Stop and remove volumes
```

**Output:**
```
[+] Running 10/10
 ✔ Container ata-pgadmin            Removed    0.1s 
 ✔ Container ata-gateway            Removed   12.1s 
 ✔ Container ata-grafana            Removed    1.8s 
 ✔ Container ata-ollama-pull        Removed    0.0s 
 ✔ Container ata-prometheus         Removed    1.9s 
 ✔ Container ata-orchestrator       Removed   10.9s 
 ✔ Container ata-ollama             Removed    7.4s 
 ✔ Container ata-credit-bureau-mcp  Removed    5.6s 
 ✔ Container ata-postgres           Removed    1.7s 
 ✔ Network ata-network              Removed    0.4s 
```

## Development

### Running Tests

```bash
# Unit tests only
mvn test

# Specific module
mvn test -pl ata-mcp

# Integration tests (requires Ollama running)
mvn verify -Pintegration

# With coverage
mvn verify jacoco:report
```

### Code Quality

```bash
# Check style
mvn checkstyle:check

# Security scan
mvn org.owasp:dependency-check-maven:check
```

## Architecture

See [Architecture Documentation](audit_trail_agent_architecture.md), [Distributed agentic system (multi-container A2A + MCP)](distributed_agentic_system.md) and [Development Status](development-status.md).

```
┌─────────────────────────────────────────────────────────────────────┐
│                         ATA Architecture (Phase 3)                   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  Claude Desktop ──MCP──▶ GET /mcp/sse                                │
│  IDE Plugin     ──MCP──▶ POST /mcp/message ──▶ McpJsonRpcHandler     │
│                                                    │                  │
│  REST Clients ──▶ POST /api/loans/evaluate         │ tools/call       │
│                              │                     ▼                  │
│                              └──────▶ AuditTrailAgent                │
│                                            │  (LangChain4j)           │
│                                            ▼                          │
│                                     Ollama / vLLM                    │
│                                       (Llama 3)                      │
│                                            │                          │
│                              ┌─────────────┴───────────┐             │
│                              ▼                         ▼             │
│                        Tool Registry           AuditInterceptor      │
│                   getCreditScore()              (AOP / ScopedValue)  │
│                   verifyKYC()                          │              │
│                   checkPolicyCompliance()              ▼              │
│                   calculateRiskScore()          PostgreSQL / H2       │
│                   getEmploymentInfo()           + JVector             │
│                                                                       │
│  MCP Client ──▶ POST /mock/credit-bureau-mcp/message                 │
│              (creditBureauMcpClient bean → MockCreditBureauController)│
└─────────────────────────────────────────────────────────────────────┘
```

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Java 25 LTS |
| Framework | Spring Boot 4.0.5 |
| AI Framework | LangChain4j 0.35.0 |
| LLM (Dev) | Ollama + Llama 3 8B |
| LLM (Prod) | vLLM + Llama 3 70B |
| Database | PostgreSQL 16 / H2 (dev) |
| Vector Store | JVector (embedded) |
| MCP Transport | Custom HTTP + SSE (JSON-RPC 2.0) |
| MCP Protocol | `2024-11-05` |
| Protocols | MCP (Phase 3 ✅), A2A (Phase 4) |
| Observability | Prometheus, Grafana |

## ⚠️ License & Usage Restrictions

This project is published for **viewing purposes only**.

| Action | Allowed? |
|--------|----------|
| View / read the code on GitHub | ✅ Yes |
| Fork on GitHub (to submit a pull request) | ✅ Yes |
| Clone, download, or copy the code locally | ❌ No |
| Use any part of the code in another project | ❌ No |
| Distribute or republish the code | ❌ No |

All rights are reserved by the repository owner. See the [LICENSE](LICENSE) file for full terms.
If you need special permission, please open an issue or contact the owner via GitHub.

## Support

Contact the AI Platform Team via GitHub issues. If you need access to the code then you can create an issue in this repository to get access to code whih is available here: https://github.com/BrajveerSingh/audit-trail-agent

