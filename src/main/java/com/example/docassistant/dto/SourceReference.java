package com.example.docassistant.dto;

import java.util.UUID;

public record SourceReference(
        UUID documentId,
        String filename,
        Integer pageNumber,
        String excerpt,
        String sourceType,
        UUID visualAssetId,
        String caption
) {
    public SourceReference(UUID documentId, String filename, Integer pageNumber, String excerpt) {
        this(documentId, filename, pageNumber, excerpt, "TEXT", null, null);
    }
}
