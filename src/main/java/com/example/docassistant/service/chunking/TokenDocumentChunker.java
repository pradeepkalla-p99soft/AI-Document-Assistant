package com.example.docassistant.service.chunking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class TokenDocumentChunker implements DocumentChunker {

    private static final Logger log = LoggerFactory.getLogger(TokenDocumentChunker.class);

    // Standard metadata keys attached to chunks
    public static final String METADATA_DOCUMENT_ID = "documentId";
    public static final String METADATA_FILENAME = "filename";
    public static final String METADATA_PAGE_NUMBER = "pageNumber";
    public static final String METADATA_CHUNK_INDEX = "chunkIndex";

    private final TokenTextSplitter tokenTextSplitter;

    public TokenDocumentChunker(
            @Value("${storage.chunking.target-chunk-tokens:800}") int targetChunkTokens,
            @Value("${storage.chunking.min-chunk-chars:200}") int minChunkSizeChars,
            @Value("${storage.chunking.min-chunk-length-to-embed:5}") int minChunkLengthToEmbed) {

        this.tokenTextSplitter = TokenTextSplitter.builder()
                .withChunkSize(targetChunkTokens)
                .withMinChunkSizeChars(minChunkSizeChars)
                .withMinChunkLengthToEmbed(minChunkLengthToEmbed)
                .build();
    }

    @Override
    public List<Document> chunk(List<Document> extractedPages, UUID documentId, String filename) {
        List<Document> chunkedDocuments = new ArrayList<>();
        int globalChunkIndex = 0;

        for (Document pageDoc : extractedPages) {
            String pageText = pageDoc.getText();
            if (pageText == null || pageText.isBlank()) {
                continue;
            }

            // Normalize page number from PagePdfDocumentReader metadata
            int pageNumber = extractPageNumber(pageDoc.getMetadata());

            // Split the page document using token text splitter
            List<Document> pageChunks = tokenTextSplitter.split(List.of(pageDoc));

            for (Document chunk : pageChunks) {
                String chunkText = chunk.getText();
                if (chunkText == null || chunkText.isBlank()) {
                    continue;
                }

                // Preserve original metadata and enrich with document-level traceability
                Map<String, Object> enrichedMetadata = new HashMap<>(chunk.getMetadata());
                enrichedMetadata.put(METADATA_DOCUMENT_ID, documentId.toString());
                enrichedMetadata.put(METADATA_FILENAME, filename);
                enrichedMetadata.put(METADATA_PAGE_NUMBER, pageNumber);
                enrichedMetadata.put(METADATA_CHUNK_INDEX, globalChunkIndex);

                // Deterministic chunk ID: documentId_chunkIndex
                String chunkId = String.format("%s_%d", documentId, globalChunkIndex);

                Document finalChunk = new Document(chunkId, chunkText, enrichedMetadata);
                chunkedDocuments.add(finalChunk);
                globalChunkIndex++;
            }
        }

        log.debug("Chunked document {} ({}) into {} chunks", documentId, filename, chunkedDocuments.size());
        return chunkedDocuments;
    }

    private int extractPageNumber(Map<String, Object> metadata) {
        if (metadata == null) {
            return 1;
        }

        // PagePdfDocumentReader typically provides PagePdfDocumentReader.METADATA_START_PAGE_NUMBER ("page_number")
        Object pageNumObj = metadata.get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER);
        if (pageNumObj == null) {
            pageNumObj = metadata.get("page_number");
        }
        if (pageNumObj == null) {
            pageNumObj = metadata.get("page");
        }

        if (pageNumObj instanceof Number number) {
            return number.intValue();
        } else if (pageNumObj instanceof String str) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException ignored) {
            }
        }

        return 1;
    }
}
