package com.example.docassistant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.example.docassistant.repository.UserRepository;

/**
 * Smoke test: verifies the Spring application context loads without a live PostgreSQL
 * or Ollama instance. VectorStore and EmbeddingModel are mocked so that
 * PgVectorStoreAutoConfiguration does not attempt a real JDBC connection.
 */
@SpringBootTest
class DocAssistantApplicationTests {

    /** Prevents PgVectorStore from attempting a real PostgreSQL connection during tests. */
    @MockitoBean
    VectorStore vectorStore;

    /** Prevents OllamaEmbeddingModel from attempting a real Ollama connection during tests. */
    @MockitoBean
    EmbeddingModel embeddingModel;

    /** Prevents CustomUserDetailsService from requiring a live DB connection during context load. */
    @MockitoBean
    UserRepository userRepository;

    @Test
    @DisplayName("Application context loads successfully without a live Ollama or PostgreSQL connection")
    void contextLoads() {
    }
}
