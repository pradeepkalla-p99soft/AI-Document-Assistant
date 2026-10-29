package com.example.docassistant.service.extraction.layout;

import java.io.InputStream;
import java.util.UUID;

public interface LayoutAwareDocumentExtractor {
    StructuredDocumentResult extractStructured(UUID documentId, InputStream pdfInputStream);
}

