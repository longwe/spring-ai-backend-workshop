package com.ezcloud.controller;

import com.ezcloud.dto.ChatDtos.ChatRequest;
import com.ezcloud.dto.ChatDtos.ChatResponse;
import com.ezcloud.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(Authentication authentication,
                                             @Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(chatService.chat(authentication.getName(), request));
    }

    /** Server-sent events: tokens arrive as they are generated. */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(Authentication authentication,
                                   @Valid @RequestBody ChatRequest request) {
        return chatService.chatStream(authentication.getName(), request);
    }
}
