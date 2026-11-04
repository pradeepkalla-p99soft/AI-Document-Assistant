package com.example.docassistant.service.indexing;

import com.example.docassistant.domain.DocumentRecord;
import com.example.docassistant.domain.DocumentStatus;
import com.example.docassistant.exception.PdfExtractionException;
import com.example.docassistant.repository.DocumentRepository;
import com.example.docassistant.repository.VisualAssetRepository;
import com.example.docassistant.service.chunking.DocumentChunker;
import com.example.docassistant.service.extraction.DocumentContentExtractor;
import com.example.docassistant.storage.VisualAssetStorage;
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
    private final VisualAssetRepository visualAssetRepository;
    private final VisualAssetStorage visualAssetStorage;

    public DocumentProcessingServiceImpl(
            DocumentRepository documentRepository,
            DocumentContentExtractor contentExtractor,
            DocumentChunker documentChunker,
            VectorStore vectorStore,
            VisualAssetRepository visualAssetRepository,
            VisualAssetStorage visualAssetStorage) {
        this.documentRepository = documentRepository;
        this.contentExtractor = contentExtractor;
        this.documentChunker = documentChunker;
        this.vectorStore = vectorStore;
        this.visualAssetRepository = visualAssetRepository;
        this.visualAssetStorage = visualAssetStorage;
    }

    @Override
    public DocumentRecord processDocument(DocumentRecord record) {
        Objects.requireNonNull(record, "DocumentRecord must not be null");
        log.info("Starting processing pipeline for document ID: {}, filename: {}", record.getId(), record.getFilename());

        // 1. Mark status as PROCESSING
        record.updateStatus(DocumentStatus.PROCESSING, null);
        record = documentRepository.save(record);

        // Pre-clean previous assets and vector chunks for idempotent reprocessing
        cleanUpPreviousData(record.getId());

        List<Document> chunks = List.of();
        try {
            // 2. Extract structured page documents & visual descriptions from storage
            List<Document> pages = contentExtractor.extract(record.getStorageKey(), record.getId());

            // 3. Split into token-bounded chunks with preserved metadata
            chunks = documentChunker.chunk(pages, record.getId(), record.getFilename());

            if (chunks.isEmpty()) {
                throw new PdfExtractionException("No text chunks generated for document");
            }

            // 4. Generate embeddings and persist chunks in vector store (PgVectorStore persists automatically via JDBC)
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
            cleanUpPreviousData(record.getId());
            record.updateStatus(DocumentStatus.FAILED, "TEXT_EXTRACTION_FAILED: " + ex.getMessage());
            return documentRepository.save(record);

        } catch (Exception ex) {
            log.error("Indexing/Embedding failed for document ID: {}. Error: {}", record.getId(), ex.getMessage(), ex);
            cleanUpChunks(record.getId().toString(), chunks);
            cleanUpPreviousData(record.getId());
            String sanitizedError = sanitizeErrorMessage(ex);
            record.updateStatus(DocumentStatus.FAILED, sanitizedError);
            return documentRepository.save(record);
        }
    }

    private void cleanUpPreviousData(java.util.UUID documentId) {
        try {
            visualAssetRepository.deleteByDocumentId(documentId);
            visualAssetStorage.deleteAssetsForDocument(documentId);
        } catch (Exception e) {
            log.warn("Failed to cleanup previous visual assets for document {}: {}", documentId, e.getMessage());
        }
    }

    private void cleanUpChunks(String documentId, List<Document> chunks) {
        try {
            String filter = String.format("documentId == '%s'", documentId);
            vectorStore.delete(filter);
            log.debug("Cleaned up vector store chunks for documentId via filter: {}", documentId);
        } catch (Exception filterEx) {
            log.warn("Vector store delete by filter expression failed ({}); falling back to chunk IDs", filterEx.getMessage());
            if (chunks != null && !chunks.isEmpty()) {
                try {
                    List<String> chunkIds = chunks.stream().map(Document::getId).toList();
                    vectorStore.delete(chunkIds);
                    log.debug("Cleaned up vector store chunks via explicit IDs for documentId: {}", documentId);
                } catch (Exception idEx) {
                    log.warn("Vector store fallback cleanup by chunk IDs also failed: {}", idEx.getMessage());
                }
            }
        }
    }

    private String sanitizeErrorMessage(Throwable ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "An internal error occurred during document indexing";
        }
        return message.length() > 500 ? message.substring(0, 500) + "..." : message;
    }
}
