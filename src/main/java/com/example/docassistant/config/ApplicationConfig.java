package com.example.docassistant.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * Application-level configuration class for beans and component customization.
 * Note: VectorStore is auto-configured as PgVectorStore via spring-ai-starter-vector-store-pgvector
 * using properties defined under spring.ai.vectorstore.pgvector.* in application.yml.
 */
@Configuration
public class ApplicationConfig {

    private static final Logger log = LoggerFactory.getLogger(ApplicationConfig.class);

}
