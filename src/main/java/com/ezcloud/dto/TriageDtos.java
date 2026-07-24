package com.ezcloud.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Support-ticket triage: free-text in, typed classification out. */
public final class TriageDtos {

    private TriageDtos() {
    }

    public record TriageRequest(
            @NotBlank @Size(max = 4000) String message) {
    }

    public enum Category { BILLING, TECHNICAL, ACCOUNT, GENERAL }

    public enum Priority { LOW, MEDIUM, HIGH, URGENT }

    /** Structured output produced directly by the LLM via ChatClient.entity(). */
    public record TicketTriage(
            Category category,
            Priority priority,
            String summary,
            String suggestedReply) {
    }
}
