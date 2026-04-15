# Development Environment Setup

This guide will help you set up your local development environment for the Audit Trail Agent.

## Prerequisites

### Required Software

| Software | Version | Download |
|----------|---------|----------|
| Java JDK | 25 LTS | [Eclipse Temurin](https://adoptium.net/) |
| Maven | 3.9+ | [Apache Maven](https://maven.apache.org/) |
| Docker | 24+ | [Docker Desktop](https://www.docker.com/) |
| Git | 2.40+ | [Git SCM](https://git-scm.com/) |
| IDE | IntelliJ IDEA / VS Code | [IntelliJ](https://www.jetbrains.com/idea/) |

### System Requirements

- **CPU**: 4+ cores recommended
- **RAM**: 16GB minimum (32GB recommended for Ollama)
- **Disk**: 50GB free space (for Docker images and models)
- **GPU**: Optional (NVIDIA GPU for faster Ollama inference)

## Step-by-Step Setup

### 1. Install Java 25 LTS

```powershell
# Windows (using winget)
winget install EclipseAdoptium.Temurin.25.JDK

# Verify installation
java -version
# Expected: openjdk version "25" 2025-09-16 LTS
```

### 2. Install Maven

```powershell
# Windows (using winget)
winget install Apache.Maven

# Verify installation
mvn -version
```

### 3. Clone the Repository

```bash
git clone https://github.com/bank/audit-trail-agent.git
cd audit-trail-agent
```

### 4. Start Docker Services

```bash
# Start all services (Ollama, PostgreSQL, Prometheus, Grafana)
docker-compose up -d

# Check service status
docker-compose ps

# View Ollama logs (first run will pull the model)
docker-compose logs -f ollama-pull
```

**Note**: First-time setup will download the Llama 3 8B model (~5GB). This may take 5-10 minutes.

### 5. Verify Ollama

```bash
# Check Ollama is running
curl http://localhost:11434/api/tags

# Expected response:
# {"models":[{"name":"llama3:8b",...}]}
```

### 6. Build the Project

```bash
# Full build with tests
mvn clean install

# Quick build (skip tests)
mvn clean install -DskipTests
```

### 7. Run the Application

```bash
cd ata-app
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 8. Verify the Application

```bash
# Health check
curl http://localhost:8080/api/health

# Expected:
# {"status":"UP","application":"Audit Trail Agent",...}
```

## Service URLs

| Service | URL | Credentials |
|---------|-----|-------------|
| ATA Application | http://localhost:8080 | - |
| Ollama API | http://localhost:11434 | - |
| PostgreSQL | localhost:5432 | ata / ata_dev_password |
| PgAdmin | http://localhost:5050 | admin@bank.local / admin |
| Prometheus | http://localhost:9090 | - |
| Grafana | http://localhost:3001 | admin / admin |
| H2 Console (dev) | http://localhost:8080/h2-console | sa / (blank) |

## MCP Integration (Phase 3)

### Verify the MCP Server

```bash
# MCP server liveness probe
curl http://localhost:8080/mcp/health

# Expected:
# {"status":"UP","server":"audit-trail-agent","version":"1.0.0","protocol":"2024-11-05","activeSessions":0}
```

### Test MCP via curl (JSON-RPC 2.0)

```bash
# 1. Initialize session
curl -s -X POST http://localhost:8080/mcp/message \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","clientInfo":{"name":"curl","version":"1.0"},"capabilities":{}}}'

# 2. List available tools (5 total)
curl -s -X POST http://localhost:8080/mcp/message \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'

# 3. List available resources
curl -s -X POST http://localhost:8080/mcp/message \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":3,"method":"resources/list","params":{}}'

# 4. Read a loan policy resource
curl -s -X POST http://localhost:8080/mcp/message \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":4,"method":"resources/read","params":{"uri":"loan://policies/standard"}}'

# 5. Call the get_credit_score tool
curl -s -X POST http://localhost:8080/mcp/message \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"get_credit_score","arguments":{"customerId":"CUST001"}}}'

# 6. Call check_compliance tool
curl -s -X POST http://localhost:8080/mcp/message \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"check_compliance","arguments":{"amount":50000,"purpose":"HOME_IMPROVEMENT","creditScore":720}}}'
```

### Mock Credit Bureau MCP

The built-in mock credit bureau is available for development and testing:

```bash
# Health check
curl http://localhost:8080/mock/credit-bureau-mcp/health

# List tools
curl -s -X POST http://localhost:8080/mock/credit-bureau-mcp/message \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'

# Call the external credit score tool
curl -s -X POST http://localhost:8080/mock/credit-bureau-mcp/message \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"get_credit_score_external","arguments":{"customerId":"CUST001"}}}'
```

To point the MCP client at a real (non-mock) credit bureau service, override in `application.yml`:

```yaml
mcp:
  client:
    credit-bureau:
      url: https://credit-bureau-mcp.bank.internal/mcp/message
```

### Claude Desktop Integration

1. Start the ATA application (`mvn spring-boot:run -Dspring-boot.run.profiles=dev`)
2. Open (or create) the Claude Desktop config file:
   - **macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
   - **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`
3. Add the ATA MCP server:

```json
{
  "mcpServers": {
    "audit-trail-agent": {
      "url": "http://localhost:8080/mcp/sse"
    }
  }
}
```

4. Restart Claude Desktop
5. The following will be available automatically:
   - **5 tools**: `evaluate_loan`, `get_audit_trail`, `search_audit_reasoning`, `get_credit_score`, `check_compliance`
   - **3 resources**: `loan://policies`, `loan://policies/{policyId}`, `audit://trail/{applicationId}`

## IDE Setup

### IntelliJ IDEA

1. Open IntelliJ IDEA
2. File → Open → Select `audit-trail-agent` folder
3. Trust the project when prompted
4. Wait for Maven to import dependencies
5. Install recommended plugins:
   - Spring Boot
   - Lombok (if used)
   - Docker

### VS Code

1. Open VS Code
2. File → Open Folder → Select `audit-trail-agent`
3. Install recommended extensions:
   - Extension Pack for Java
   - Spring Boot Extension Pack
   - Docker

## Running Tests

```bash
# Run all unit tests
mvn test

# Run specific module tests
mvn test -pl ata-core

# Run with coverage report
mvn verify jacoco:report

# View coverage report
open ata-core/target/site/jacoco/index.html
```

## Troubleshooting

### Ollama Not Starting

```bash
# Check Ollama logs
docker-compose logs ollama

# Restart Ollama
docker-compose restart ollama

# Check if port 11434 is in use
netstat -ano | findstr :11434
```

### Model Not Downloaded

```bash
# Manually pull the model
docker exec -it ata-ollama ollama pull llama3:8b

# Verify model exists
docker exec -it ata-ollama ollama list
```

### Build Failures

```bash
# Clean build
mvn clean install -U

# Skip tests if they're failing
mvn clean install -DskipTests

# Check Java version
java -version
```

### Port Conflicts

If default ports are in use, modify `docker-compose.yml`:

```yaml
services:
  ollama:
    ports:
      - "11435:11434"  # Use different port
```

## Next Steps

1. Read the [Architecture Documentation](../audit_trail_agent_architecture.md)
2. Review the [Development Plan](../ata_dev_plan.md)
3. Check the [Development Status](../development-status.md) for what's implemented
4. **Phase 3 complete** — try the MCP integration with Claude Desktop (see above)
5. **Next up: Phase 4 A2A** — multi-agent collaboration

