package com.example.docassistant.storage;

import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.util.UUID;

public interface VisualAssetStorage {
    String storeAsset(UUID documentId, UUID assetId, String extension, InputStream inputStream);
    Resource loadAssetAsResource(String storageKey);
    byte[] loadAssetBytes(String storageKey);
    void deleteAsset(String storageKey);
    void deleteAssetsForDocument(UUID documentId);
}

