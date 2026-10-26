package com.example.docassistant.controller;

import com.example.docassistant.domain.DocumentStatus;
import com.example.docassistant.dto.DocumentResponse;
import com.example.docassistant.exception.DocumentNotFoundException;
import com.example.docassistant.exception.InvalidFileException;
import com.example.docassistant.service.DocumentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;

    @Test
    @DisplayName("POST /api/documents returns 201 with DocumentResponse on successful upload")
    void uploadDocument_success() throws Exception {
        UUID docId = UUID.randomUUID();
        DocumentResponse response = new DocumentResponse(
                docId,
                "test.pdf",
                "key-1.pdf",
                "application/pdf",
                500L,
                DocumentStatus.UPLOADED,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        given(documentService.uploadDocument(any())).willReturn(response);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.pdf",
                "application/pdf",
                "%PDF-1.4 sample content".getBytes()
        );

        mockMvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(docId.toString()))
                .andExpect(jsonPath("$.filename").value("test.pdf"))
                .andExpect(jsonPath("$.status").value("UPLOADED"));
    }

    @Test
    @DisplayName("POST /api/documents returns 400 when file validation fails")
    void uploadDocument_validationFailure_returnsBadRequest() throws Exception {
        given(documentService.uploadDocument(any()))
                .willThrow(new InvalidFileException("File content is not a valid PDF document"));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "bad.pdf",
                "application/pdf",
                "not-pdf".getBytes()
        );

        mockMvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid File Upload"))
                .andExpect(jsonPath("$.detail").value("File content is not a valid PDF document"));
    }

    @Test
    @DisplayName("GET /api/documents/{id} returns 200 with document details when found")
    void getDocument_found() throws Exception {
        UUID docId = UUID.randomUUID();
        DocumentResponse response = new DocumentResponse(
                docId,
                "sample.pdf",
                "key-2.pdf",
                "application/pdf",
                1024L,
                DocumentStatus.UPLOADED,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        given(documentService.getDocument(docId)).willReturn(response);

        mockMvc.perform(get("/api/documents/{id}", docId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(docId.toString()))
                .andExpect(jsonPath("$.filename").value("sample.pdf"));
    }

    @Test
    @DisplayName("GET /api/documents/{id} returns 404 when document not found")
    void getDocument_notFound() throws Exception {
        UUID docId = UUID.randomUUID();
        given(documentService.getDocument(docId))
                .willThrow(new DocumentNotFoundException("Document not found with ID: " + docId));

        mockMvc.perform(get("/api/documents/{id}", docId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Document Not Found"));
    }

    @Test
    @DisplayName("GET /api/documents returns 200 with paginated list")
    void listDocuments_returnsPage() throws Exception {
        UUID docId = UUID.randomUUID();
        DocumentResponse response = new DocumentResponse(
                docId,
                "sample.pdf",
                "key-3.pdf",
                "application/pdf",
                1024L,
                DocumentStatus.UPLOADED,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        given(documentService.listDocuments(any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(docId.toString()))
                .andExpect(jsonPath("$.content[0].filename").value("sample.pdf"));
    }
}
