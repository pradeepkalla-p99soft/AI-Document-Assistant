package com.example.docassistant.service.extraction.layout;

import java.util.List;

public record StructuredPageContent(
        int pageNumber,
        List<ExtractedTextBlock> textBlocks,
        List<ExtractedImageRegion> images,
        boolean multiColumnDetected,
        boolean isUncertainReadingOrder,
        boolean isOcrApplied,
        List<String> warnings
) {
    public String getFullOrderedText() {
        if (textBlocks == null || textBlocks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ExtractedTextBlock block : textBlocks) {
            if (block.text() != null && !block.text().isBlank()) {
                sb.append(block.text().trim()).append("\n\n");
            }
        }
        return sb.toString().trim();
    }
}

