package com.example.docassistant.service;

import com.example.docassistant.exception.AiProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AiChatServiceImpl implements AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatServiceImpl.class);

    private final ChatClient chatClient;

    public AiChatServiceImpl(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public String chat(String message) {
        try {
            return this.chatClient.prompt()
                    .user(message)
                    .call()
                    .content();
        } catch (Exception ex) {
            log.error("AI provider error during chat generation: {}", ex.getMessage());
            throw new AiProviderException("Failed to generate response from AI provider", ex);
        }
    }
}
