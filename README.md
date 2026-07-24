> **👋 Workshop attendees: start with [WORKSHOP.md](WORKSHOP.md).** This README
> describes the finished application you will have built by the end of the session.

# AI Platform — Spring AI Backend

Production-grade AI backend: Claude-powered chat with conversation memory, RAG over
uploaded documents (PGVector), LLM tool calling (agents), JWT security with RBAC,
rate limiting, and full observability.

## System Architecture

```
Client (web / mobile / API consumer)
        |
API Gateway / Ingress (TLS termination, k8s Service)
        |
Spring Boot Service (this app)
  ├── Controller layer   — REST endpoints, validation, RBAC
  ├── Service layer      — AuthService, ChatService, DocumentService, AgentTools
  ├── AI orchestration   — Spring AI ChatClient
  │     ├── MessageChatMemoryAdvisor  (conversation memory, JDBC-backed)
  │     ├── QuestionAnswerAdvisor     (RAG: similarity search + context injection)
  │     └── @Tool functions           (calculator, KB search, doc count, datetime)
  ├── LLM provider       — Anthropic Claude (claude-opus-4-8)
  ├── Embeddings         — local ONNX all-MiniLM-L6-v2 (384-dim; Anthropic has no embeddings API)
  ├── Vector database    — PostgreSQL + pgvector (HNSW, cosine distance)
  └── Business database  — PostgreSQL (users, documents, conversations, ai_logs; Flyway-managed)
        |
Redis — response caching (document listing), swap-in point for distributed rate limiting
```

## Requirements

**Business**: let authenticated users converse with an assistant grounded in the
organization's documents, with auditable AI usage and cost visibility.

**Functional**: register/login (JWT); multi-turn chat with per-conversation memory;
document upload → parse → chunk → embed → vector search; RAG-cited answers; agent
tool calling; admin user listing and system status.

**Non-functional**: stateless app tier (horizontally scalable — all state in
Postgres/Redis); p50 chat latency dominated by LLM generation; observability via
Actuator + Prometheus (token counters, latency timer); every LLM call audited in
`ai_logs`.

**Scalability**: scale replicas freely (JWT stateless, memory in JDBC, vectors in
Postgres). PGVector HNSW handles millions of chunks; beyond that, swap the
`VectorStore` bean for a dedicated engine (Weaviate/Pinecone) without touching
service code. Rate-limit buckets are in-memory per instance — move to
bucket4j-redis for cluster-wide limits.

**Security**: see `docs/security-checklist.md`.

## RAG Pipeline

```
POST /api/documents/upload
  → Tika parse (PDF/DOCX/TXT/HTML…)
  → TokenTextSplitter chunking
  → ONNX embedding (all-MiniLM-L6-v2)
  → PGVector storage (metadata: documentId, filename)

POST /api/chat
  → QuestionAnswerAdvisor: embed query → top-K similarity search → inject context
  → Claude generates grounded answer (may also call tools)
  → response includes sources[] + token/latency metadata
```

## Agent Workflow

The ChatClient acts as the planner: Claude receives the tool schemas
(auto-generated from `@Tool` annotations in `AgentTools`) and decides per
request whether to call `calculator`, `searchKnowledgeBase`, `documentCount`,
or `currentDateTime`. Spring AI executes the tool round trips transparently;
conversation history (agent memory) is preserved by the JDBC chat memory.

## Quick Start

```bash
# Full stack (Postgres + Redis + app)
ANTHROPIC_API_KEY=sk-ant-... docker compose up --build

# Or run locally against your own Postgres (pgvector required)
export ANTHROPIC_API_KEY=sk-ant-...
mvn spring-boot:run

# Tests (no DB/network needed)
mvn test
```

First run downloads the ONNX embedding model (~90 MB) to the local cache.

```bash
# Register, then chat
curl -s localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"alice","email":"alice@example.com","password":"changeme123"}'
TOKEN=<token from response>
curl -s localhost:8080/api/chat -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"message":"What is (12.5*4)-3?"}'
curl -s localhost:8080/api/documents/upload -H "Authorization: Bearer $TOKEN" \
  -F file=@handbook.pdf
```

## Key Files

| Path | Role |
|---|---|
| `config/AiConfig.java` | ChatClient + advisors + chat memory wiring |
| `config/SecurityConfig.java` | JWT filter chain, RBAC rules |
| `config/RateLimitFilter.java` | Bucket4j token-bucket limiting on /api/** |
| `service/ChatService.java` | Chat orchestration, source extraction, AI audit logging |
| `service/DocumentService.java` | RAG ingestion/deletion pipeline |
| `service/AgentTools.java` | `@Tool` functions for LLM function calling |
| `resources/prompts/system-rag.st` | System prompt (version-controlled) |
| `resources/db/migration/V1__init.sql` | Business schema (Flyway) |

## Docs

- [API specification](docs/api-spec.md)
- [Security checklist](docs/security-checklist.md)
- [Deployment guide](docs/deployment.md)
- [Performance & cost optimization](docs/performance.md)
