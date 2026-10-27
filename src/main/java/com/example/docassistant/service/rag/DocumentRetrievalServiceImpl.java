package com.example.docassistant.service.rag;

import com.example.docassistant.service.chunking.TokenDocumentChunker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class DocumentRetrievalServiceImpl implements DocumentRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(DocumentRetrievalServiceImpl.class);

    private final VectorStore vectorStore;
    private final int topK;
    private final double similarityThreshold;

    public DocumentRetrievalServiceImpl(
            VectorStore vectorStore,
            @Value("${spring.ai.rag.retrieval.top-k:4}") int topK,
            @Value("${spring.ai.rag.retrieval.similarity-threshold:0.0}") double similarityThreshold) {
        this.vectorStore = vectorStore;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
    }

    @Override
    public List<Document> retrieveRelevantChunks(UUID documentId, String query) {
        Objects.requireNonNull(documentId, "Document ID must not be null");
        Objects.requireNonNull(query, "Query must not be null");

        log.debug("Retrieving chunks for documentId={}, query='{}', topK={}, threshold={}",
                documentId, query, topK, similarityThreshold);

        // Construct programmatic, safe filter expression strictly using application-controlled keys
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        var filterExpr = b.eq(TokenDocumentChunker.METADATA_DOCUMENT_ID, documentId.toString()).build();

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .filterExpression(filterExpr)
                .build();

        try {
            List<Document> results = vectorStore.similaritySearch(request);
            List<Document> verifiedResults = (results != null)
                    ? results.stream()
                        .filter(doc -> isChunkForDocument(doc, documentId))
                        .toList()
                    : List.of();

            // Resilient fallback: If provider-specific FilterExpression dropped candidates or was unsupported,
            // perform standard similarity search and apply rigorous application-level metadata filtering
            if (verifiedResults.isEmpty()) {
                log.debug("No results with filter expression, performing fallback similarity search for documentId={}", documentId);
                SearchRequest fallbackRequest = SearchRequest.builder()
                        .query(query)
                        .topK(Math.max(topK * 5, 20))
                        .similarityThreshold(similarityThreshold)
                        .build();

                List<Document> fallbackResults = vectorStore.similaritySearch(fallbackRequest);
                if (fallbackResults != null) {
                    verifiedResults = fallbackResults.stream()
                            .filter(doc -> isChunkForDocument(doc, documentId))
                            .limit(topK)
                            .toList();
                }
            }

            log.info("Retrieved {} relevant chunks for documentId={}", verifiedResults.size(), documentId);
            return verifiedResults;
        } catch (Exception ex) {
            log.error("Failed during vector similarity search for documentId={}: {}", documentId, ex.getMessage(), ex);
            throw ex;
        }
    }

    private boolean isChunkForDocument(Document doc, UUID documentId) {
        if (doc == null || doc.getMetadata() == null) {
            return false;
        }
        Object chunkDocId = doc.getMetadata().get(TokenDocumentChunker.METADATA_DOCUMENT_ID);
        if (chunkDocId == null) {
            chunkDocId = doc.getMetadata().get("documentId");
        }
        return documentId.toString().equalsIgnoreCase(chunkDocId != null ? chunkDocId.toString() : null);
    }
}
