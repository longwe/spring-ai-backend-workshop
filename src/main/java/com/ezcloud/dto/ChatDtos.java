package com.ezcloud.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public final class ChatDtos {

    private ChatDtos() {
    }

    public record ChatRequest(
            @NotBlank @Size(max = 20000) String message,
            String conversationId) {
    }

    public record SourceRef(String documentId, String filename, String snippet) {
    }

    public record ChatResponse(
            String conversationId,
            String answer,
            List<SourceRef> sources,
            Map<String, Object> metadata) {
    }
}
