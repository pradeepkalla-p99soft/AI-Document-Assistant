package com.example.docassistant.repository;

import com.example.docassistant.domain.DocumentRecord;
import com.example.docassistant.domain.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentRecord, UUID> {

    Optional<DocumentRecord> findByStorageKey(String storageKey);

    Page<DocumentRecord> findByStatus(DocumentStatus status, Pageable pageable);
}
