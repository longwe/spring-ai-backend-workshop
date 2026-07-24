# Deployment Guide

## Environment variables

| Variable | Required | Default | Notes |
|---|---|---|---|
| `ANTHROPIC_API_KEY` | yes | — | Claude API key |
| `JWT_SECRET` | yes (prod) | dev value | Base64, >= 256 bits |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | yes | localhost dev values | Postgres must have the `vector` extension available (pgvector) |
| `REDIS_HOST` / `REDIS_PORT` | when `CACHE_TYPE=redis` | localhost:6379 | |
| `CACHE_TYPE` | no | `simple` | `redis` in deployed envs |
| `RATE_LIMIT_RPM` | no | 60 | per-user requests/minute |
| `JWT_EXPIRATION_MINUTES` | no | 60 | |

## Local

```bash
ANTHROPIC_API_KEY=sk-ant-... docker compose up --build
```
Compose runs `pgvector/pgvector:pg16`, `redis:7`, and the app. Flyway creates the
business schema; Spring AI creates `vector_store` (incl. `CREATE EXTENSION vector`)
and the chat-memory table on first boot.

## CI/CD

`.github/workflows/ci.yml`: build + unit tests on every PR; Docker image build on
main. Add your registry push (ECR/GHCR) and a deploy step (e.g. `kubectl set image`
or Argo CD sync) where marked.

Pipeline: Code commit → Build → Test → Docker image → Deploy.

## Kubernetes

`k8s/deployment.yaml` provides a 2-replica Deployment + Service with
liveness/readiness probes wired to Actuator health groups. Create the secret first:

```bash
kubectl create secret generic ai-platform-secrets \
  --from-literal=ANTHROPIC_API_KEY=sk-ant-... \
  --from-literal=JWT_SECRET=$(openssl rand -base64 32) \
  --from-literal=DB_HOST=... --from-literal=DB_PASSWORD=... \
  --from-literal=REDIS_HOST=...
kubectl apply -f k8s/deployment.yaml
```

The app is stateless — HPA on CPU or on the `ai.chat.latency` metric works without
session affinity. Postgres (with pgvector) and Redis should be managed services.

## First-boot notes

- The ONNX embedding model (~90 MB) downloads on first startup; bake it into the
  image or mount a cache volume to avoid cold-start delay.
- Migration order: Flyway runs before Spring AI initializers; both are idempotent.
