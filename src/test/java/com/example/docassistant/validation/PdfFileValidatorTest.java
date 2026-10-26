package com.example.docassistant.validation;

import com.example.docassistant.exception.InvalidFileException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfFileValidatorTest {

    private PdfFileValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PdfFileValidator("10MB");
    }

    @Test
    @DisplayName("validate accepts valid PDF file with signature")
    void validate_validPdf_success() {
        byte[] pdfContent = "%PDF-1.7\nSample content...".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.pdf",
                "application/pdf",
                pdfContent
        );

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validate rejects null file")
    void validate_nullFile_throwsException() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("missing or empty");
    }

    @Test
    @DisplayName("validate rejects empty file")
    void validate_emptyFile_throwsException() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.pdf",
                "application/pdf",
                new byte[0]
        );

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("missing or empty");
    }

    @Test
    @DisplayName("validate rejects file without .pdf extension")
    void validate_nonPdfExtension_throwsException() {
        byte[] pdfContent = "%PDF-1.4\nSome text".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.docx",
                "application/pdf",
                pdfContent
        );

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining(".pdf extension");
    }

    @Test
    @DisplayName("validate rejects non-PDF content even if extension is .pdf")
    void validate_fakePdfContent_throwsException() {
        byte[] fakeContent = "This is a plain text file pretending to be pdf".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "malicious.pdf",
                "application/pdf",
                fakeContent
        );

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("invalid file signature");
    }

    @Test
    @DisplayName("validate rejects file exceeding configured size")
    void validate_exceedingSize_throwsException() {
        PdfFileValidator strictValidator = new PdfFileValidator("10B");
        byte[] pdfContent = "%PDF-1.7\nThis content is longer than ten bytes".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.pdf",
                "application/pdf",
                pdfContent
        );

        assertThatThrownBy(() -> strictValidator.validate(file))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("exceeds the maximum allowed size");
    }
}
