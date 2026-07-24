package com.ezcloud;

import com.ezcloud.controller.TriageController;
import com.ezcloud.dto.TriageDtos.Category;
import com.ezcloud.dto.TriageDtos.Priority;
import com.ezcloud.dto.TriageDtos.TicketTriage;
import com.ezcloud.exception.GlobalExceptionHandler;
import com.ezcloud.service.TriageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone MockMvc test — no Spring context, DB, or LLM required. */
class TriageControllerTest {

    private TriageService triageService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        triageService = Mockito.mock(TriageService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new TriageController(triageService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsTypedTriage() throws Exception {
        Mockito.when(triageService.triage(any())).thenReturn(
                new TicketTriage(Category.BILLING, Priority.HIGH,
                        "Customer was double-charged for the annual plan.",
                        "We're sorry about the duplicate charge — we're looking into it now."));

        mockMvc.perform(post("/api/triage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"I was charged twice for my annual subscription!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("BILLING"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.suggestedReply").isNotEmpty());
    }

    @Test
    void rejectsBlankMessage() throws Exception {
        mockMvc.perform(post("/api/triage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
