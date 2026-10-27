package com.example.docassistant.service.rag;

import com.example.docassistant.service.chunking.TokenDocumentChunker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DocumentRetrievalServiceImplTest {

    @Mock
    private VectorStore vectorStore;

    private DocumentRetrievalServiceImpl retrievalService;

    @BeforeEach
    void setUp() {
        retrievalService = new DocumentRetrievalServiceImpl(vectorStore, 4, 0.5);
    }

    @Test
    @DisplayName("retrieveRelevantChunks restricts search strictly to specified documentId filter")
    void retrieveRelevantChunks_appliesDocumentFilter() {
        UUID docId = UUID.randomUUID();
        String query = "What is the policy?";

        Document matchingDoc = new Document("c1", "Policy details", Map.of(
                TokenDocumentChunker.METADATA_DOCUMENT_ID, docId.toString(),
                TokenDocumentChunker.METADATA_PAGE_NUMBER, 1
        ));

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        given(vectorStore.similaritySearch(captor.capture())).willReturn(List.of(matchingDoc));

        List<Document> results = retrievalService.retrieveRelevantChunks(docId, query);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getText()).isEqualTo("Policy details");

        SearchRequest captured = captor.getValue();
        assertThat(captured.getQuery()).isEqualTo(query);
        assertThat(captured.getTopK()).isEqualTo(4);
        assertThat(captured.getSimilarityThreshold()).isEqualTo(0.5);
        assertThat(captured.getFilterExpression()).isNotNull();
    }

    @Test
    @DisplayName("retrieveRelevantChunks drops chunks belonging to different document (defense in depth)")
    void retrieveRelevantChunks_dropsChunksFromOtherDocuments() {
        UUID targetDocId = UUID.randomUUID();
        UUID otherDocId = UUID.randomUUID();

        Document docForTarget = new Document("c1", "Target document chunk", Map.of(
                TokenDocumentChunker.METADATA_DOCUMENT_ID, targetDocId.toString()
        ));
        Document docForOther = new Document("c2", "Leak from another doc", Map.of(
                TokenDocumentChunker.METADATA_DOCUMENT_ID, otherDocId.toString()
        ));

        given(vectorStore.similaritySearch(org.mockito.ArgumentMatchers.any(SearchRequest.class)))
                .willReturn(List.of(docForTarget, docForOther));

        List<Document> results = retrievalService.retrieveRelevantChunks(targetDocId, "query");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getText()).isEqualTo("Target document chunk");
    }

    @Test
    @DisplayName("retrieveRelevantChunks returns empty list when no matches meet threshold")
    void retrieveRelevantChunks_noMatches_returnsEmptyList() {
        UUID docId = UUID.randomUUID();
        given(vectorStore.similaritySearch(org.mockito.ArgumentMatchers.any(SearchRequest.class)))
                .willReturn(List.of());

        List<Document> results = retrievalService.retrieveRelevantChunks(docId, "unknown topic");

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("retrieveRelevantChunks propagates vector store exceptions")
    void retrieveRelevantChunks_vectorStoreError_propagates() {
        UUID docId = UUID.randomUUID();
        given(vectorStore.similaritySearch(org.mockito.ArgumentMatchers.any(SearchRequest.class)))
                .willThrow(new RuntimeException("Vector store connection failure"));

        assertThatThrownBy(() -> retrievalService.retrieveRelevantChunks(docId, "query"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Vector store connection failure");
    }
}
