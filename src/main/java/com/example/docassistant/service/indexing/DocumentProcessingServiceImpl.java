package com.example.docassistant.service.indexing;

import com.example.docassistant.domain.DocumentRecord;
import com.example.docassistant.domain.DocumentStatus;
import com.example.docassistant.exception.PdfExtractionException;
import com.example.docassistant.repository.DocumentRepository;
import com.example.docassistant.service.chunking.DocumentChunker;
import com.example.docassistant.service.extraction.DocumentContentExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class DocumentProcessingServiceImpl implements DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingServiceImpl.class);

    private final DocumentRepository documentRepository;
    private final DocumentContentExtractor contentExtractor;
    private final DocumentChunker documentChunker;
    private final VectorStore vectorStore;

    public DocumentProcessingServiceImpl(
            DocumentRepository documentRepository,
            DocumentContentExtractor contentExtractor,
            DocumentChunker documentChunker,
            VectorStore vectorStore) {
        this.documentRepository = documentRepository;
        this.contentExtractor = contentExtractor;
        this.documentChunker = documentChunker;
        this.vectorStore = vectorStore;
    }

    @Override
    public DocumentRecord processDocument(DocumentRecord record) {
        Objects.requireNonNull(record, "DocumentRecord must not be null");
        log.info("Starting processing pipeline for document ID: {}, filename: {}", record.getId(), record.getFilename());

        // 1. Mark status as PROCESSING in a discrete database transaction
        record.updateStatus(DocumentStatus.PROCESSING, null);
        record = documentRepository.save(record);

        List<Document> chunks = List.of();
        try {
            // 2. Extract page documents from storage
            List<Document> pages = contentExtractor.extract(record.getStorageKey());

            // 3. Split into token-bounded chunks with preserved metadata
            chunks = documentChunker.chunk(pages, record.getId(), record.getFilename());

            if (chunks.isEmpty()) {
                throw new PdfExtractionException("No text chunks generated for document");
            }

            // 4. Generate embeddings and persist chunks in vector store
            //    (Pre-cleanup is skipped on first run; cleanup only runs in error paths to ensure idempotency)
            log.info("Indexing {} chunks in vector store for document ID: {}", chunks.size(), record.getId());
            vectorStore.add(chunks);

            // 5. Transition to READY upon successful vector indexing
            record.updateStatus(DocumentStatus.READY, null);
            record = documentRepository.save(record);
            log.info("Successfully indexed document ID: {}", record.getId());
            return record;

        } catch (PdfExtractionException ex) {
            log.warn("Extraction failed for document ID: {}. Error: {}", record.getId(), ex.getMessage());
            cleanUpChunks(record.getId().toString(), chunks);
            record.updateStatus(DocumentStatus.FAILED, "TEXT_EXTRACTION_FAILED: " + ex.getMessage());
            return documentRepository.save(record);

        } catch (Exception ex) {
            log.error("Indexing/Embedding failed for document ID: {}. Error: {}", record.getId(), ex.getMessage(), ex);
            cleanUpChunks(record.getId().toString(), chunks);
            String sanitizedError = sanitizeErrorMessage(ex);
            record.updateStatus(DocumentStatus.FAILED, sanitizedError);
            return documentRepository.save(record);
        }
    }

    private void cleanUpChunks(String documentId, List<Document> chunks) {
        try {
            // First attempt: Delete by documentId metadata filter
            String filter = String.format("documentId == '%s'", documentId);
            vectorStore.delete(filter);
            log.debug("Cleaned up vector store chunks for documentId via filter: {}", documentId);
        } catch (Exception filterEx) {
            log.warn("Vector store delete by filter expression failed ({}); falling back to chunk IDs", filterEx.getMessage());
            // Fallback attempt: Delete by explicit chunk IDs if available
            if (chunks != null && !chunks.isEmpty()) {
                try {
                    List<String> chunkIds = chunks.stream().map(Document::getId).toList();
                    vectorStore.delete(chunkIds);
                    log.debug("Cleaned up vector store chunks via explicit IDs for documentId: {}", documentId);
                } catch (Exception idEx) {
                    log.warn("Vector store fallback cleanup by ID failed: {}", idEx.getMessage());
                }
            }
        }
    }

    private String sanitizeErrorMessage(Exception ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        if (msg.toLowerCase().contains("connection") || msg.toLowerCase().contains("refused")
                || msg.toLowerCase().contains("timeout") || msg.toLowerCase().contains("unavailable")) {
            return "EMBEDDING_SERVICE_UNAVAILABLE: The AI embedding service could not be reached.";
        }
        if (msg.toLowerCase().contains("dimension") || msg.toLowerCase().contains("vector")) {
            return "VECTOR_STORE_ERROR: Vector dimensionality or storage operation failed.";
        }
        return "INDEXING_FAILED: Internal error occurred during document indexing.";
    }
}
