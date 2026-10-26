package com.example.docassistant.dto;

import com.example.docassistant.domain.DocumentRecord;
import com.example.docassistant.domain.DocumentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String filename,
        String storageKey,
        String contentType,
        long sizeBytes,
        DocumentStatus status,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static DocumentResponse from(DocumentRecord record) {
        return new DocumentResponse(
                record.getId(),
                record.getFilename(),
                record.getStorageKey(),
                record.getContentType(),
                record.getSizeBytes(),
                record.getStatus(),
                record.getErrorMessage(),
                record.getCreatedAt(),
                record.getUpdatedAt()
        );
    }
}
