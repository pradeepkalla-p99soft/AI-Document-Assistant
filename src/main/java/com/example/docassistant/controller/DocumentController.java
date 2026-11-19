package com.example.docassistant.controller;

import com.example.docassistant.dto.AskQuestionRequest;
import com.example.docassistant.dto.AskQuestionResponse;
import com.example.docassistant.dto.DocumentResponse;
import com.example.docassistant.security.UserPrincipal;
import com.example.docassistant.service.DocumentService;
import com.example.docassistant.service.rag.QuestionAnsweringService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final QuestionAnsweringService questionAnsweringService;

    public DocumentController(DocumentService documentService, QuestionAnsweringService questionAnsweringService) {
        this.documentService = documentService;
        this.questionAnsweringService = questionAnsweringService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal user) {
        DocumentResponse response = documentService.uploadDocument(file, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocument(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal UserPrincipal user) {
        DocumentResponse response = documentService.getDocument(id, user.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<DocumentResponse>> listDocuments(
            @AuthenticationPrincipal UserPrincipal user,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<DocumentResponse> response = documentService.listDocuments(user.getId(), pageable);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{documentId}/ask")
    public ResponseEntity<AskQuestionResponse> askQuestion(
            @PathVariable("documentId") UUID documentId,
            @Valid @RequestBody AskQuestionRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        // Ownership check: throws DocumentNotFoundException (404) if document doesn't belong to this user
        documentService.getDocument(documentId, user.getId());
        AskQuestionResponse response = questionAnsweringService.askQuestion(documentId, request);
        return ResponseEntity.ok(response);
    }
}
