package com.ezcloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * AI Platform — Spring AI backend entry point.
 * Chat (Claude via Anthropic), RAG over PGVector, agent tools, JWT security.
 */
@SpringBootApplication
@EnableCaching
public class AiPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiPlatformApplication.class, args);
    }
}
