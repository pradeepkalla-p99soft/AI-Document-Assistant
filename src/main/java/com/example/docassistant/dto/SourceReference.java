package com.example.docassistant.dto;

import java.util.UUID;

public record SourceReference(
        UUID documentId,
        String filename,
        Integer pageNumber,
        String excerpt
) {
}
