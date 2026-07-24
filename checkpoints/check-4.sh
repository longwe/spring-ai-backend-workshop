#!/usr/bin/env bash
# Checkpoint 4 — RAG: upload a document, then get an answer grounded in it,
# with source references.
set -uo pipefail
cd "$(dirname "$0")"
source ./common.sh

require_app
get_token

# --- upload ---------------------------------------------------------------
up=$(curl -s -X POST "$BASE_URL/api/documents/upload" \
  -H "Authorization: Bearer $TOKEN" -F "file=@sample-doc.md")
printf '%s' "$up" | grep -q '"chunkCount"' \
  || fail "upload failed: $up" "complete TODO 4.1 and 4.2 (chunk + embed + store), then restart the app"
pass "document ingested: $(json_field "$up" filename)"

# --- grounded answer ------------------------------------------------------
resp=$(curl -s -X POST "$BASE_URL/api/chat" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"message":"According to the knowledge base, what is the maximum number of read replicas ZephyrDB Enterprise supports? Answer with just the number."}')

case "$resp" in
  *42*) pass "answer is grounded in the uploaded document (found the magic number)" ;;
  *)    fail "answer not grounded. response: $resp" \
             "complete TODO 4.3 (QuestionAnswerAdvisor on the ChatClient), then restart the app" ;;
esac

printf '%s' "$resp" | grep -q 'sample-doc' \
  && pass "response cites its source document" \
  || echo "ℹ️  no source citation found (extractSources) — optional stretch goal"
