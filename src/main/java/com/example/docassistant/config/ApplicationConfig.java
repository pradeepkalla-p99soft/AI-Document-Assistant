package com.example.docassistant.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * Application-level configuration class for beans and component customization.
 */
@Configuration
public class ApplicationConfig {

    private static final Logger log = LoggerFactory.getLogger(ApplicationConfig.class);

    @Value("${storage.vectorstore.file:uploads/vector_store.json}")
    private String vectorStoreFilePath;

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        log.info("Initializing persistent SimpleVectorStore at: {}", vectorStoreFilePath);
        File storeFile = new File(vectorStoreFilePath);
        File parentDir = storeFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        if (storeFile.exists() && storeFile.length() > 0) {
            try {
                vectorStore.load(storeFile);
                log.info("Loaded existing vector store data from: {}", storeFile.getAbsolutePath());
            } catch (Exception e) {
                log.warn("Could not load existing vector store file: {}. Starting with clean store.", e.getMessage());
            }
        }
        return vectorStore;
    }
}
