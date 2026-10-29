package com.example.docassistant.repository;

import com.example.docassistant.domain.VisualAssetRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VisualAssetRepository extends JpaRepository<VisualAssetRecord, UUID> {
    List<VisualAssetRecord> findByDocumentIdOrderByPageNumberAsc(UUID documentId);
    void deleteByDocumentId(UUID documentId);
}

