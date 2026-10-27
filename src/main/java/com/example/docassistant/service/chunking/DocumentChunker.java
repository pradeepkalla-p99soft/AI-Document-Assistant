package com.example.docassistant.service.chunking;

import org.springframework.ai.document.Document;

import java.util.List;
import java.util.UUID;

/**
 * Pipeline strategy for splitting extracted document pages into token-bounded chunks
 * while preserving source document and page metadata.
 */
public interface DocumentChunker {

    /**
     * Splits extracted page documents into structured chunks with enriched metadata.
     *
     * @param extractedPages page-level Document instances from the extractor
     * @param documentId     the UUID of the parent document
     * @param filename       the original filename
     * @return list of chunked Document instances ready for embedding
     */
    List<Document> chunk(List<Document> extractedPages, UUID documentId, String filename);
}
