package com.example.docassistant.storage;

import org.springframework.core.io.Resource;

import java.io.InputStream;

/**
 * Storage port abstraction for managing physical document files.
 */
public interface DocumentStorage {

    /**
     * Stores an input stream under the specified generated storage key.
     *
     * @param storageKey  application-generated safe storage key
     * @param inputStream the source content stream
     * @return the total bytes written
     */
    long store(String storageKey, InputStream inputStream);

    /**
     * Retrieves the stored file as a Spring Resource.
     *
     * @param storageKey application-generated safe storage key
     * @return Resource representing the file
     */
    Resource loadAsResource(String storageKey);

    /**
     * Deletes the stored file associated with the storage key.
     *
     * @param storageKey application-generated safe storage key
     * @return true if deleted, false if file did not exist
     */
    boolean delete(String storageKey);
}
