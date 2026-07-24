#!/usr/bin/env bash
# Checkpoint 3 — Streaming + conversation memory:
#   a) /api/chat/stream delivers multiple SSE chunks
#   b) a follow-up message in the same conversation remembers earlier facts
set -uo pipefail
cd "$(dirname "$0")"
source ./common.sh

require_app
get_token

# --- a) streaming ---------------------------------------------------------
chunks=$(curl -sN --max-time 90 -X POST "$BASE_URL/api/chat/stream" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"message":"Count from 1 to 10, one number per line."}' | grep -c '^data:')

if [ "$chunks" -ge 2 ]; then
  pass "streaming: received $chunks SSE chunks"
else
  fail "streaming: expected several data: chunks, got $chunks" \
       "complete TODO 3.1 (.stream().content() as a Flux), then restart the app"
fi

# --- b) memory ------------------------------------------------------------
first=$(curl -s -X POST "$BASE_URL/api/chat" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"message":"My favorite color is chartreuse. Just acknowledge briefly."}')
conv=$(json_field "$first" conversationId)
[ -n "$conv" ] && [ "$conv" != "$first" ] || fail "no conversationId in chat response"

second=$(curl -s -X POST "$BASE_URL/api/chat" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"message\":\"What is my favorite color? One word only.\",\"conversationId\":\"$conv\"}")

case "$second" in
  *hartreuse*)
    pass "memory: the model remembered the earlier message in this conversation" ;;
  *)
    fail "memory: model did not recall the fact. response: $second" \
         "complete TODO 3.2 (MessageChatMemoryAdvisor bean wiring + conversation-id advisor param)" ;;
esac
