package com.example.docassistant.storage;

import com.example.docassistant.exception.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Component
public class LocalFileSystemVisualAssetStorage implements VisualAssetStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileSystemVisualAssetStorage.class);

    private final Path assetStorageRoot;

    public LocalFileSystemVisualAssetStorage(@Value("${storage.assets.root:uploads/assets}") String assetRoot) {
        this.assetStorageRoot = Paths.get(assetRoot).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.assetStorageRoot);
            log.info("Initialized visual asset storage directory: {}", this.assetStorageRoot);
        } catch (IOException e) {
            throw new StorageException("Failed to initialize visual asset storage directory: " + this.assetStorageRoot, e);
        }
    }

    @Override
    public String storeAsset(UUID documentId, UUID assetId, String extension, InputStream inputStream) {
        try {
            Path docDir = assetStorageRoot.resolve(documentId.toString()).normalize();
            if (!docDir.startsWith(assetStorageRoot)) {
                throw new StorageException("Path traversal attempt detected");
            }
            Files.createDirectories(docDir);

            String safeExt = (extension != null && extension.startsWith(".")) ? extension : (extension != null ? "." + extension : ".png");
            String filename = assetId.toString() + safeExt;
            Path targetFile = docDir.resolve(filename).normalize();
            if (!targetFile.startsWith(docDir)) {
                throw new StorageException("Path traversal attempt detected");
            }

            Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
            String relativeKey = documentId + "/" + filename;
            log.debug("Stored visual asset {} at key {}", assetId, relativeKey);
            return relativeKey;
        } catch (IOException e) {
            throw new StorageException("Failed to store visual asset: " + assetId, e);
        }
    }

    @Override
    public Resource loadAssetAsResource(String storageKey) {
        Path filePath = resolvePath(storageKey);
        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            throw new StorageException("Visual asset not found or unreadable: " + storageKey);
        }
        return new FileSystemResource(filePath);
    }

    @Override
    public byte[] loadAssetBytes(String storageKey) {
        Path filePath = resolvePath(storageKey);
        try {
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            throw new StorageException("Failed to read visual asset bytes: " + storageKey, e);
        }
    }

    @Override
    public void deleteAsset(String storageKey) {
        try {
            Path filePath = resolvePath(storageKey);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("Failed to delete asset at key {}: {}", storageKey, e.getMessage());
        }
    }

    @Override
    public void deleteAssetsForDocument(UUID documentId) {
        try {
            Path docDir = assetStorageRoot.resolve(documentId.toString()).normalize();
            if (Files.exists(docDir) && docDir.startsWith(assetStorageRoot)) {
                try (var stream = Files.walk(docDir)) {
                    stream.sorted((a, b) -> b.compareTo(a)) // reverse order to delete contents first
                            .forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException ignored) {}
                            });
                }
            }
        } catch (IOException e) {
            log.warn("Failed to delete visual assets folder for document {}: {}", documentId, e.getMessage());
        }
    }

    private Path resolvePath(String storageKey) {
        if (storageKey == null || storageKey.contains("..")) {
            throw new StorageException("Invalid storage key: " + storageKey);
        }
        Path path = assetStorageRoot.resolve(storageKey).normalize();
        if (!path.startsWith(assetStorageRoot)) {
            throw new StorageException("Access outside storage root denied: " + storageKey);
        }
        return path;
    }
}

