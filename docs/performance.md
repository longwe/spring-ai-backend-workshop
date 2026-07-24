# Performance & Cost Optimization

## Where time goes

A chat request ≈ embedding the query (~5 ms, local ONNX) + PGVector HNSW search
(ms at moderate scale) + Claude generation (seconds, dominates). Optimize
generation first.

## LLM cost/latency levers

- **`max-tokens`** (application.yaml): hard output cap per call — first cost control.
- **Model tier**: `claude-opus-4-8` for quality; drop specific routes to a cheaper
  Sonnet/Haiku tier by adding a second ChatClient bean if you build high-volume,
  low-complexity endpoints (classification, tagging).
- **Memory window**: `MEMORY_WINDOW_MESSAGES = 20` in AiConfig bounds prompt growth
  per conversation.
- **RAG top-K / threshold**: `RAG_TOP_K = 5`, threshold 0.4 — lower K to cut input
  tokens, raise threshold to drop weak context.
- **Token accounting**: every call records input/output tokens in `ai_logs` and the
  `ai.tokens` Prometheus counters — build cost dashboards/alerts from these.

## Application levers

- **Caching**: document listing is `@Cacheable` (Redis in deployed envs). Add
  caching to any read-heavy endpoint you introduce.
- **Vector search**: HNSW index configured in Spring AI's pgvector settings; ensure
  `work_mem` and `maintenance_work_mem` are tuned for index builds at scale.
- **Connection pooling**: HikariCP defaults are fine to start; size pool ≈ cores × 2
  for LLM-bound workloads (threads mostly wait on Anthropic I/O).
- **Horizontal scale**: stateless app; scale replicas. Move rate limiting to
  bucket4j-redis so limits are cluster-wide.
- **Chunking**: `TokenTextSplitter` defaults (~800 tokens/chunk). Smaller chunks →
  more precise retrieval but more rows; tune with your corpus.

## Load testing

Drive `POST /api/chat` with realistic conversation mixes (new vs continuing) —
continuing conversations carry the memory window and cost more input tokens.
Watch: `ai.chat.latency` (p95), `ai.tokens` rate, Hikari pool saturation, and
Anthropic 429s (add retry/backoff via Spring AI's retry properties if you hit
provider limits).
