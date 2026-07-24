#!/usr/bin/env bash
# Checkpoint 1 — Your first ChatClient: POST /api/chat answers via the model.
set -uo pipefail
cd "$(dirname "$0")"
source ./common.sh

require_app
get_token

resp=$(curl -s -X POST "$BASE_URL/api/chat" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"message":"Reply with exactly the two words: WORKSHOP OK"}')

case "$resp" in
  *"WORKSHOP OK"*)
    pass "the model answered through your ChatClient" ;;
  *temporarily\ unavailable*)
    fail "got the resilience fallback, not a real answer" "is ANTHROPIC_API_KEY set to a valid key?" ;;
  *TODO*|*Unsupported*)
    fail "hit an unimplemented TODO" "complete TODO 1.1 and 1.2, then restart the app" ;;
  *)
    fail "unexpected response: $resp" "complete TODO 1.1 and 1.2, then restart the app" ;;
esac
