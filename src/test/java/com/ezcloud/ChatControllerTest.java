package com.ezcloud;

import com.ezcloud.controller.ChatController;
import com.ezcloud.dto.ChatDtos.ChatResponse;
import com.ezcloud.exception.GlobalExceptionHandler;
import com.ezcloud.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone MockMvc test — no Spring context, DB, or LLM required. */
class ChatControllerTest {

    private ChatService chatService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        chatService = Mockito.mock(ChatService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(chatService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsAnswerFromService() throws Exception {
        Mockito.when(chatService.chat(eq("alice"), any())).thenReturn(
                new ChatResponse("conv-1", "Paris is the capital of France.", List.of(),
                        Map.of("model", "claude-opus-4-8")));

        mockMvc.perform(post("/api/chat")
                        .principal(new UsernamePasswordAuthenticationToken("alice", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"What is the capital of France?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Paris is the capital of France."))
                .andExpect(jsonPath("$.conversationId").value("conv-1"));
    }

    @Test
    void rejectsBlankMessage() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .principal(new UsernamePasswordAuthenticationToken("alice", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
