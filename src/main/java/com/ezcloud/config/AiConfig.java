package com.ezcloud.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatProperties;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
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

    /**
     * Claude Opus 4.8 rejects the temperature parameter, but Spring AI's
     * autoconfiguration hardcodes a default one into AnthropicChatProperties
     * (and yaml cannot bind a property to null). Clear it after binding so the
     * parameter is never sent — without this every model call fails with
     * HTTP 400 "temperature is deprecated for this model".
     */
    @Bean
    public static BeanPostProcessor clearAnthropicTemperature() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof AnthropicChatProperties properties) {
                    properties.getOptions().setTemperature(null);
                }
                return bean;
            }
        };
    }

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
        // TODO(1.1): give the client its persona via .defaultSystem(readResource(systemPrompt))   [Module 1]
        // TODO(3.2): add conversation memory — MessageChatMemoryAdvisor via .defaultAdvisors(...) [Module 3]
        // TODO(4.3): ground answers in uploaded docs — add a QuestionAnswerAdvisor
        //            (use the RAG_TOP_K / RAG_SIMILARITY_THRESHOLD constants above)               [Module 4]
        return builder.build();
    }

    private static String readResource(Resource resource) {
        try {
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read system prompt", e);
        }
    }
}
