package com.example.docassistant.service;

import com.example.docassistant.domain.DocumentRecord;
import com.example.docassistant.dto.DocumentResponse;
import com.example.docassistant.exception.DocumentNotFoundException;
import com.example.docassistant.exception.StorageException;
import com.example.docassistant.repository.DocumentRepository;
import com.example.docassistant.storage.DocumentStorage;
import com.example.docassistant.validation.PdfFileValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.UUID;

@Service
public class DocumentServiceImpl implements DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentServiceImpl.class);

    private final DocumentRepository documentRepository;
    private final DocumentStorage documentStorage;
    private final PdfFileValidator pdfFileValidator;

    public DocumentServiceImpl(
            DocumentRepository documentRepository,
            DocumentStorage documentStorage,
            PdfFileValidator pdfFileValidator) {
        this.documentRepository = documentRepository;
        this.documentStorage = documentStorage;
        this.pdfFileValidator = pdfFileValidator;
    }

    @Override
    @Transactional
    public DocumentResponse uploadDocument(MultipartFile file) {
        // 1. Validate incoming file
        pdfFileValidator.validate(file);

        // 2. Sanitize original filename (keep basename only)
        String rawFilename = file.getOriginalFilename();
        String sanitizedFilename = rawFilename != null
                ? Paths.get(rawFilename).getFileName().toString()
                : "document.pdf";

        // 3. Generate safe storage key
        String storageKey = UUID.randomUUID() + ".pdf";
        String contentType = file.getContentType() != null ? file.getContentType() : "application/pdf";

        // 4. Store file to document storage
        try (InputStream inputStream = file.getInputStream()) {
            documentStorage.store(storageKey, inputStream);
        } catch (IOException e) {
            throw new StorageException("Failed to read upload input stream for: " + sanitizedFilename, e);
        }

        // 5. Persist metadata in database with compensating cleanup on failure
        DocumentRecord record = DocumentRecord.createUploaded(
                sanitizedFilename,
                storageKey,
                contentType,
                file.getSize()
        );

        try {
            DocumentRecord savedRecord = documentRepository.save(record);
            log.info("Document successfully uploaded and saved with ID: {}, storageKey: {}",
                    savedRecord.getId(), storageKey);
            return DocumentResponse.from(savedRecord);
        } catch (Exception ex) {
            // Compensating transaction: remove physical file if database persistence fails
            log.error("Database save failed for document {}. Performing compensating file deletion.", storageKey, ex);
            try {
                documentStorage.delete(storageKey);
            } catch (Exception cleanupEx) {
                log.warn("Compensating cleanup failed to delete file: {}", storageKey, cleanupEx);
            }
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse getDocument(UUID id) {
        Objects.requireNonNull(id, "Document ID must not be null");
        DocumentRecord record = documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found with ID: " + id));
        return DocumentResponse.from(record);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DocumentResponse> listDocuments(Pageable pageable) {
        Objects.requireNonNull(pageable, "Pageable must not be null");
        return documentRepository.findAll(pageable)
                .map(DocumentResponse::from);
    }
}
