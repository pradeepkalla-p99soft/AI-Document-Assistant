package com.example.docassistant.service;

import com.example.docassistant.dto.DocumentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface DocumentService {

    DocumentResponse uploadDocument(MultipartFile file);

    DocumentResponse uploadDocument(MultipartFile file, UUID ownerId);

    DocumentResponse getDocument(UUID id);

    DocumentResponse getDocument(UUID id, UUID ownerId);

    Page<DocumentResponse> listDocuments(Pageable pageable);

    Page<DocumentResponse> listDocuments(UUID ownerId, Pageable pageable);
}
