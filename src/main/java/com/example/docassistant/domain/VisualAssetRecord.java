package com.example.docassistant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "visual_assets")
public class VisualAssetRecord {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 50)
    private VisualAssetType assetType;

    @Column(name = "storage_key", nullable = false, unique = true, length = 255)
    private String storageKey;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "caption", length = 2000)
    private String caption;

    @Column(name = "bounding_box", length = 255)
    private String boundingBox;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 50)
    private VisualAssetProcessingStatus processingStatus;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected VisualAssetRecord() {
    }

    public VisualAssetRecord(UUID id, UUID documentId, int pageNumber, VisualAssetType assetType,
                             String storageKey, String mimeType, String caption, String boundingBox,
                             VisualAssetProcessingStatus processingStatus, String description,
                             OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.documentId = Objects.requireNonNull(documentId, "documentId must not be null");
        this.pageNumber = pageNumber;
        this.assetType = Objects.requireNonNull(assetType, "assetType must not be null");
        this.storageKey = Objects.requireNonNull(storageKey, "storageKey must not be null");
        this.mimeType = Objects.requireNonNull(mimeType, "mimeType must not be null");
        this.caption = caption;
        this.boundingBox = boundingBox;
        this.processingStatus = Objects.requireNonNull(processingStatus, "processingStatus must not be null");
        this.description = description;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static VisualAssetRecord createNew(UUID documentId, int pageNumber, VisualAssetType assetType,
                                              String storageKey, String mimeType, String caption, String boundingBox) {
        OffsetDateTime now = OffsetDateTime.now();
        return new VisualAssetRecord(
                UUID.randomUUID(),
                documentId,
                pageNumber,
                assetType,
                storageKey,
                mimeType,
                caption,
                boundingBox,
                VisualAssetProcessingStatus.PENDING,
                null,
                now,
                now
        );
    }

    public void updateProcessing(VisualAssetProcessingStatus status, String description) {
        this.processingStatus = Objects.requireNonNull(status, "processingStatus must not be null");
        this.description = description;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public VisualAssetType getAssetType() {
        return assetType;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
        this.updatedAt = OffsetDateTime.now();
    }

    public String getBoundingBox() {
        return boundingBox;
    }

    public VisualAssetProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public String getDescription() {
        return description;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}

