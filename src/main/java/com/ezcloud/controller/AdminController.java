package com.ezcloud.controller;

import com.ezcloud.repository.AiLogRepository;
import com.ezcloud.repository.ConversationRepository;
import com.ezcloud.repository.DocumentRepository;
import com.ezcloud.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Admin-only endpoints (RBAC enforced in SecurityConfig: ROLE_ADMIN). */
@RestController
@RequestMapping("/api")
public class AdminController {

    public record UserSummary(UUID id, String username, String email, String role, Instant createdAt) {
    }

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final ConversationRepository conversationRepository;
    private final AiLogRepository aiLogRepository;
    private final String model;

    public AdminController(UserRepository userRepository,
                           DocumentRepository documentRepository,
                           ConversationRepository conversationRepository,
                           AiLogRepository aiLogRepository,
                           @Value("${spring.ai.anthropic.chat.options.model}") String model) {
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.conversationRepository = conversationRepository;
        this.aiLogRepository = aiLogRepository;
        this.model = model;
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserSummary>> users() {
        return ResponseEntity.ok(userRepository.findAll().stream()
                .map(u -> new UserSummary(u.getId(), u.getUsername(), u.getEmail(),
                        u.getRole().name(), u.getCreatedAt()))
                .toList());
    }

    @GetMapping("/system/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "model", model,
                "users", userRepository.count(),
                "documents", documentRepository.count(),
                "conversations", conversationRepository.count(),
                "aiCalls", aiLogRepository.count(),
                "uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime()));
    }
}
