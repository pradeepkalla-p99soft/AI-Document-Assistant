package com.example.docassistant.service.rag;

import org.springframework.ai.document.Document;

import java.util.List;
import java.util.UUID;

public interface DocumentRetrievalService {

    /**
     * Retrieves relevant document chunks scoped strictly to the specified document UUID.
     *
     * @param documentId the validated document ID
     * @param query      the semantic search query
     * @return list of matching chunks meeting the configured similarity threshold and count limit
     */
    List<Document> retrieveRelevantChunks(UUID documentId, String query);
}
