package com.example.docassistant;

import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {PgVectorStoreAutoConfiguration.class})
public class DocAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocAssistantApplication.class, args);
    }
}
