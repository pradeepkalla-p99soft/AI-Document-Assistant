package com.example.docassistant.service;

/**
 * Service abstraction for interacting with an AI chat model.
 */
public interface AiChatService {

    /**
     * Generates a chat response for the given user message.
     *
     * @param message the user input prompt
     * @return the generated response text from the AI model
     */
    String chat(String message);
}
