-- Business schema. The Spring AI vector_store table and chat-memory table are
-- created by Spring AI's own initializers (initialize-schema: true / always).

CREATE TABLE users (
    id          UUID PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL UNIQUE,
    email       VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    role        VARCHAR(16)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE documents (
    id           UUID PRIMARY KEY,
    filename     VARCHAR(255) NOT NULL,
    content_type VARCHAR(128),
    size_bytes   BIGINT       NOT NULL,
    chunk_count  INT          NOT NULL,
    uploaded_by  UUID         NOT NULL REFERENCES users (id),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE conversations (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES users (id),
    title      VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_conversations_user ON conversations (user_id);

CREATE TABLE ai_logs (
    id              UUID PRIMARY KEY,
    user_id         UUID,
    conversation_id UUID,
    prompt          TEXT,
    response        TEXT,
    input_tokens    BIGINT,
    output_tokens   BIGINT,
    latency_ms      BIGINT,
    model           VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_logs_created ON ai_logs (created_at);
