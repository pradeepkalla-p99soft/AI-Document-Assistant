package com.example.docassistant.service.extraction.layout;

import java.util.List;

public record ExtractedTextBlock(
        String text,
        BoundingBox boundingBox,
        int readingOrderIndex,
        boolean isHeading,
        boolean isCaption,
        boolean isOcr
) {
}

