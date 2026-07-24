package com.ezcloud.service;

import com.ezcloud.dto.TriageDtos.TicketTriage;
import com.ezcloud.dto.TriageDtos.TriageRequest;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Structured output: renders a PromptTemplate and asks the model for a typed
 * {@link TicketTriage} via ChatClient.entity() — Spring AI appends JSON-schema
 * format instructions and converts the response, no string-wrangling needed.
 *
 * Uses its own bare ChatClient (the injected builder is prototype-scoped):
 * triage is a one-shot classification, so the conversation-memory and RAG
 * advisors configured on the main chat client must not apply here.
 */
@Service
public class TriageService {

    private final ChatClient chatClient;
    private final Resource triagePrompt;

    public TriageService(ChatClient.Builder builder,
                         @Value("classpath:prompts/triage.st") Resource triagePrompt) {
        this.chatClient = builder.build();
        this.triagePrompt = triagePrompt;
    }

    @Retry(name = "ai")
    public TicketTriage triage(TriageRequest request) {
        // TODO(2.1): render prompts/triage.st with a PromptTemplate (variable: "message"),
        //            then ask for a typed result: .call().entity(TicketTriage.class)              [Module 2]
        throw new UnsupportedOperationException("TODO 2.1 — see WORKSHOP.md Module 2");
    }
}
