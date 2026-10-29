package com.example.docassistant.service.extraction;

import com.example.docassistant.domain.VisualAssetRecord;
import com.example.docassistant.domain.VisualAssetType;
import com.example.docassistant.exception.PdfExtractionException;
import com.example.docassistant.repository.VisualAssetRepository;
import com.example.docassistant.service.chunking.TokenDocumentChunker;
import com.example.docassistant.service.extraction.layout.ExtractedImageRegion;
import com.example.docassistant.service.extraction.layout.LayoutAwareDocumentExtractor;
import com.example.docassistant.service.extraction.layout.StructuredDocumentResult;
import com.example.docassistant.service.extraction.layout.StructuredPageContent;
import com.example.docassistant.service.vision.ImageUnderstandingService;
import com.example.docassistant.service.vision.VisualAnalysisDescription;
import com.example.docassistant.storage.DocumentStorage;
import com.example.docassistant.storage.VisualAssetStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;

@Component
@Primary
public class LayoutAwarePdfContentExtractor implements DocumentContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(LayoutAwarePdfContentExtractor.class);

    private final DocumentStorage documentStorage;
    private final LayoutAwareDocumentExtractor layoutExtractor;
    private final VisualAssetStorage visualAssetStorage;
    private final VisualAssetRepository visualAssetRepository;
    private final ImageUnderstandingService imageUnderstandingService;

    public LayoutAwarePdfContentExtractor(
            DocumentStorage documentStorage,
            LayoutAwareDocumentExtractor layoutExtractor,
            VisualAssetStorage visualAssetStorage,
            VisualAssetRepository visualAssetRepository,
            ImageUnderstandingService imageUnderstandingService) {
        this.documentStorage = documentStorage;
        this.layoutExtractor = layoutExtractor;
        this.visualAssetStorage = visualAssetStorage;
        this.visualAssetRepository = visualAssetRepository;
        this.imageUnderstandingService = imageUnderstandingService;
    }

    @Override
    public List<Document> extract(String storageKey) {
        Resource pdfResource = documentStorage.loadAsResource(storageKey);
        UUID documentId = extractDocumentIdFromKey(storageKey);

        StructuredDocumentResult result;
        try (InputStream is = pdfResource.getInputStream()) {
            result = layoutExtractor.extractStructured(documentId, is);
        } catch (Exception ex) {
            log.error("Failed to extract structured content from PDF key {}: {}", storageKey, ex.getMessage());
            throw new PdfExtractionException("Unable to parse PDF document: " + ex.getMessage(), ex);
        }

        List<Document> springDocs = new ArrayList<>();

        for (StructuredPageContent page : result.pages()) {
            String pageText = page.getFullOrderedText();
            if (pageText != null && !pageText.isBlank()) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put(TokenDocumentChunker.METADATA_PAGE_NUMBER, page.pageNumber());
                metadata.put("multiColumn", page.multiColumnDetected());
                metadata.put("uncertainReadingOrder", page.isUncertainReadingOrder());
                metadata.put("ocrApplied", page.isOcrApplied());
                metadata.put(TokenDocumentChunker.METADATA_SOURCE_TYPE, page.isOcrApplied() ? "OCR" : "TEXT");

                springDocs.add(new Document(pageText, metadata));
            }

            // Process visual assets on this page
            if (page.images() != null && !page.images().isEmpty()) {
                for (ExtractedImageRegion imgRegion : page.images()) {
                    try {
                        processAndIndexVisualAsset(documentId, page.pageNumber(), imgRegion, pageText, springDocs);
                    } catch (Exception e) {
                        log.warn("Failed to process visual asset on page {}: {}", page.pageNumber(), e.getMessage());
                    }
                }
            }
        }

        boolean hasUsableContent = !springDocs.isEmpty() && springDocs.stream()
                .anyMatch(doc -> doc.getText() != null && !doc.getText().isBlank());

        if (!hasUsableContent) {
            throw new PdfExtractionException("PDF produces no usable extracted text (may be scanned or empty without OCR)");
        }

        log.info("Layout extraction produced {} total content documents (text + visual descriptions) for key {}", springDocs.size(), storageKey);
        return springDocs;
    }

    private void processAndIndexVisualAsset(
            UUID documentId,
            int pageNumber,
            ExtractedImageRegion imgRegion,
            String surroundingPageText,
            List<Document> outDocs) {

        UUID assetId = imgRegion.assetId();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(imgRegion.image(), "png", baos);
        } catch (Exception e) {
            log.warn("Failed to write image to buffer for asset {}: {}", assetId, e.getMessage());
            return;
        }

        byte[] imgBytes = baos.toByteArray();
        String assetKey = visualAssetStorage.storeAsset(documentId, assetId, ".png", new ByteArrayInputStream(imgBytes));

        VisualAssetRecord assetRecord = VisualAssetRecord.createNew(
                documentId,
                pageNumber,
                imgRegion.assetType(),
                assetKey,
                "image/png",
                imgRegion.caption(),
                imgRegion.boundingBox() != null ? imgRegion.boundingBox().toCoordinateString() : null
        );

        String descriptionText = "";
        if (imageUnderstandingService.isVisionModelConfigured()) {
            try {
                Resource assetResource = visualAssetStorage.loadAssetAsResource(assetKey);
                VisualAnalysisDescription desc = imageUnderstandingService.analyzeImage(assetRecord, assetResource, imgRegion.caption() != null ? imgRegion.caption() : surroundingPageText);
                descriptionText = desc.toSearchableText();
                assetRecord.updateProcessing(com.example.docassistant.domain.VisualAssetProcessingStatus.PROCESSED, descriptionText);
            } catch (Exception e) {
                log.warn("Vision model analysis failed for asset {}: {}", assetId, e.getMessage());
                assetRecord.updateProcessing(com.example.docassistant.domain.VisualAssetProcessingStatus.FAILED, "Analysis failed: " + e.getMessage());
            }
        } else {
            // When vision model not enabled/configured, use caption or default description
            descriptionText = (imgRegion.caption() != null && !imgRegion.caption().isBlank())
                    ? "Visual Figure: " + imgRegion.caption()
                    : "Figure on page " + pageNumber;
            assetRecord.updateProcessing(com.example.docassistant.domain.VisualAssetProcessingStatus.SKIPPED, descriptionText);
        }

        visualAssetRepository.save(assetRecord);

        // Create an indexed document for the visual description
        Map<String, Object> meta = new HashMap<>();
        meta.put(TokenDocumentChunker.METADATA_PAGE_NUMBER, pageNumber);
        meta.put(TokenDocumentChunker.METADATA_SOURCE_TYPE, "VISUAL_ASSET");
        meta.put(TokenDocumentChunker.METADATA_ASSET_ID, assetId.toString());
        if (imgRegion.caption() != null) {
            meta.put(TokenDocumentChunker.METADATA_CAPTION, imgRegion.caption());
        }

        String indexedContent = "[Figure on page " + pageNumber + "]"
                + (imgRegion.caption() != null ? " Caption: " + imgRegion.caption() + "\n" : "\n")
                + descriptionText;

        outDocs.add(new Document(indexedContent, meta));
    }

    private UUID extractDocumentIdFromKey(String storageKey) {
        try {
            int slash = storageKey.lastIndexOf('/');
            String filename = slash >= 0 ? storageKey.substring(slash + 1) : storageKey;
            int dot = filename.indexOf('.');
            String uuidPart = dot >= 0 ? filename.substring(0, dot) : filename;
            return UUID.fromString(uuidPart);
        } catch (Exception e) {
            return UUID.randomUUID();
        }
    }
}

