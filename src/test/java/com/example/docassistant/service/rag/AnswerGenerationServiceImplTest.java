package com.example.docassistant.service.rag;

import com.example.docassistant.exception.AiProviderException;
import com.example.docassistant.service.chunking.TokenDocumentChunker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AnswerGenerationServiceImplTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient.Builder chatClientBuilder;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    private AnswerGenerationServiceImpl answerGenerationService;

    @BeforeEach
    void setUp() {
        given(chatClientBuilder.defaultSystem(anyString())).willReturn(chatClientBuilder);
        given(chatClientBuilder.build()).willReturn(chatClient);
        answerGenerationService = new AnswerGenerationServiceImpl(chatClientBuilder);
    }

    @Test
    @DisplayName("generateAnswer returns insufficient evidence when context chunks list is empty")
    void generateAnswer_emptyChunks_returnsInsufficientEvidence() {
        String answer = answerGenerationService.generateAnswer("What is RAG?", List.of());
        assertThat(answer).isEqualTo(AnswerGenerationServiceImpl.INSUFFICIENT_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("generateAnswer calls ChatClient and returns trimmed model answer")
    void generateAnswer_withChunks_callsChatClient() {
        Document chunk = new Document("c1", "Retrieval-Augmented Generation combines retrieval with generative models.", Map.of(
                TokenDocumentChunker.METADATA_PAGE_NUMBER, 2
        ));

        given(chatClient.prompt().user(anyString()).call().content())
                .willReturn(" RAG combines retrieval and generative models. ");

        String answer = answerGenerationService.generateAnswer("What is RAG?", List.of(chunk));

        assertThat(answer).isEqualTo("RAG combines retrieval and generative models.");
    }

    @Test
    @DisplayName("generateAnswer wraps AI provider exception into AiProviderException")
    void generateAnswer_chatClientThrowsException_wrapsInAiProviderException() {
        Document chunk = new Document("c1", "Sample text", Map.of());

        given(chatClient.prompt().user(anyString()).call().content())
                .willThrow(new RuntimeException("Ollama connection timed out"));

        assertThatThrownBy(() -> answerGenerationService.generateAnswer("question", List.of(chunk)))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("Failed to generate answer from AI provider");
    }
}
