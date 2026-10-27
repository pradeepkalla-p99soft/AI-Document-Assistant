package com.example.docassistant.service.rag;

import com.example.docassistant.dto.AskQuestionRequest;
import com.example.docassistant.dto.AskQuestionResponse;

import java.util.UUID;

public interface QuestionAnsweringService {

    /**
     * Answers a user question grounded on a specific uploaded document.
     *
     * @param documentId the UUID of the target document
     * @param request    the question request DTO
     * @return structured response containing the answer, source references, and context indicator
     */
    AskQuestionResponse askQuestion(UUID documentId, AskQuestionRequest request);
}
