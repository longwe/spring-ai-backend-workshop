package com.ezcloud.dto;

import java.time.Instant;
import java.util.UUID;

public final class DocumentDtos {

    private DocumentDtos() {
    }

    public record DocumentResponse(
            UUID id,
            String filename,
            String contentType,
            long sizeBytes,
            int chunkCount,
            Instant createdAt) {
    }
}
