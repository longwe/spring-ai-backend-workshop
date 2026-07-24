package com.ezcloud.service;

import com.ezcloud.domain.AiLog;
import com.ezcloud.domain.Conversation;
import com.ezcloud.domain.User;
import com.ezcloud.dto.ChatDtos.ChatRequest;
import com.ezcloud.dto.ChatDtos.ChatResponse;
import com.ezcloud.dto.ChatDtos.SourceRef;
import com.ezcloud.exception.NotFoundException;
import com.ezcloud.repository.AiLogRepository;
import com.ezcloud.repository.ConversationRepository;
import com.ezcloud.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static java.util.function.Predicate.not;

/**
 * Conversation orchestration: resolves the conversation, calls the LLM through
 * the ChatClient (memory + RAG advisors + agent tools), extracts RAG sources,
 * and records an AiLog row plus Micrometer token counters per round trip.
 */
@Service
public class ChatService {

    private final ChatClient chatClient;
    private final AgentTools agentTools;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final AiLogRepository aiLogRepository;
    private final MeterRegistry meterRegistry;

    public ChatService(ChatClient chatClient,
                       AgentTools agentTools,
                       ConversationRepository conversationRepository,
                       UserRepository userRepository,
                       AiLogRepository aiLogRepository,
                       MeterRegistry meterRegistry) {
        this.chatClient = chatClient;
        this.agentTools = agentTools;
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
        this.aiLogRepository = aiLogRepository;
        this.meterRegistry = meterRegistry;
    }

    public ChatResponse chat(String username, ChatRequest request) {
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("user_not_found"));
        var conversation = resolveConversation(user, request);

        var start = System.currentTimeMillis();
        var response = chatClient.prompt()
                .user(request.message())
                .advisors(advisors -> advisors.param(ChatMemory.CONVERSATION_ID, conversation.getId().toString()))
                .tools(agentTools)
                .call()
                .chatClientResponse();
        var latencyMs = System.currentTimeMillis() - start;

        var answer = response.chatResponse().getResult().getOutput().getText();
        var model = Objects.requireNonNullElse(response.chatResponse().getMetadata().getModel(), "unknown");
        var usage = response.chatResponse().getMetadata().getUsage();
        long inputTokens = Objects.requireNonNullElse(usage.getPromptTokens(), 0);
        long outputTokens = Objects.requireNonNullElse(usage.getCompletionTokens(), 0);

        meterRegistry.counter("ai.tokens", "direction", "input").increment(inputTokens);
        meterRegistry.counter("ai.tokens", "direction", "output").increment(outputTokens);
        meterRegistry.timer("ai.chat.latency").record(java.time.Duration.ofMillis(latencyMs));

        aiLogRepository.save(new AiLog(user.getId(), conversation.getId(), request.message(), answer,
                inputTokens, outputTokens, latencyMs, model));

        var metadata = Map.<String, Object>of(
                "model", model,
                "inputTokens", inputTokens,
                "outputTokens", outputTokens,
                "latencyMs", latencyMs);

        return new ChatResponse(conversation.getId().toString(), answer, extractSources(response), metadata);
    }

    private Conversation resolveConversation(User user, ChatRequest request) {
        return Optional.ofNullable(request.conversationId())
                .filter(not(String::isBlank))
                .map(id -> findOwnedConversation(user, id))
                .orElseGet(() -> newConversation(user, request));
    }

    private Conversation newConversation(User user, ChatRequest request) {
        var title = request.message().length() > 60
                ? request.message().substring(0, 60)
                : request.message();
        return conversationRepository.save(new Conversation(user.getId(), title));
    }

    private Conversation findOwnedConversation(User user, String id) {
        final UUID conversationId;
        try {
            conversationId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid_conversation_id");
        }
        return conversationRepository.findById(conversationId)
                .filter(conversation -> conversation.getUserId().equals(user.getId()))
                .orElseThrow(() -> new NotFoundException("conversation_not_found"));
    }

    @SuppressWarnings("unchecked")
    private List<SourceRef> extractSources(ChatClientResponse response) {
        var retrieved = response.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        if (!(retrieved instanceof List<?> documents)) {
            return List.of();
        }
        return ((List<Document>) documents).stream()
                .map(doc -> new SourceRef(
                        String.valueOf(doc.getMetadata().getOrDefault("documentId", "")),
                        String.valueOf(doc.getMetadata().getOrDefault("filename", "unknown")),
                        snippet(doc.getText())))
                .toList();
    }

    private static String snippet(String text) {
        var value = Objects.requireNonNullElse(text, "");
        return value.length() <= 240 ? value : value.substring(0, 240) + "…";
    }
}
