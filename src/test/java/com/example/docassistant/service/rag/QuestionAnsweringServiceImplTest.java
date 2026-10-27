package com.example.docassistant.service.rag;

import com.example.docassistant.domain.DocumentRecord;
import com.example.docassistant.domain.DocumentStatus;
import com.example.docassistant.dto.AskQuestionRequest;
import com.example.docassistant.dto.AskQuestionResponse;
import com.example.docassistant.exception.AiProviderException;
import com.example.docassistant.exception.DocumentNotFoundException;
import com.example.docassistant.exception.DocumentNotReadyException;
import com.example.docassistant.repository.DocumentRepository;
import com.example.docassistant.service.chunking.TokenDocumentChunker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QuestionAnsweringServiceImplTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentRetrievalService retrievalService;

    @Mock
    private AnswerGenerationService answerGenerationService;

    private QuestionAnsweringServiceImpl questionAnsweringService;

    @BeforeEach
    void setUp() {
        questionAnsweringService = new QuestionAnsweringServiceImpl(
                documentRepository,
                retrievalService,
                answerGenerationService
        );
    }

    @Test
    @DisplayName("askQuestion throws DocumentNotFoundException when document does not exist")
    void askQuestion_documentNotFound_throwsException() {
        UUID docId = UUID.randomUUID();
        given(documentRepository.findById(docId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> questionAnsweringService.askQuestion(docId, new AskQuestionRequest("question")))
                .isInstanceOf(DocumentNotFoundException.class)
                .hasMessageContaining(docId.toString());

        verify(retrievalService, never()).retrieveRelevantChunks(any(), any());
    }

    @Test
    @DisplayName("askQuestion throws DocumentNotReadyException when document is not in READY status")
    void askQuestion_documentNotReady_throwsException() {
        UUID docId = UUID.randomUUID();
        DocumentRecord record = createRecord(docId, DocumentStatus.PROCESSING);
        given(documentRepository.findById(docId)).willReturn(Optional.of(record));

        assertThatThrownBy(() -> questionAnsweringService.askQuestion(docId, new AskQuestionRequest("question")))
                .isInstanceOf(DocumentNotReadyException.class)
                .hasMessageContaining("PROCESSING");

        verify(retrievalService, never()).retrieveRelevantChunks(any(), any());
    }

    @Test
    @DisplayName("askQuestion handles insufficient evidence without invoking model when no chunks retrieved")
    void askQuestion_noChunksRetrieved_returnsInsufficientEvidenceDirectly() {
        UUID docId = UUID.randomUUID();
        DocumentRecord record = createRecord(docId, DocumentStatus.READY);
        given(documentRepository.findById(docId)).willReturn(Optional.of(record));
        given(retrievalService.retrieveRelevantChunks(docId, "unknown topic")).willReturn(List.of());

        AskQuestionResponse response = questionAnsweringService.askQuestion(
                docId, new AskQuestionRequest("unknown topic"));

        assertThat(response.documentId()).isEqualTo(docId);
        assertThat(response.answer()).isEqualTo(AnswerGenerationServiceImpl.INSUFFICIENT_EVIDENCE_ANSWER);
        assertThat(response.contextSupplied()).isFalse();
        assertThat(response.sources()).isEmpty();

        // Model should NOT be invoked unnecessarily
        verify(answerGenerationService, never()).generateAnswer(any(), anyList());
    }

    @Test
    @DisplayName("askQuestion successfully builds source references and returns answer on relevant context")
    void askQuestion_successfulAnswer_withSources() {
        UUID docId = UUID.randomUUID();
        DocumentRecord record = createRecord(docId, DocumentStatus.READY);
        given(documentRepository.findById(docId)).willReturn(Optional.of(record));

        Document chunk1 = new Document("c1", "Spring Boot provides autoconfiguration for web apps.", Map.of(
                TokenDocumentChunker.METADATA_DOCUMENT_ID, docId.toString(),
                TokenDocumentChunker.METADATA_FILENAME, "spring.pdf",
                TokenDocumentChunker.METADATA_PAGE_NUMBER, 3
        ));
        Document chunk2 = new Document("c2", "Dependency injection is handled by the application context.", Map.of(
                TokenDocumentChunker.METADATA_DOCUMENT_ID, docId.toString(),
                TokenDocumentChunker.METADATA_FILENAME, "spring.pdf",
                TokenDocumentChunker.METADATA_PAGE_NUMBER, 4
        ));

        given(retrievalService.retrieveRelevantChunks(docId, "What does Spring do?"))
                .willReturn(List.of(chunk1, chunk2));
        given(answerGenerationService.generateAnswer(eq("What does Spring do?"), anyList()))
                .willReturn("Spring Boot provides autoconfiguration [S1] and dependency injection [S2].");

        AskQuestionResponse response = questionAnsweringService.askQuestion(
                docId, new AskQuestionRequest("What does Spring do?"));

        assertThat(response.documentId()).isEqualTo(docId);
        assertThat(response.contextSupplied()).isTrue();
        assertThat(response.answer()).contains("[S1]").contains("[S2]");
        assertThat(response.sources()).hasSize(2);
        assertThat(response.sources().get(0).pageNumber()).isEqualTo(3);
        assertThat(response.sources().get(0).filename()).isEqualTo("spring.pdf");
        assertThat(response.sources().get(1).pageNumber()).isEqualTo(4);
    }

    @Test
    @DisplayName("askQuestion strips out fabricated citation markers exceeding actual source count")
    void askQuestion_stripsFabricatedCitations() {
        UUID docId = UUID.randomUUID();
        DocumentRecord record = createRecord(docId, DocumentStatus.READY);
        given(documentRepository.findById(docId)).willReturn(Optional.of(record));

        Document chunk1 = new Document("c1", "Single source excerpt", Map.of(
                TokenDocumentChunker.METADATA_DOCUMENT_ID, docId.toString(),
                TokenDocumentChunker.METADATA_PAGE_NUMBER, 1
        ));

        given(retrievalService.retrieveRelevantChunks(docId, "question")).willReturn(List.of(chunk1));
        // Model returned valid [S1] and fabricated [S9]
        given(answerGenerationService.generateAnswer(eq("question"), anyList()))
                .willReturn("Claim supported by [S1], and unsupported claim [S9].");

        AskQuestionResponse response = questionAnsweringService.askQuestion(
                docId, new AskQuestionRequest("question"));

        assertThat(response.answer()).contains("[S1]");
        assertThat(response.answer()).doesNotContain("[S9]");
    }

    @Test
    @DisplayName("askQuestion propagates AiProviderException when model generation fails")
    void askQuestion_modelFailure_propagatesException() {
        UUID docId = UUID.randomUUID();
        DocumentRecord record = createRecord(docId, DocumentStatus.READY);
        given(documentRepository.findById(docId)).willReturn(Optional.of(record));

        Document chunk = new Document("c1", "Excerpt", Map.of());
        given(retrievalService.retrieveRelevantChunks(docId, "question")).willReturn(List.of(chunk));
        given(answerGenerationService.generateAnswer(eq("question"), anyList()))
                .willThrow(new AiProviderException("AI service unavailable"));

        assertThatThrownBy(() -> questionAnsweringService.askQuestion(docId, new AskQuestionRequest("question")))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("AI service unavailable");
    }

    private DocumentRecord createRecord(UUID id, DocumentStatus status) {
        OffsetDateTime now = OffsetDateTime.now();
        return new DocumentRecord(
                id,
                "manual.pdf",
                "key-1.pdf",
                "application/pdf",
                2048L,
                status,
                now,
                now
        );
    }
}
