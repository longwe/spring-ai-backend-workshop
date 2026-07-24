package com.ezcloud.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Audit record of one LLM round trip: prompt/response snippets, tokens, latency. */
@Entity
@Table(name = "ai_logs")
public class AiLog {

    private static final int SNIPPET_MAX = 4000;

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(columnDefinition = "text")
    private String prompt;

    @Column(columnDefinition = "text")
    private String response;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "latency_ms")
    private Long latencyMs;

    private String model;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiLog() {
    }

    public AiLog(UUID userId, UUID conversationId, String prompt, String response,
                 Long inputTokens, Long outputTokens, Long latencyMs, String model) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.conversationId = conversationId;
        this.prompt = truncate(prompt);
        this.response = truncate(response);
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.latencyMs = latencyMs;
        this.model = model;
        this.createdAt = Instant.now();
    }

    private static String truncate(String value) {
        return Optional.ofNullable(value)
                .map(v -> v.length() <= SNIPPET_MAX ? v : v.substring(0, SNIPPET_MAX))
                .orElse(null);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getResponse() {
        return response;
    }

    public Long getInputTokens() {
        return inputTokens;
    }

    public Long getOutputTokens() {
        return outputTokens;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public String getModel() {
        return model;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
