package com.example.docassistant.service.extraction.layout;

import java.util.List;

public record StructuredDocumentResult(
        List<StructuredPageContent> pages,
        List<String> warnings
) {
}

