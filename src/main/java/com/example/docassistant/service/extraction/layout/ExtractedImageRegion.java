package com.example.docassistant.service.extraction.layout;

import com.example.docassistant.domain.VisualAssetType;

import java.awt.image.BufferedImage;
import java.util.UUID;

public record ExtractedImageRegion(
        UUID assetId,
        int pageNumber,
        VisualAssetType assetType,
        BufferedImage image,
        String mimeType,
        BoundingBox boundingBox,
        String caption
) {
}

