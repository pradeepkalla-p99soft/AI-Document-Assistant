package com.example.docassistant.service.extraction;

import com.example.docassistant.exception.PdfExtractionException;
import com.example.docassistant.storage.DocumentStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PagePdfDocumentExtractor implements DocumentContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(PagePdfDocumentExtractor.class);

    private final DocumentStorage documentStorage;

    public PagePdfDocumentExtractor(DocumentStorage documentStorage) {
        this.documentStorage = documentStorage;
    }

    @Override
    public List<Document> extract(String storageKey) {
        Resource pdfResource = documentStorage.loadAsResource(storageKey);

        PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                .withPageTopMargin(0)
                .withPageBottomMargin(0)
                .withPagesPerDocument(1)
                .build();

        List<Document> documents;
        try {
            // Constructor may throw if the PDF is corrupt or unparseable
            PagePdfDocumentReader reader = new PagePdfDocumentReader(pdfResource, config);
            documents = reader.get();
        } catch (Exception ex) {
            log.error("Failed to read or parse PDF from storage key {}: {}", storageKey, ex.getMessage());
            throw new PdfExtractionException("Unable to parse PDF document: " + ex.getMessage(), ex);
        }

        // Verify that at least one page contains non-blank text
        boolean hasUsableText = documents != null && documents.stream()
                .anyMatch(doc -> doc.getText() != null && !doc.getText().isBlank());

        if (!hasUsableText) {
            throw new PdfExtractionException("PDF produces no usable extracted text (may be scanned or empty without OCR)");
        }

        log.debug("Successfully extracted {} page(s) from document {}", documents.size(), storageKey);
        return documents;
    }
}
