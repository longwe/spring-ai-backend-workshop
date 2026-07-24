# Security Checklist

## Implemented in this codebase

- [x] **JWT authentication** — HMAC-SHA (jjwt 0.12), 60-min expiry, stateless sessions
- [x] **Password hashing** — BCrypt
- [x] **RBAC** — `USER` vs `ADMIN`; admin endpoints and non-health actuator endpoints require `ROLE_ADMIN`
- [x] **Rate limiting** — Bucket4j token bucket on `/api/**`, keyed by user (or IP for anonymous auth endpoints — covers login brute force)
- [x] **Input validation** — Jakarta Bean Validation on all request DTOs; message size cap (20k chars); upload size cap (20 MB)
- [x] **Resource ownership checks** — conversations are only readable by their owner (404, not 403, to avoid ID probing)
- [x] **Filename sanitization** — path components stripped from uploaded filenames
- [x] **Secure prompt handling** — system prompt is version-controlled (not user-supplied); prompt instructs the model to treat retrieved documents and user text as data, not instructions; prompt/response snippets truncated before audit storage
- [x] **Secrets via environment** — `ANTHROPIC_API_KEY`, `JWT_SECRET`, DB credentials never in code; k8s Secret reference in manifest
- [x] **Error hygiene** — generic 500 body; stack traces only in server logs
- [x] **Non-root container** — Docker image runs as unprivileged user

## Deployment-time obligations (not in code)

- [ ] Terminate TLS at the gateway/ingress; HSTS
- [ ] Rotate `JWT_SECRET` and `ANTHROPIC_API_KEY` via your secrets manager; never use the dev defaults
- [ ] Restrict DB/Redis network access to the app's security group
- [ ] Enable Postgres encryption at rest + TLS in transit
- [ ] Add refresh-token rotation if sessions must outlive 60 minutes
- [ ] Move rate limiting to bucket4j-redis for multi-replica correctness
- [ ] Add CORS configuration if a browser SPA will call the API directly
- [ ] Review prompt-injection posture whenever new tools are added — tools that mutate state should require explicit authorization checks inside the tool method

## LLM-specific risks

| Risk | Mitigation here |
|---|---|
| Prompt injection via uploaded docs | System-prompt guardrails; tools are read-only (no state mutation); output treated as text, never executed |
| Data exfiltration through tools | Tools return only KB passages/counts; no network or filesystem tools exposed |
| Cost abuse | Per-user rate limit + `max-tokens: 4096` cap + token metrics/audit for alerting |
| PII in logs | Prompt/response snippets truncated to 4000 chars; apply retention policy to `ai_logs` |
