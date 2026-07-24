#!/usr/bin/env bash
# Checkpoint 5 — Production hardening: when the model is unreachable, chat
# degrades gracefully (HTTP 200 + fallback message) instead of a 500.
#
# Run this one with a BROKEN key on purpose:
#   1. stop the app
#   2. ANTHROPIC_API_KEY=broken ./mvnw spring-boot:run
#   3. ./checkpoints/check-5.sh
#   4. restart with your real key afterwards
set -uo pipefail
cd "$(dirname "$0")"
source ./common.sh

require_app
get_token

status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/chat" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"message":"hello"}')
resp=$(curl -s -X POST "$BASE_URL/api/chat" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"message":"hello"}')

case "$resp" in
  *'"degraded":true'*)
    pass "circuit breaker fallback returned a graceful degraded response (HTTP $status)" ;;
  *"WORKSHOP"*|*capital*|*[Hh]ello*)
    fail "got a real model answer — this checkpoint needs a broken key" \
         "restart the app with: ANTHROPIC_API_KEY=broken ./mvnw spring-boot:run" ;;
  *)
    [ "$status" = "500" ] \
      && fail "chat returned HTTP 500 — the failure is not being handled" \
              "complete TODO 5.1 (@Retry/@CircuitBreaker) and 5.2 (fallback), then restart" \
      || fail "unexpected response (HTTP $status): $resp" \
              "complete TODO 5.1 and 5.2, then restart the app" ;;
esac

echo
echo "Don't forget to restart the app with your real ANTHROPIC_API_KEY."
