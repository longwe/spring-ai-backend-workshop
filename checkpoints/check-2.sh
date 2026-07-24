#!/usr/bin/env bash
# Checkpoint 2 — Prompt template + structured output: /api/triage returns a
# typed TicketTriage with enum fields, not free text.
set -uo pipefail
cd "$(dirname "$0")"
source ./common.sh

require_app
get_token

resp=$(curl -s -X POST "$BASE_URL/api/triage" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"message":"Our production dashboard is down and we are losing orders every minute!"}')

echo "response: $resp"

printf '%s' "$resp" | grep -qE '"category":"(BILLING|TECHNICAL|ACCOUNT|GENERAL)"' \
  || fail "no valid category enum in response" "complete TODO 2.1 (render the template + .entity(TicketTriage.class))"
printf '%s' "$resp" | grep -qE '"priority":"(LOW|MEDIUM|HIGH|URGENT)"' \
  || fail "no valid priority enum in response" "complete TODO 2.1"
printf '%s' "$resp" | grep -q '"suggestedReply":"' \
  || fail "no suggestedReply in response" "complete TODO 2.1"

pass "triage returned a fully-typed TicketTriage"
