package com.example.docassistant.service;

import com.example.docassistant.dto.DocumentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface DocumentService {

    DocumentResponse uploadDocument(MultipartFile file);

    DocumentResponse getDocument(UUID id);

    Page<DocumentResponse> listDocuments(Pageable pageable);
}
