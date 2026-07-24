#!/usr/bin/env bash
# Pre-workshop environment check. RUN THIS AT HOME, ON GOOD WIFI, BEFORE THE
# WORKSHOP: it downloads Maven, all dependencies, the Docker images, and the
# local embedding model (~90 MB) so nothing large downloads on venue wifi.
#
# Usage: ANTHROPIC_API_KEY=sk-ant-... ./scripts/verify-setup.sh
set -uo pipefail
cd "$(dirname "$0")/.."

failures=0
step() { printf '\n== %s\n' "$1"; }
good() { printf '✅ %s\n' "$1"; }
bad()  { printf '❌ %s\n' "$1"; failures=$((failures+1)); }

step "1/6 Docker daemon"
if docker info >/dev/null 2>&1; then
  good "Docker is running"
else
  bad "Docker is not running — install & start Docker Desktop (https://docker.com)"
fi

step "2/6 Postgres (pgvector) + Redis containers"
if docker compose up -d postgres redis >/dev/null 2>&1; then
  healthy=""
  for _ in $(seq 1 30); do
    state=$(docker compose ps postgres --format '{{.Health}}' 2>/dev/null)
    [ "$state" = "healthy" ] && healthy=yes && break
    sleep 2
  done
  [ -n "$healthy" ] && good "postgres + redis are up" || bad "postgres container did not become healthy"
else
  bad "docker compose failed — is Docker running?"
fi

step "3/6 Java 21 toolchain + Maven wrapper + dependencies"
if ./mvnw -q -DskipTests compile >/dev/null 2>&1; then
  good "project compiles (JDK 21 toolchain found, dependencies downloaded)"
else
  bad "compile failed — install a JDK 21 (https://adoptium.net), then rerun"
fi

step "4/6 Unit tests"
if ./mvnw -q -Dsurefire.printSummary=false test >/dev/null 2>&1; then
  good "unit tests pass"
else
  bad "unit tests failed — run ./mvnw test to see details"
fi

step "5/6 Anthropic API key"
if [ -z "${ANTHROPIC_API_KEY:-}" ]; then
  bad "ANTHROPIC_API_KEY is not set — run: ANTHROPIC_API_KEY=sk-ant-... ./scripts/verify-setup.sh"
else
  ping=$(curl -s https://api.anthropic.com/v1/messages \
    -H "x-api-key: $ANTHROPIC_API_KEY" \
    -H "anthropic-version: 2023-06-01" \
    -H "content-type: application/json" \
    -d '{"model":"claude-haiku-4-5-20251001","max_tokens":8,"messages":[{"role":"user","content":"ping"}]}')
  case "$ping" in
    *'"content"'*) good "API key works (live call to Anthropic succeeded)" ;;
    *) bad "API call failed — check the key. Server said: $(printf '%s' "$ping" | head -c 200)" ;;
  esac
fi

step "6/6 Full application boot (downloads the embedding model on first run)"
BOOT_LOG=$(mktemp)
./mvnw -q spring-boot:run >"$BOOT_LOG" 2>&1 &
started=""
for _ in $(seq 1 60); do   # up to 5 minutes: first boot downloads the ONNX model
  grep -q "Started AiPlatformApplication" "$BOOT_LOG" && started=yes && break
  grep -q "APPLICATION FAILED TO START" "$BOOT_LOG" && break
  sleep 5
done
if [ -n "$started" ]; then
  good "application boots (embedding model is now cached locally)"
else
  bad "application did not start — see log: $BOOT_LOG"
fi
lsof -ti:8080 2>/dev/null | xargs kill 2>/dev/null
docker compose stop >/dev/null 2>&1

echo
if [ "$failures" -eq 0 ]; then
  echo "🎉 All checks passed — you're ready for the workshop!"
else
  echo "⚠️  $failures check(s) failed — fix the ❌ items above and rerun."
  exit 1
fi
