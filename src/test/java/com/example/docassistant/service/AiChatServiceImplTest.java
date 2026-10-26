package com.example.docassistant.service;

import com.example.docassistant.exception.AiProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AiChatServiceImplTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    private AiChatServiceImpl aiChatService;

    @BeforeEach
    void setUp() {
        given(chatClientBuilder.build()).willReturn(chatClient);
        aiChatService = new AiChatServiceImpl(chatClientBuilder);
    }

    @Test
    @DisplayName("chat returns response content on successful invocation")
    void chat_success() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);

        given(chatClient.prompt()).willReturn(requestSpec);
        given(requestSpec.user("Hello")).willReturn(requestSpec);
        given(requestSpec.call()).willReturn(responseSpec);
        given(responseSpec.content()).willReturn("Hi there!");

        String result = aiChatService.chat("Hello");

        assertThat(result).isEqualTo("Hi there!");
    }

    @Test
    @DisplayName("chat wraps any ChatClient exception in AiProviderException")
    void chat_failure_throwsAiProviderException() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);

        given(chatClient.prompt()).willReturn(requestSpec);
        given(requestSpec.user("Hello")).willReturn(requestSpec);
        given(requestSpec.call()).willThrow(new RuntimeException("Connection timeout"));

        assertThatThrownBy(() -> aiChatService.chat("Hello"))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("Failed to generate response from AI provider");
    }
}
