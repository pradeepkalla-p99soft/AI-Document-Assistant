package com.example.docassistant.service.extraction.layout;

import com.example.docassistant.domain.VisualAssetType;
import com.example.docassistant.exception.PdfExtractionException;
import com.example.docassistant.service.extraction.ocr.OcrService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.*;

@Component
public class PdfBoxCoordinateExtractor implements LayoutAwareDocumentExtractor {

    private static final Logger log = LoggerFactory.getLogger(PdfBoxCoordinateExtractor.class);

    private final OcrService ocrService;
    private final int maxImagesPerPage;
    private final int minCharsForTextExtraction;
    private final boolean renderPageIfNoImages;

    public PdfBoxCoordinateExtractor(
            OcrService ocrService,
            @Value("${extraction.max-images-per-page:5}") int maxImagesPerPage,
            @Value("${extraction.min-chars-per-page:50}") int minCharsForTextExtraction,
            @Value("${extraction.render-page-if-no-images:true}") boolean renderPageIfNoImages) {
        this.ocrService = ocrService;
        this.maxImagesPerPage = maxImagesPerPage;
        this.minCharsForTextExtraction = minCharsForTextExtraction;
        this.renderPageIfNoImages = renderPageIfNoImages;
    }

    @Override
    public StructuredDocumentResult extractStructured(UUID documentId, InputStream pdfInputStream) {
        List<StructuredPageContent> pages = new ArrayList<>();
        List<String> docWarnings = new ArrayList<>();

        byte[] pdfBytes;
        try {
            pdfBytes = pdfInputStream.readAllBytes();
        } catch (IOException e) {
            throw new PdfExtractionException("Failed to read PDF input stream: " + e.getMessage(), e);
        }

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();

            for (int pageIdx = 0; pageIdx < pageCount; pageIdx++) {
                int physicalPageNumber = pageIdx + 1;
                PDPage pdPage = document.getPage(pageIdx);
                PDRectangle mediaBox = pdPage.getMediaBox();
                float pageWidth = mediaBox.getWidth();
                float pageHeight = mediaBox.getHeight();

                List<String> pageWarnings = new ArrayList<>();
                List<ExtractedImageRegion> images = extractImagesFromPage(pdPage, renderer, pageIdx, physicalPageNumber, pageWarnings);

                // Extract text positions using custom stripper
                CoordinateStripper stripper = new CoordinateStripper();
                stripper.setStartPage(physicalPageNumber);
                stripper.setEndPage(physicalPageNumber);
                stripper.getText(document);

                List<ExtractedTextBlock> rawBlocks = stripper.getBlocks();
                boolean isOcrApplied = false;

                // Check if extracted text is too sparse (e.g. scanned page)
                int totalTextLength = rawBlocks.stream().mapToInt(b -> b.text().length()).sum();
                if (totalTextLength < minCharsForTextExtraction) {
                    if (ocrService.isAvailable()) {
                        try {
                            BufferedImage pageImg = renderer.renderImageWithDPI(pageIdx, 150);
                            String ocrText = ocrService.extractText(pageImg);
                            if (ocrText != null && !ocrText.isBlank()) {
                                rawBlocks = List.of(new ExtractedTextBlock(
                                        ocrText.trim(),
                                        new BoundingBox(0, 0, pageWidth, pageHeight),
                                        0,
                                        false,
                                        false,
                                        true
                                ));
                                isOcrApplied = true;
                                log.info("OCR successfully applied for document {} page {}", documentId, physicalPageNumber);
                            }
                        } catch (Exception ocrEx) {
                            pageWarnings.add("OCR failed on page " + physicalPageNumber + ": " + ocrEx.getMessage());
                            log.warn("OCR failed on page {}: {}", physicalPageNumber, ocrEx.getMessage());
                        }
                    } else {
                        pageWarnings.add("Page " + physicalPageNumber + " contains sparse text (" + totalTextLength + " chars) and OCR is unavailable/disabled");
                    }
                }

                // Analyze reading order (detect 2 columns)
                boolean multiColumnDetected = false;
                boolean isUncertainReadingOrder = false;
                List<ExtractedTextBlock> orderedBlocks = new ArrayList<>();

                if (!isOcrApplied && !rawBlocks.isEmpty()) {
                    LayoutAnalysisResult layoutAnalysis = orderBlocks(rawBlocks, pageWidth, pageHeight);
                    orderedBlocks = layoutAnalysis.orderedBlocks();
                    multiColumnDetected = layoutAnalysis.multiColumn();
                    isUncertainReadingOrder = layoutAnalysis.uncertain();
                } else {
                    orderedBlocks = rawBlocks;
                }

                // Link captions to visual assets if captions detected
                associateCaptions(orderedBlocks, images);

                pages.add(new StructuredPageContent(
                        physicalPageNumber,
                        orderedBlocks,
                        images,
                        multiColumnDetected,
                        isUncertainReadingOrder,
                        isOcrApplied,
                        pageWarnings
                ));
                docWarnings.addAll(pageWarnings);
            }

        } catch (Exception e) {
            log.error("Failed to parse PDF document {}: {}", documentId, e.getMessage(), e);
            throw new PdfExtractionException("Failed to parse PDF document: " + e.getMessage(), e);
        }

