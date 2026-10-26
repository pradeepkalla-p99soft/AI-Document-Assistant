package com.example.docassistant.service;

import com.example.docassistant.domain.DocumentRecord;
import com.example.docassistant.domain.DocumentStatus;
import com.example.docassistant.dto.DocumentResponse;
import com.example.docassistant.exception.DocumentNotFoundException;
import com.example.docassistant.exception.InvalidFileException;
import com.example.docassistant.repository.DocumentRepository;
import com.example.docassistant.storage.DocumentStorage;
import com.example.docassistant.validation.PdfFileValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentStorage documentStorage;

    @Mock
    private PdfFileValidator pdfFileValidator;

    private DocumentServiceImpl documentService;

    @BeforeEach
    void setUp() {
        documentService = new DocumentServiceImpl(documentRepository, documentStorage, pdfFileValidator);
    }

    @Test
    @DisplayName("uploadDocument stores file and saves metadata record")
    void uploadDocument_success() {
        byte[] content = "%PDF-1.4 test".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "my-doc.pdf", "application/pdf", content);

        given(documentRepository.save(any(DocumentRecord.class))).willAnswer(invocation -> invocation.getArgument(0));

        DocumentResponse response = documentService.uploadDocument(file);

        assertThat(response).isNotNull();
        assertThat(response.filename()).isEqualTo("my-doc.pdf");
        assertThat(response.status()).isEqualTo(DocumentStatus.UPLOADED);
        assertThat(response.sizeBytes()).isEqualTo(content.length);

        verify(pdfFileValidator).validate(file);
        verify(documentStorage).store(anyString(), any(InputStream.class));
        verify(documentRepository).save(any(DocumentRecord.class));
    }

    @Test
    @DisplayName("uploadDocument performs compensating file deletion if database save fails")
    void uploadDocument_databaseFailure_cleansUpFile() {
        byte[] content = "%PDF-1.4 test".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "fail-doc.pdf", "application/pdf", content);

        given(documentRepository.save(any(DocumentRecord.class)))
                .willThrow(new RuntimeException("Database error"));

        assertThatThrownBy(() -> documentService.uploadDocument(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Database error");

        // Verify physical file cleanup was executed
        verify(documentStorage).delete(anyString());
    }

    @Test
    @DisplayName("uploadDocument propagates validation failure without saving")
    void uploadDocument_validationFailure_throwsException() {
        MockMultipartFile file = new MockMultipartFile("file", "bad.txt", "text/plain", new byte[0]);
        doThrow(new InvalidFileException("File must have a .pdf extension"))
                .when(pdfFileValidator).validate(file);

        assertThatThrownBy(() -> documentService.uploadDocument(file))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining(".pdf extension");
    }

    @Test
    @DisplayName("getDocument returns response when found")
    void getDocument_found() {
        UUID docId = UUID.randomUUID();
        DocumentRecord record = new DocumentRecord(
                docId,
                "report.pdf",
                "key-123.pdf",
                "application/pdf",
                1024L,
                DocumentStatus.UPLOADED,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        given(documentRepository.findById(docId)).willReturn(Optional.of(record));

        DocumentResponse response = documentService.getDocument(docId);

        assertThat(response.id()).isEqualTo(docId);
        assertThat(response.filename()).isEqualTo("report.pdf");
    }

    @Test
    @DisplayName("getDocument throws DocumentNotFoundException when ID does not exist")
    void getDocument_notFound_throwsException() {
        UUID docId = UUID.randomUUID();
        given(documentRepository.findById(docId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.getDocument(docId))
                .isInstanceOf(DocumentNotFoundException.class)
                .hasMessageContaining("Document not found with ID");
    }

    @Test
    @DisplayName("listDocuments returns paged responses")
    void listDocuments_returnsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        DocumentRecord record = new DocumentRecord(
                UUID.randomUUID(),
                "report.pdf",
                "key-123.pdf",
                "application/pdf",
                1024L,
                DocumentStatus.UPLOADED,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        Page<DocumentRecord> page = new PageImpl<>(List.of(record), pageable, 1);

        given(documentRepository.findAll(pageable)).willReturn(page);

        Page<DocumentResponse> result = documentService.listDocuments(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).filename()).isEqualTo("report.pdf");
    }
}
