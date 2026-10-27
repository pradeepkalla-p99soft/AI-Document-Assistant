package com.example.docassistant.service.extraction;

import com.example.docassistant.exception.PdfExtractionException;
import com.example.docassistant.storage.DocumentStorage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PagePdfDocumentExtractorTest {

    @Mock
    private DocumentStorage documentStorage;

    @TempDir
    Path tempDir;

    private PagePdfDocumentExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new PagePdfDocumentExtractor(documentStorage);
    }

    @Test
    @DisplayName("extract reads pages with text and metadata from PDF")
    void extract_validPdfWithText_success() throws IOException {
        String key = "test-doc.pdf";
        byte[] pdfBytes = createPdfWithText("Page 1 Content text", "Page 2 Content text");

        given(documentStorage.loadAsResource(key)).willReturn(new ByteArrayResource(pdfBytes));

        List<Document> documents = extractor.extract(key);

        assertThat(documents).hasSize(2);
        assertThat(documents.get(0).getText()).containsIgnoringWhitespaces("Page 1 Content text");
        assertThat(documents.get(1).getText()).containsIgnoringWhitespaces("Page 2 Content text");
    }

    @Test
    @DisplayName("extract rejects PDF when pages contain no extractable text")
    void extract_pdfWithoutText_throwsException() throws IOException {
        String key = "empty-pages.pdf";
        byte[] pdfBytes = createEmptyPdfWithPages(1);

        given(documentStorage.loadAsResource(key)).willReturn(new ByteArrayResource(pdfBytes));

        assertThatThrownBy(() -> extractor.extract(key))
                .isInstanceOf(PdfExtractionException.class)
                .hasMessageContaining("no usable extracted text");
    }

    @Test
    @DisplayName("extract throws PdfExtractionException on corrupt or invalid PDF")
    void extract_corruptPdf_throwsException() {
        String key = "corrupt.pdf";
        given(documentStorage.loadAsResource(key))
                .willReturn(new ByteArrayResource("not really a pdf".getBytes()));

        assertThatThrownBy(() -> extractor.extract(key))
                .isInstanceOf(PdfExtractionException.class)
                .hasMessageContaining("Unable to parse PDF document");
    }

    private byte[] createPdfWithText(String... pageTexts) throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String text : pageTexts) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                    stream.beginText();
                    stream.setFont(font, 12);
                    stream.newLineAtOffset(100, 700);
                    stream.showText(text);
                    stream.endText();
                }
            }
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createEmptyPdfWithPages(int numPages) throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            for (int i = 0; i < numPages; i++) {
                doc.addPage(new PDPage());
            }
            doc.save(baos);
            return baos.toByteArray();
        }
    }
}
