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
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
public class LocalFileSystemDocumentStorage implements DocumentStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileSystemDocumentStorage.class);
    private static final Pattern SAFE_KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9]+)?$");

    private final Path rootLocation;

    public LocalFileSystemDocumentStorage(@Value("${storage.local.root:uploads/documents}") String rootDir) {
        Objects.requireNonNull(rootDir, "Root directory must not be null");
        this.rootLocation = Paths.get(rootDir).toAbsolutePath().normalize();
        init();
    }

    private void init() {
        try {
            Files.createDirectories(rootLocation);
            log.info("Initialized local document storage at: {}", rootLocation);
        } catch (IOException e) {
            throw new StorageException("Could not initialize local document storage directory: " + rootLocation, e);
        }
    }

    @Override
    public long store(String storageKey, InputStream inputStream) {
        validateStorageKey(storageKey);
        Path destinationFile = resolvePath(storageKey);

        try (OutputStream outputStream = Files.newOutputStream(
                destinationFile,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            long bytesWritten = inputStream.transferTo(outputStream);
            log.debug("Stored file with key: {}, size: {} bytes", storageKey, bytesWritten);
            return bytesWritten;
        } catch (IOException e) {
            // Attempt cleanup of incomplete file if created
            try {
                Files.deleteIfExists(destinationFile);
            } catch (IOException ignored) {
            }
            throw new StorageException("Failed to store document file with key: " + storageKey, e);
        }
    }

    @Override
    public Resource loadAsResource(String storageKey) {
        validateStorageKey(storageKey);
        Path file = resolvePath(storageKey);

        if (!Files.exists(file) || !Files.isReadable(file)) {
            throw new StorageException("Stored document not found or unreadable: " + storageKey);
        }

        return new FileSystemResource(file);
    }

    @Override
    public boolean delete(String storageKey) {
        validateStorageKey(storageKey);
        Path file = resolvePath(storageKey);

        try {
            boolean deleted = Files.deleteIfExists(file);
            log.debug("Deleted file for key: {}, result: {}", storageKey, deleted);
            return deleted;
        } catch (IOException e) {
            throw new StorageException("Failed to delete stored document file: " + storageKey, e);
        }
    }

    private Path resolvePath(String storageKey) {
        Path resolved = rootLocation.resolve(storageKey).normalize();
        if (!resolved.startsWith(rootLocation)) {
            // Path traversal prevention check
            throw new StorageException("Cannot store or access file outside designated root directory: " + storageKey);
        }
        return resolved;
    }

    private void validateStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || !SAFE_KEY_PATTERN.matcher(storageKey).matches()) {
            throw new StorageException("Invalid storage key format: " + storageKey);
        }
    }

    public Path getRootLocation() {
        return rootLocation;
    }
}
