package com.example.docassistant.controller;

import com.example.docassistant.dto.ChatRequest;
import com.example.docassistant.dto.ChatResponse;
import com.example.docassistant.service.AiChatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev")
public class DevChatController {

    private final AiChatService aiChatService;

    public DevChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        String responseText = aiChatService.chat(request.message());
        return ResponseEntity.ok(new ChatResponse(responseText));
    }
}
