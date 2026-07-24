#!/usr/bin/env bash
# Prints a JWT for the workshop user (registers it on first use).
#
# Usage:
#   TOKEN=$(scripts/token.sh)
#   curl -H "Authorization: Bearer $TOKEN" localhost:8080/api/chat ...
set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
USER_NAME="${WORKSHOP_USER:-workshop}"
PASSWORD="${WORKSHOP_PASSWORD:-Workshop123!}"

resp=$(curl -s -X POST "$BASE_URL/api/auth/register" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER_NAME\",\"email\":\"$USER_NAME@example.com\",\"password\":\"$PASSWORD\"}")
case "$resp" in
  *'"token"'*) ;;
  *) resp=$(curl -s -X POST "$BASE_URL/api/auth/login" -H 'Content-Type: application/json' \
       -d "{\"username\":\"$USER_NAME\",\"password\":\"$PASSWORD\"}") ;;
esac

token=$(printf '%s' "$resp" | sed -E 's/.*"token":"([^"]+)".*/\1/')
if [ -z "$token" ] || [ "$token" = "$resp" ]; then
  echo "error: could not obtain a token — is the app running at $BASE_URL?" >&2
  echo "  server said: $resp" >&2
  exit 1
fi
printf '%s\n' "$token"
