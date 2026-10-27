package com.example.docassistant.service.chunking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TokenDocumentChunkerTest {

    private TokenDocumentChunker chunker;

    @BeforeEach
    void setUp() {
        // chunk target 100 tokens, min chunk chars 10
        chunker = new TokenDocumentChunker(100, 10, 5);
    }

    @Test
    @DisplayName("chunk preserves source metadata and normalizes page numbers")
    void chunk_preservesMetadataAndNormalizesPage() {
        UUID docId = UUID.randomUUID();
        String filename = "sample-report.pdf";

        Document page1 = new Document(
                "p1",
                "This is the content of the first page with enough words to be indexed.",
                Map.of("page_number", 1)
        );
        Document page2 = new Document(
                "p2",
                "This is the content of the second page with valuable information.",
                Map.of("page_number", 2)
        );

        List<Document> chunks = chunker.chunk(List.of(page1, page2), docId, filename);

        assertThat(chunks).isNotEmpty();

        // Check chunk 0
        Document firstChunk = chunks.get(0);
        assertThat(firstChunk.getId()).startsWith(docId.toString());
        assertThat(firstChunk.getMetadata())
                .containsEntry(TokenDocumentChunker.METADATA_DOCUMENT_ID, docId.toString())
                .containsEntry(TokenDocumentChunker.METADATA_FILENAME, filename)
                .containsEntry(TokenDocumentChunker.METADATA_PAGE_NUMBER, 1)
                .containsEntry(TokenDocumentChunker.METADATA_CHUNK_INDEX, 0);

        // Check chunk on page 2
        Document secondChunk = chunks.get(1);
        assertThat(secondChunk.getMetadata())
                .containsEntry(TokenDocumentChunker.METADATA_DOCUMENT_ID, docId.toString())
                .containsEntry(TokenDocumentChunker.METADATA_FILENAME, filename)
                .containsEntry(TokenDocumentChunker.METADATA_PAGE_NUMBER, 2)
                .containsEntry(TokenDocumentChunker.METADATA_CHUNK_INDEX, 1);
    }

    @Test
    @DisplayName("chunk handles null or string page number metadata safely")
    void chunk_handlesStringPageNumber() {
        UUID docId = UUID.randomUUID();
        Document page = new Document(
                "p1",
                "Valid text content that needs to be chunked into pieces.",
                Map.of("page_number", "3")
        );

        List<Document> chunks = chunker.chunk(List.of(page), docId, "doc.pdf");

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).getMetadata())
                .containsEntry(TokenDocumentChunker.METADATA_PAGE_NUMBER, 3);
    }
}
