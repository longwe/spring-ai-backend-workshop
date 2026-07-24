# Spring AI Workshop — From Empty Bean to Production-Shaped AI Service

Two hours, five modules, one runnable enterprise AI backend. Every module ends
with a checkpoint script that tells you ✅ or ❌ — no guessing.

## Before you arrive (do this at home!)

1. Install: **Docker Desktop** and a **JDK 21** ([adoptium.net](https://adoptium.net)).
   No Maven needed — the repo ships its own wrapper.
2. Clone this repo and run the setup check on good wifi (it pre-downloads
   dependencies, Docker images, and the ~90 MB local embedding model):

   ```bash
   ANTHROPIC_API_KEY=sk-ant-...  ./scripts/verify-setup.sh
   ```

   You need it to print `🎉 All checks passed`. If it doesn't, fix the ❌ lines
   or bring the output to the workshop and we'll sort it out first thing.

## Running the app

```bash
docker compose up -d postgres redis          # infra
ANTHROPIC_API_KEY=sk-ant-... ./mvnw spring-boot:run
```

All API calls need a JWT. Get one any time with:

```bash
TOKEN=$(scripts/token.sh)
curl -s localhost:8080/api/chat -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"message":"hello"}'
```

**Restart the app after completing each TODO** — then run the module's
checkpoint script.

## Where the TODOs are

Your work is marked `TODO(n.m)` in the code. Everything else — security, rate
limiting, persistence, controllers, metrics — is finished and working. Find
your next task with:

```bash
grep -rn "TODO(" src/main/java
```

Fallen behind? Grab any finished file from the solution branch and keep going:

```bash
git fetch origin solution
git checkout origin/solution -- src/main/java/com/ezcloud/config/AiConfig.java
```

---

## Module 1 — Your first ChatClient (~20 min)

AI as just another Spring bean: build the `ChatClient` and make one blocking
round trip to Claude.

| TODO | File | Task |
|------|------|------|
| 1.1 | `config/AiConfig.java` | Give the client its persona: `.defaultSystem(readResource(systemPrompt))` on the builder |
| 1.2 | `service/ChatService.callModel` | The round trip: `chatClient.prompt().user(...).tools(agentTools).call().chatClientResponse()` |

<details><summary>Hint 1.2</summary>

```java
return chatClient.prompt()
        .user(request.message())
        .tools(agentTools)          // function-calling toolbox (already written)
        .call()
        .chatClientResponse();
```
</details>

**Checkpoint:** `./checkpoints/check-1.sh`

---

## Module 2 — Prompt templates + structured output (~20 min)

Typed objects, not string-wrangling: `/api/triage` turns a rant into a
`TicketTriage` record with enum fields.

| TODO | File | Task |
|------|------|------|
| 2.1 | `service/TriageService.triage` | Render `prompts/triage.st` with a `PromptTemplate`, then ask for a typed result with `.entity(TicketTriage.class)` |

<details><summary>Hint 2.1</summary>

```java
var prompt = new PromptTemplate(triagePrompt)
        .render(Map.of("message", request.message()));
return chatClient.prompt().user(prompt).call().entity(TicketTriage.class);
```
Spring AI appends JSON-schema format instructions to your prompt and converts
the response — look at the console log to see what it actually sent.
</details>

**Checkpoint:** `./checkpoints/check-2.sh`

---

## Module 3 — Streaming + conversation memory (~25 min)

Tokens as they're generated, and a model that remembers the conversation.

| TODO | File | Task |
|------|------|------|
| 3.1 | `service/ChatService.chatStream` | Return the answer as a `Flux<String>`: same prompt chain as 1.2, but `.stream().content()` |
| 3.2 | `config/AiConfig.java` + `ChatService` | Wire memory: add `MessageChatMemoryAdvisor.builder(chatMemory).build()` to the client's `.defaultAdvisors(...)`, and route each request to its conversation with `.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversation.getId().toString()))` in **both** `callModel` and `chatStream` |

<details><summary>Hint 3.1 (minimal version)</summary>

```java
return chatClient.prompt()
        .user(request.message())
        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversation.getId().toString()))
        .tools(agentTools)
        .stream()
        .content();
```
The solution also aggregates the chunks and writes an audit log on completion —
compare with `origin/solution` after the checkpoint passes.
</details>

Watch it stream: `curl -N -X POST localhost:8080/api/chat/stream -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"message":"Tell me a short story"}'`

**Checkpoint:** `./checkpoints/check-3.sh`

---

## Module 4 — RAG with PostgreSQL + pgvector (~30 min)

Answers grounded in *your* documents. The upload endpoint, Tika parsing, and
pgvector schema are done — you build the pipeline core and switch on retrieval.

| TODO | File | Task |
|------|------|------|
| 4.1 | `service/DocumentService.chunkDocument` | Split parsed pages into chunks: `new TokenTextSplitter().apply(parsed)` |
| 4.2 | `service/DocumentService.storeChunks` | Stamp each chunk's metadata (`documentId`, `filename`), then `vectorStore.add(chunks)` — this is where embedding happens |
| 4.3 | `config/AiConfig.java` | Ground the chat client: add a `QuestionAnswerAdvisor` built with `.searchRequest(SearchRequest.builder().topK(5).similarityThreshold(0.4).build())` to `.defaultAdvisors(...)` |

**Checkpoint:** `./checkpoints/check-4.sh` — uploads `checkpoints/sample-doc.md`
(a fictional product FAQ) and asks a question only that document can answer.

**Stretch:** look at `ChatService.extractSources` — the response already cites
which document chunks grounded the answer.

---

## Module 5 — Production hardening (~15 min)

Retries, circuit breakers, and graceful degradation. Rate limiting (bucket4j),
caching, and Prometheus metrics are already live — we'll tour them; you wire
the resilience.

| TODO | File | Task |
|------|------|------|
| 5.1 | `service/ChatService.chat` | Annotate with `@Retry(name = "ai")` and `@CircuitBreaker(name = "ai", fallbackMethod = "chatFallback")` — the `resilience4j:` config in `application.yaml` is provided; read it |
| 5.2 | `service/ChatService.chatFallback` | Rethrow business exceptions (`NotFoundException`, `IllegalArgumentException`) so they keep their HTTP mapping; otherwise return a degraded `ChatResponse` with `"degraded": true` metadata |

**Checkpoint:** restart with a broken key — `ANTHROPIC_API_KEY=broken ./mvnw spring-boot:run` —
then `./checkpoints/check-5.sh`. A graceful 200, not a 500. Restart with your
real key afterwards.

**Tour points:** `RateLimitFilter` (hammer any endpoint 61× in a minute),
`@Cacheable` on document listing, `curl localhost:8080/actuator/prometheus | grep ai_`
for token counters and latency histograms.

---

## If you finish early

- Add a new `@Tool` to `AgentTools` and watch the model decide to call it.
- Change `topK`/`similarityThreshold` in the QA advisor and observe answer quality.
- Open the circuit by hand: 5+ failing calls, then watch `/actuator/metrics/resilience4j.circuitbreaker.state`.
