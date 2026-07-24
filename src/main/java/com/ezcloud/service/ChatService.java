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
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
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

    // workshop:start(5.1)
    @Retry(name = "ai")
    @CircuitBreaker(name = "ai", fallbackMethod = "chatFallback")
    // workshop:end
    public ChatResponse chat(String username, ChatRequest request) {
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("user_not_found"));
        var conversation = resolveConversation(user, request);

        var start = System.currentTimeMillis();
        var response = callModel(conversation, request);
        var latencyMs = System.currentTimeMillis() - start;

        var answer = response.chatResponse().getResult().getOutput().getText();
        var model = Objects.requireNonNullElse(response.chatResponse().getMetadata().getModel(), "unknown");
        var usage = response.chatResponse().getMetadata().getUsage();
        long inputTokens = Objects.requireNonNullElse(usage.getPromptTokens(), 0);
        long outputTokens = Objects.requireNonNullElse(usage.getCompletionTokens(), 0);

        meterRegistry.counter("ai.tokens", "direction", "input").increment(inputTokens);
        meterRegistry.counter("ai.tokens", "direction", "output").increment(outputTokens);
        meterRegistry.timer("ai.chat.latency").record(Duration.ofMillis(latencyMs));

        aiLogRepository.save(new AiLog(user.getId(), conversation.getId(), request.message(), answer,
                inputTokens, outputTokens, latencyMs, model));

        var metadata = Map.<String, Object>of(
                "model", model,
                "inputTokens", inputTokens,
                "outputTokens", outputTokens,
                "latencyMs", latencyMs);

        return new ChatResponse(conversation.getId().toString(), answer, extractSources(response), metadata);
    }

    /**
     * One blocking LLM round trip: user message in, full response out. The
     * advisors param routes conversation memory to this conversation's history;
     * tools registers the function-calling toolbox for this request.
     */
    private ChatClientResponse callModel(Conversation conversation, ChatRequest request) {
        // workshop:start(1.2)
        return chatClient.prompt()
                .user(request.message())
                .advisors(advisors -> advisors.param(ChatMemory.CONVERSATION_ID, conversation.getId().toString()))
                .tools(agentTools)
                .call()
                .chatClientResponse();
        // workshop:end
    }

    /**
     * Streams the answer token-by-token as it is generated. Memory and RAG
     * advisors run exactly as in {@link #chat}; the full answer is logged once
     * the stream completes. (Resilience4j annotations don't support reactive
     * return types without the reactor module, so this path relies on the
     * client timing out and re-requesting.)
     */
    public Flux<String> chatStream(String username, ChatRequest request) {
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("user_not_found"));
        var conversation = resolveConversation(user, request);

        // workshop:start(3.1)
        var start = System.currentTimeMillis();
        var answer = new StringBuilder();
        return chatClient.prompt()
                .user(request.message())
                .advisors(advisors -> advisors.param(ChatMemory.CONVERSATION_ID, conversation.getId().toString()))
                .tools(agentTools)
                .stream()
                .content()
                .doOnNext(answer::append)
                .doOnComplete(() -> {
                    var latencyMs = System.currentTimeMillis() - start;
                    meterRegistry.timer("ai.chat.latency").record(Duration.ofMillis(latencyMs));
                    meterRegistry.counter("ai.chat.stream").increment();
                    aiLogRepository.save(new AiLog(user.getId(), conversation.getId(), request.message(),
                            answer.toString(), null, null, latencyMs, "streamed"));
                });
        // workshop:end
    }

    /**
     * Invoked by the circuit breaker when {@link #chat} fails or the circuit is
     * open. Business exceptions keep their normal HTTP mapping; infrastructure
     * failures degrade gracefully instead of surfacing a 500.
     */
    private ChatResponse chatFallback(String username, ChatRequest request, Throwable failure) {
        // workshop:start(5.2)
        if (failure instanceof NotFoundException || failure instanceof IllegalArgumentException) {
            throw (RuntimeException) failure;
        }
        meterRegistry.counter("ai.chat.fallback").increment();
        return new ChatResponse(request.conversationId(),
                "The AI service is temporarily unavailable. Please try again in a moment.",
                List.of(), Map.of("degraded", true));
        // workshop:end
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
