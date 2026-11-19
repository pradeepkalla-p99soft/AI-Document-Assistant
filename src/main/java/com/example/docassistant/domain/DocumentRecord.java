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
@Table(name = "documents")
public class DocumentRecord {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "filename", nullable = false, length = 255)
    private String filename;

    @Column(name = "storage_key", nullable = false, unique = true, length = 255)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private DocumentStatus status;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected DocumentRecord() {
        // JPA required default constructor
    }

    public DocumentRecord(UUID id, String filename, String storageKey, String contentType,
                          long sizeBytes, DocumentStatus status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this(id, filename, storageKey, contentType, sizeBytes, status, null, createdAt, updatedAt);
    }

    public DocumentRecord(UUID id, String filename, String storageKey, String contentType,
                          long sizeBytes, DocumentStatus status, UUID ownerId, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.filename = Objects.requireNonNull(filename, "filename must not be null");
        this.storageKey = Objects.requireNonNull(storageKey, "storageKey must not be null");
        this.contentType = Objects.requireNonNull(contentType, "contentType must not be null");
        this.sizeBytes = sizeBytes;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.ownerId = ownerId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static DocumentRecord createUploaded(String filename, String storageKey, String contentType, long sizeBytes) {
        return createUploaded(filename, storageKey, contentType, sizeBytes, null);
    }

    public static DocumentRecord createUploaded(String filename, String storageKey, String contentType, long sizeBytes, UUID ownerId) {
        OffsetDateTime now = OffsetDateTime.now();
        return new DocumentRecord(
                UUID.randomUUID(),
                filename,
                storageKey,
                contentType,
                sizeBytes,
                DocumentStatus.UPLOADED,
                ownerId,
                now,
                now
        );
    }

    public UUID getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void updateStatus(DocumentStatus status, String errorMessage) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.errorMessage = errorMessage;
        this.updatedAt = OffsetDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DocumentRecord that = (DocumentRecord) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
