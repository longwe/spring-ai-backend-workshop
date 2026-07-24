#!/usr/bin/env bash
# Shared helpers for checkpoint scripts. Source this; don't run it directly.

BASE_URL="${BASE_URL:-http://localhost:8080}"

pass() { printf '✅ PASS: %s\n' "$1"; }
fail() {
  printf '❌ FAIL: %s\n' "$1"
  [ -n "${2:-}" ] && printf '   hint: %s\n' "$2"
  exit 1
}

require_app() {
  curl -sf "$BASE_URL/actuator/health" >/dev/null \
    || fail "app is not running at $BASE_URL" \
            "start it with: ANTHROPIC_API_KEY=sk-ant-... ./mvnw spring-boot:run"
}

# Registers the workshop user (or logs in if it already exists) and sets $TOKEN.
get_token() {
  local user="workshop" pw="Workshop123!" resp
  resp=$(curl -s -X POST "$BASE_URL/api/auth/register" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$user\",\"email\":\"workshop@example.com\",\"password\":\"$pw\"}")
  case "$resp" in
    *'"token"'*) ;;
    *) resp=$(curl -s -X POST "$BASE_URL/api/auth/login" -H 'Content-Type: application/json' \
         -d "{\"username\":\"$user\",\"password\":\"$pw\"}") ;;
  esac
  TOKEN=$(printf '%s' "$resp" | sed -E 's/.*"token":"([^"]+)".*/\1/')
  if [ -z "$TOKEN" ] || [ "$TOKEN" = "$resp" ]; then
    fail "could not obtain a JWT" "is the app running and Postgres up? (docker compose up -d postgres redis)"
  fi
}

# json_field <json> <field> — naive extractor for flat string fields.
json_field() {
  printf '%s' "$1" | sed -E "s/.*\"$2\":\"([^\"]*)\".*/\1/"
}
