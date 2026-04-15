# ADR-001: Technology Stack Selection

**Date**: 2026-04-07  
**Status**: Accepted  
**Deciders**: Architecture Team

## Context

We need to select the technology stack for the Audit Trail Agent, a bank-grade AI system for loan evaluation with full auditability requirements.

### Requirements

1. **Regulatory Compliance**: Complete audit trail of all AI decisions
2. **Data Privacy**: No data can leave the bank's network
3. **Performance**: Handle 500+ concurrent loan evaluations
4. **Maintainability**: Use enterprise Java standards
5. **Scalability**: Support multi-agent orchestration

## Decision

### Programming Language: Java 25 LTS

**Rationale**:
- Long-Term Support until 2033 (8 years of updates)
- Virtual Threads (Project Loom) for high concurrency
- Enterprise standard in banking
- Strong typing and compile-time safety
- Mature ecosystem and tooling

**Alternatives Considered**:
- Python: Better AI/ML ecosystem, but weaker enterprise support
- Kotlin: Good, but smaller talent pool in banking sector
- Java 21 LTS: Also viable, but Java 25 LTS offers additional improvements

### Framework: Spring Boot 3.3

**Rationale**:
- Industry standard for enterprise Java
- Excellent security features
- Native observability support
- GraalVM native image support

### AI Framework: LangChain4j

**Rationale**:
- Native Java implementation
- Built-in tool calling support
- Memory management included
- Active development community

**Alternatives Considered**:
- Semantic Kernel: Microsoft-focused, less Java support
- Custom implementation: Higher maintenance burden

### LLM Runtime: Ollama (Dev) / vLLM (Prod)

**Rationale**:
- Ollama: Easy local development setup
- vLLM: Production-grade throughput (PagedAttention)
- Both run models locally (data stays in VPC)
- OpenAI-compatible API for easy switching

**Alternatives Considered**:
- TGI (Text Generation Inference): Good, but less community adoption
- Cloud LLMs (GPT-4, Claude): Data privacy concerns

### Model: Llama 3 (8B dev / 70B prod)

**Rationale**:
- Open weights, no licensing concerns
- Strong reasoning capabilities
- Multiple size options for dev/prod
- Active community and fine-tuning options

### Database: PostgreSQL 16

**Rationale**:
- ACID compliance for audit integrity
- JSONB for flexible audit data
- Row-level security for access control
- Proven at scale in banking

### Protocols: MCP + A2A

**Rationale**:
- MCP: Industry standard for tool exposure
- A2A: Google's agent collaboration protocol
- Both designed for enterprise AI agents

### Vector Database: JVector

**Rationale**:
- Pure Java, embedded in application
- No external dependencies
- Suitable for policy/decision similarity search

## Consequences

### Positive
- Consistent Java ecosystem
- Strong security posture
- Enterprise-grade observability
- Local-first data processing

### Negative
- LangChain4j less mature than Python version
- vLLM requires GPU infrastructure investment

### Risks
- LangChain4j API changes (mitigate: pin versions)
- vLLM scaling challenges (mitigate: early load testing)
- MCP/A2A protocol evolution (mitigate: abstraction layers)

## Related Decisions

- ADR-002: Audit Trail Immutability (pending)
- ADR-003: Multi-Agent Orchestration Pattern (pending)

## References

- [LangChain4j Documentation](https://docs.langchain4j.dev/)
- [vLLM Project](https://github.com/vllm-project/vllm)
- [MCP Specification](https://modelcontextprotocol.io/)
- [A2A Protocol](https://github.com/google/a2a-spec)

