package com.example.docassistant.service.rag;

import org.springframework.ai.document.Document;

import java.util.List;

public interface AnswerGenerationService {

    /**
     * Generates a grounded answer for the user question using the provided context chunks.
     *
     * @param question the user question
     * @param contextChunks the retrieved context chunks
     * @return the generated answer string
     */
    String generateAnswer(String question, List<Document> contextChunks);
}
