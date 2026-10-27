package com.example.docassistant.service.indexing;

import com.example.docassistant.domain.DocumentRecord;

/**
 * Service orchestrating the complete document ingestion pipeline:
 * extraction -> chunking -> embedding -> vector indexing -> status update.
 */
public interface DocumentProcessingService {

    /**
     * Processes and indexes a stored document.
     *
     * @param record the document record to ingest
     * @return the updated document record reflecting final state (READY or FAILED)
     */
    DocumentRecord processDocument(DocumentRecord record);
}