        return new StructuredDocumentResult(pages, docWarnings);
    }

    private List<ExtractedImageRegion> extractImagesFromPage(
            PDPage pdPage, PDFRenderer renderer, int pageIdx, int physicalPageNum, List<String> warnings) {
        List<ExtractedImageRegion> images = new ArrayList<>();
        PDResources resources = pdPage.getResources();
        if (resources != null) {
            int count = 0;
            for (COSName name : resources.getXObjectNames()) {
                if (count >= maxImagesPerPage) {
                    warnings.add("Page " + physicalPageNum + " exceeded max image limit (" + maxImagesPerPage + "). Extra images skipped.");
                    break;
                }
                try {
                    PDXObject xobject = resources.getXObject(name);
                    if (xobject instanceof PDImageXObject imgXObject) {
                        BufferedImage bImg = imgXObject.getImage();
                        // Filter out tiny icons / 1x1 tracking pixels
                        if (bImg != null && bImg.getWidth() >= 50 && bImg.getHeight() >= 50) {
                            images.add(new ExtractedImageRegion(
                                    UUID.randomUUID(),
                                    physicalPageNum,
                                    VisualAssetType.EMBEDDED_FIGURE,
                                    bImg,
                                    "image/png",
                                    new BoundingBox(0, 0, bImg.getWidth(), bImg.getHeight()),
                                    null
                            ));
                            count++;
                        }
                    }
                } catch (Exception e) {
                    warnings.add("Failed to extract image " + name.getName() + " on page " + physicalPageNum + ": " + e.getMessage());
                }
            }
        }

        // If no embedded raster images found, but document is configured to render vector graphics/page as image:
        if (images.isEmpty() && renderPageIfNoImages) {
            try {
                BufferedImage renderedPage = renderer.renderImageWithDPI(pageIdx, 100);
                if (renderedPage != null) {
                    images.add(new ExtractedImageRegion(
                            UUID.randomUUID(),
                            physicalPageNum,
                            VisualAssetType.RENDERED_REGION,
                            renderedPage,
                            "image/png",
                            new BoundingBox(0, 0, renderedPage.getWidth(), renderedPage.getHeight()),
                            "Full page rendered visual overview"
                    ));
                }
            } catch (Exception e) {
                log.debug("Failed to render page {} visual region: {}", physicalPageNum, e.getMessage());
            }
        }

        return images;
    }

    private void associateCaptions(List<ExtractedTextBlock> blocks, List<ExtractedImageRegion> images) {
        if (images.isEmpty() || blocks.isEmpty()) return;

        for (ExtractedTextBlock block : blocks) {
            String lower = block.text().trim().toLowerCase();
            if (lower.startsWith("figure ") || lower.startsWith("fig. ") || lower.startsWith("table ") || lower.startsWith("chart ")) {
                // Associate with first uncaptioned image on this page
                for (int i = 0; i < images.size(); i++) {
                    ExtractedImageRegion img = images.get(i);
                    if (img.caption() == null || img.caption().isBlank()) {
                        images.set(i, new ExtractedImageRegion(
                                img.assetId(),
                                img.pageNumber(),
                                img.assetType(),
                                img.image(),
                                img.mimeType(),
                                img.boundingBox(),
                                block.text().trim()
                        ));
                        break;
                    }
                }
            }
        }
    }

    record LayoutAnalysisResult(List<ExtractedTextBlock> orderedBlocks, boolean multiColumn, boolean uncertain) {}

    public LayoutAnalysisResult orderBlocks(List<ExtractedTextBlock> blocks, float pageWidth, float pageHeight) {
        if (blocks.size() <= 1) {
            return new LayoutAnalysisResult(blocks, false, false);
        }

        float midX = pageWidth / 2.0f;
        List<ExtractedTextBlock> leftColumn = new ArrayList<>();
        List<ExtractedTextBlock> rightColumn = new ArrayList<>();
        List<ExtractedTextBlock> spanningBlocks = new ArrayList<>();

        for (ExtractedTextBlock block : blocks) {
            BoundingBox box = block.boundingBox();
            float blockCenterX = box.x() + (box.width() / 2.0f);
            boolean spansWidth = box.width() > (pageWidth * 0.65f);

            if (spansWidth) {
                spanningBlocks.add(block);
            } else if (blockCenterX < midX) {
                leftColumn.add(block);
            } else {
                rightColumn.add(block);
            }
        }

        // Two-column heuristics: both left and right columns have significant text blocks
        boolean isTwoColumn = (leftColumn.size() >= 2 && rightColumn.size() >= 2);

        if (!isTwoColumn) {
            // Standard single-column reading order: top-to-bottom
            List<ExtractedTextBlock> sorted = new ArrayList<>(blocks);
            sorted.sort(Comparator.comparingDouble((ExtractedTextBlock b) -> b.boundingBox().y()));
            return new LayoutAnalysisResult(sorted, false, false);
        }

        // Two-column layout ordering:
        // Top spanning blocks -> Left column (top-to-bottom) -> Right column (top-to-bottom) -> Bottom spanning blocks
        leftColumn.sort(Comparator.comparingDouble(b -> b.boundingBox().y()));
        rightColumn.sort(Comparator.comparingDouble(b -> b.boundingBox().y()));

        float leftMinY = leftColumn.get(0).boundingBox().y();
        float leftMaxY = leftColumn.get(leftColumn.size() - 1).boundingBox().y();

        List<ExtractedTextBlock> topSpanning = new ArrayList<>();
        List<ExtractedTextBlock> bottomSpanning = new ArrayList<>();

        for (ExtractedTextBlock span : spanningBlocks) {
            if (span.boundingBox().y() < leftMinY) {
                topSpanning.add(span);
            } else {
                bottomSpanning.add(span);
            }
        }

        topSpanning.sort(Comparator.comparingDouble(b -> b.boundingBox().y()));
        bottomSpanning.sort(Comparator.comparingDouble(b -> b.boundingBox().y()));

        List<ExtractedTextBlock> result = new ArrayList<>();
        result.addAll(topSpanning);
        result.addAll(leftColumn);
        result.addAll(rightColumn);
        result.addAll(bottomSpanning);

        // Check uncertainty: if any block crosses middle boundary irregularly
        boolean uncertain = spanningBlocks.size() > (blocks.size() / 2);

        return new LayoutAnalysisResult(result, true, uncertain);
    }

    private static class CoordinateStripper extends PDFTextStripper {
        private final List<ExtractedTextBlock> blocks = new ArrayList<>();
        private final StringBuilder currentParagraph = new StringBuilder();
        private float startX = 0;
        private float startY = 0;
        private float endX = 0;
        private float endY = 0;
        private boolean inParagraph = false;
        private int blockIdx = 0;

        public CoordinateStripper() throws IOException {
            super();
            setSortByPosition(true);
        }

        public List<ExtractedTextBlock> getBlocks() {
            flushParagraph();
            return blocks;
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            if (text == null || text.isBlank() || textPositions.isEmpty()) {
                return;
            }

            for (TextPosition tp : textPositions) {
                float x = tp.getXDirAdj();
                float y = tp.getYDirAdj();
                float w = tp.getWidthDirAdj();
                float h = tp.getHeightDir();

                if (!inParagraph) {
                    startX = x;
                    startY = y;
                    endX = x + w;
                    endY = y + h;
                    inParagraph = true;
                } else {
                    startX = Math.min(startX, x);
                    startY = Math.min(startY, y);
                    endX = Math.max(endX, x + w);
                    endY = Math.max(endY, y + h);
                }
            }

            currentParagraph.append(text).append(" ");
        }

        @Override
        protected void writeParagraphEnd() throws IOException {
            flushParagraph();
            super.writeParagraphEnd();
        }

        private void flushParagraph() {
            if (inParagraph && !currentParagraph.isEmpty()) {
                String str = currentParagraph.toString().trim();
                if (!str.isEmpty()) {
                    boolean isHeading = str.length() < 100 && (str.matches("^[0-9]+(\\.[0-9]+)*\\s+.*") || Character.isUpperCase(str.charAt(0)));
                    boolean isCaption = str.toLowerCase().startsWith("fig") || str.toLowerCase().startsWith("table");
                    blocks.add(new ExtractedTextBlock(
                            str,
                            new BoundingBox(startX, startY, Math.max(endX - startX, 10), Math.max(endY - startY, 10)),
                            blockIdx++,
                            isHeading,
                            isCaption,
                            false
                    ));
                }
                currentParagraph.setLength(0);
                inParagraph = false;
            }
        }
    }
}

