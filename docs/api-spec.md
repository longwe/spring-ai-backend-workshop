# API Specification

All endpoints are JSON over HTTPS. Authenticated endpoints require
`Authorization: Bearer <jwt>`. Errors use `{"error": "...", "message": "..."}`.
Rate limit: 60 req/min per user (429 on excess).

## Authentication

### POST /api/auth/register — public
Request: `{"username": "alice", "email": "alice@example.com", "password": "min 8 chars"}`
Response `201`: `{"token": "<jwt>", "username": "alice", "role": "USER"}`
Errors: `400 username_taken | email_taken | validation_failed`

### POST /api/auth/login — public
Request: `{"username": "alice", "password": "..."}`
Response `200`: same shape as register. Errors: `401 invalid_credentials`

## Chat

### POST /api/chat — authenticated
Request:
```json
{ "message": "Explain this document", "conversationId": "optional-uuid" }
```
Omit `conversationId` to start a new conversation (the id is returned).
Response `200`:
```json
{
  "conversationId": "b4f1...",
  "answer": "…",
  "sources": [ {"documentId": "…", "filename": "handbook.pdf", "snippet": "…"} ],
  "metadata": { "model": "claude-opus-4-8", "inputTokens": 1523, "outputTokens": 210, "latencyMs": 2450 }
}
```
Errors: `400 validation_failed | invalid_conversation_id`, `404 conversation_not_found`
(also returned when the conversation belongs to another user).

## Documents

### POST /api/documents/upload — authenticated (multipart)
Field `file` (max 20 MB; any Tika-parsable type: PDF, DOCX, TXT, HTML…).
Response `201`: `{"id": "…", "filename": "handbook.pdf", "contentType": "application/pdf", "sizeBytes": 123456, "chunkCount": 42, "createdAt": "…"}`
Errors: `400 empty_file | unreadable_document`, `413 file_too_large`

### GET /api/documents — authenticated
Response `200`: array of the object above (cached).

### DELETE /api/documents/{id} — authenticated
Response `204`. Removes vector chunks and metadata. Errors: `404 document_not_found`

## Admin (ROLE_ADMIN)

### GET /api/users
Response `200`: `[{"id": "…", "username": "…", "email": "…", "role": "USER", "createdAt": "…"}]`

### GET /api/system/status
Response `200`: `{"status": "UP", "model": "claude-opus-4-8", "users": 12, "documents": 4, "conversations": 30, "aiCalls": 120, "uptimeMs": 86400000}`

## Operational

- `GET /actuator/health` — public liveness/readiness
- `GET /actuator/metrics`, `GET /actuator/prometheus` — ADMIN. Custom metrics:
  `ai.tokens{direction=input|output}` (counters), `ai.chat.latency` (timer).
