package com.example.docassistant.controller;

import com.example.docassistant.dto.ChatRequest;
import com.example.docassistant.dto.ChatResponse;
import com.example.docassistant.exception.AiProviderException;
import com.example.docassistant.service.AiChatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DevChatController.class)
class DevChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiChatService aiChatService;

    @Test
    @DisplayName("POST /api/dev/chat returns 200 with chat response for valid message")
    void chat_withValidMessage_returnsOk() throws Exception {
        given(aiChatService.chat("Explain dependency injection in Spring"))
                .willReturn("Dependency injection is a design pattern...");

        mockMvc.perform(post("/api/dev/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "message": "Explain dependency injection in Spring"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("Dependency injection is a design pattern..."));
    }

    @Test
    @DisplayName("POST /api/dev/chat returns 400 when message is blank")
    void chat_withBlankMessage_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/dev/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "message": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Request Content"))
                .andExpect(jsonPath("$.errors.message").value("Message must not be blank"));
    }

    @Test
    @DisplayName("POST /api/dev/chat returns 400 when message exceeds 2000 characters")
    void chat_withExceedingLengthMessage_returnsBadRequest() throws Exception {
        String longMessage = "a".repeat(2001);

        mockMvc.perform(post("/api/dev/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"message\": \"%s\"}", longMessage)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Request Content"))
                .andExpect(jsonPath("$.errors.message").value("Message must not exceed 2000 characters"));
    }

    @Test
    @DisplayName("POST /api/dev/chat returns 400 when request body is malformed JSON")
    void chat_withMalformedJson_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/dev/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ invalid json }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed Request Body"));
    }

    @Test
    @DisplayName("POST /api/dev/chat returns 502 when AI provider fails")
    void chat_whenAiProviderFails_returnsBadGateway() throws Exception {
        given(aiChatService.chat(anyString()))
                .willThrow(new AiProviderException("Ollama connection refused"));

        mockMvc.perform(post("/api/dev/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "message": "Hello AI"
                                }
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("AI Provider Error"))
                .andExpect(jsonPath("$.detail").value("AI service is currently unavailable or encountered an error"));
    }
}
