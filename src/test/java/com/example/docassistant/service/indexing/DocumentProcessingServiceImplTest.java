package com.example.docassistant.service.indexing;

import com.example.docassistant.domain.DocumentRecord;
import com.example.docassistant.domain.DocumentStatus;
import com.example.docassistant.exception.PdfExtractionException;
import com.example.docassistant.repository.DocumentRepository;
import com.example.docassistant.service.chunking.DocumentChunker;
import com.example.docassistant.service.extraction.DocumentContentExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DocumentProcessingServiceImplTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentContentExtractor contentExtractor;

    @Mock
    private DocumentChunker documentChunker;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private com.example.docassistant.repository.VisualAssetRepository visualAssetRepository;

    @Mock
    private com.example.docassistant.storage.VisualAssetStorage visualAssetStorage;

    private DocumentProcessingServiceImpl processingService;

    @BeforeEach
    void setUp() {
        processingService = new DocumentProcessingServiceImpl(
                documentRepository,
                contentExtractor,
                documentChunker,
                vectorStore,
                visualAssetRepository,
                visualAssetStorage
        );
    }

    @Test
    @DisplayName("processDocument successfully transitions through PROCESSING to READY and indexes chunks")
    void processDocument_success_transitionsToReady() {
        UUID docId = UUID.randomUUID();
        DocumentRecord record = createRecord(docId, "report.pdf", "storage-1.pdf");

        Document page = new Document("p1", "Sample text", Map.of("page_number", 1));
        Document chunk = new Document("c1", "Sample text", Map.of("documentId", docId.toString()));

        given(documentRepository.save(any(DocumentRecord.class))).willAnswer(i -> i.getArgument(0));
        given(contentExtractor.extract("storage-1.pdf")).willReturn(List.of(page));
        given(documentChunker.chunk(List.of(page), docId, "report.pdf")).willReturn(List.of(chunk));

        DocumentRecord result = processingService.processDocument(record);

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.READY);
        assertThat(result.getErrorMessage()).isNull();

        verify(vectorStore).add(List.of(chunk));
    }

    @Test
    @DisplayName("processDocument transitions to FAILED and cleans up when text extraction fails")
    void processDocument_extractionFailure_marksFailed() {
        UUID docId = UUID.randomUUID();
        DocumentRecord record = createRecord(docId, "empty.pdf", "storage-2.pdf");

        given(documentRepository.save(any(DocumentRecord.class))).willAnswer(i -> i.getArgument(0));
        given(contentExtractor.extract("storage-2.pdf"))
                .willThrow(new PdfExtractionException("no usable extracted text"));

        DocumentRecord result = processingService.processDocument(record);

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(result.getErrorMessage()).contains("TEXT_EXTRACTION_FAILED");

        verify(vectorStore, never()).add(anyList());
        verify(vectorStore).delete(anyString());
    }

    @Test
    @DisplayName("processDocument transitions to FAILED and cleans up vectors when vectorStore throws exception")
    void processDocument_vectorStoreFailure_marksFailedAndCleansUp() {
        UUID docId = UUID.randomUUID();
        DocumentRecord record = createRecord(docId, "doc.pdf", "storage-3.pdf");

        Document page = new Document("p1", "Text", Map.of("page_number", 1));
        Document chunk = new Document("c1", "Text", Map.of("documentId", docId.toString()));

        given(documentRepository.save(any(DocumentRecord.class))).willAnswer(i -> i.getArgument(0));
        given(contentExtractor.extract("storage-3.pdf")).willReturn(List.of(page));
        given(documentChunker.chunk(List.of(page), docId, "doc.pdf")).willReturn(List.of(chunk));

        doThrow(new RuntimeException("Connection refused to embedding service"))
                .when(vectorStore).add(anyList());

        DocumentRecord result = processingService.processDocument(record);

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(result.getErrorMessage()).contains("EMBEDDING_SERVICE_UNAVAILABLE");

        // Verify cleanup was invoked
        verify(vectorStore).delete("documentId == '" + docId + "'");
    }

    private DocumentRecord createRecord(UUID id, String filename, String storageKey) {
        OffsetDateTime now = OffsetDateTime.now();
        return new DocumentRecord(
                id,
                filename,
                storageKey,
                "application/pdf",
                1024L,
                DocumentStatus.UPLOADED,
                now,
                now
        );
    }
}
