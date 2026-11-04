package com.example.docassistant.service.extraction;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Strategy interface for extracting Spring AI Document pages from stored document files.
 */
public interface DocumentContentExtractor {

    /**
     * Extracts text content and page-level metadata from the specified document storage key.
     *
     * @param storageKey the application-generated storage key
     * @return non-empty list of page-level Spring AI Document objects
     * @throws com.example.docassistant.exception.PdfExtractionException if unreadable or no usable text is found
     */
    List<Document> extract(String storageKey);

    /**
     * Extracts text content and page-level metadata scoped to the given document entity ID.
     */
    default List<Document> extract(String storageKey, java.util.UUID documentId) {
        return extract(storageKey);
    }
}
