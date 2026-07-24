package com.ezcloud.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Central AI wiring:
 *  - ChatMemory: sliding window backed by the JDBC chat-memory repository (Postgres)
 *  - ChatClient: Anthropic Claude with a default system prompt and advisors for
 *    conversation memory (MessageChatMemoryAdvisor) and RAG (QuestionAnswerAdvisor).
 *
 * Model/token settings live in application.yaml (spring.ai.anthropic.*).
 */
@Configuration
public class AiConfig {

    private static final int MEMORY_WINDOW_MESSAGES = 20;
    private static final int RAG_TOP_K = 5;
    private static final double RAG_SIMILARITY_THRESHOLD = 0.4;

    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(MEMORY_WINDOW_MESSAGES)
                .build();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 ChatMemory chatMemory,
                                 VectorStore vectorStore,
                                 @Value("classpath:prompts/system-rag.st") Resource systemPrompt) {
        return builder
                .defaultSystem(readResource(systemPrompt))
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder()
                                        .topK(RAG_TOP_K)
                                        .similarityThreshold(RAG_SIMILARITY_THRESHOLD)
                                        .build())
                                .build(),
                        new SimpleLoggerAdvisor())
                .build();
    }

    private static String readResource(Resource resource) {
        try {
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read system prompt", e);
        }
    }
}
