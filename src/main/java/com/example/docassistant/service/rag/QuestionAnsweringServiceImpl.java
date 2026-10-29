package com.example.docassistant.service.rag;

import com.example.docassistant.domain.DocumentRecord;
import com.example.docassistant.domain.DocumentStatus;
import com.example.docassistant.dto.AskQuestionRequest;
import com.example.docassistant.dto.AskQuestionResponse;
import com.example.docassistant.dto.SourceReference;
import com.example.docassistant.exception.DocumentNotFoundException;
import com.example.docassistant.exception.DocumentNotReadyException;
import com.example.docassistant.repository.DocumentRepository;
import com.example.docassistant.service.chunking.TokenDocumentChunker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class QuestionAnsweringServiceImpl implements QuestionAnsweringService {

    private static final Logger log = LoggerFactory.getLogger(QuestionAnsweringServiceImpl.class);

    private static final int MAX_EXCERPT_LENGTH = 200;
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[S(\\d+)\\]");

    private final DocumentRepository documentRepository;
    private final DocumentRetrievalService retrievalService;
    private final AnswerGenerationService answerGenerationService;

    public QuestionAnsweringServiceImpl(
            DocumentRepository documentRepository,
            DocumentRetrievalService retrievalService,
            AnswerGenerationService answerGenerationService) {
        this.documentRepository = documentRepository;
        this.retrievalService = retrievalService;
        this.answerGenerationService = answerGenerationService;
    }

    @Override
    @Transactional(readOnly = true)
    public AskQuestionResponse askQuestion(UUID documentId, AskQuestionRequest request) {
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        // 1. Validate document existence and readiness in PostgreSQL
        DocumentRecord documentRecord = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found with ID: " + documentId));

        if (documentRecord.getStatus() != DocumentStatus.READY) {
            log.warn("Query rejected for documentId={} because status is {}", documentId, documentRecord.getStatus());
            throw new DocumentNotReadyException("Document " + documentId + " is not ready for querying. Current status: " + documentRecord.getStatus().name());
        }

        // 2. Retrieve relevant context chunks (including image descriptions)
        List<Document> retrievedChunks = retrievalService.retrieveRelevantChunks(documentId, request.question());

        // 3. Fallback path if similarity search returns no qualifying chunks
        if (retrievedChunks.isEmpty()) {
            log.info("No qualifying context chunks retrieved for documentId={}", documentId);
            return new AskQuestionResponse(
                    documentId,
                    AnswerGenerationServiceImpl.INSUFFICIENT_EVIDENCE_ANSWER,
                    List.of(),
                    false
            );
        }

        // 4. Generate answer via LLM
        String answer = answerGenerationService.generateAnswer(request.question(), retrievedChunks);

        // 5. Build source citations
        List<SourceReference> sources = buildSourceReferences(
                documentId,
                documentRecord.getFilename(),
                answer,
                retrievedChunks
        );

        return new AskQuestionResponse(
                documentId,
                answer,
                sources,
                true
        );
    }

    private List<SourceReference> buildSourceReferences(
            UUID documentId,
            String filename,
            String answer,
            List<Document> contextChunks) {

        if (answer == null || answer.equals(AnswerGenerationServiceImpl.INSUFFICIENT_EVIDENCE_ANSWER)) {
            return List.of();
        }

        Set<Integer> citedSourceIndices = new HashSet<>();
        Matcher matcher = CITATION_PATTERN.matcher(answer);
        while (matcher.find()) {
            try {
                int index = Integer.parseInt(matcher.group(1));
                citedSourceIndices.add(index);
            } catch (NumberFormatException ignored) {
            }
        }

        List<SourceReference> references = new ArrayList<>();
        for (int i = 0; i < contextChunks.size(); i++) {
            int sourceIndex = i + 1;
            if (citedSourceIndices.isEmpty() || citedSourceIndices.contains(sourceIndex)) {
                Document chunk = contextChunks.get(i);
                Integer pageNumber = resolvePageNumber(chunk);
                String excerpt = truncateExcerpt(chunk.getText());

                String sourceType = "TEXT";
                UUID assetId = null;
                String caption = null;

                if (chunk.getMetadata() != null) {
                    Object typeObj = chunk.getMetadata().get(TokenDocumentChunker.METADATA_SOURCE_TYPE);
                    if (typeObj != null) sourceType = typeObj.toString();

                    Object assetObj = chunk.getMetadata().get(TokenDocumentChunker.METADATA_ASSET_ID);
                    if (assetObj != null) {
                        try { assetId = UUID.fromString(assetObj.toString()); } catch (Exception ignored) {}
                    }

                    Object capObj = chunk.getMetadata().get(TokenDocumentChunker.METADATA_CAPTION);
                    if (capObj != null) caption = capObj.toString();
                }

                references.add(new SourceReference(
                        documentId,
                        filename,
                        pageNumber,
                        excerpt,
                        sourceType,
                        assetId,
                        caption
                ));
            }
        }

        return references;
    }

    private Integer resolvePageNumber(Document chunk) {
        if (chunk == null || chunk.getMetadata() == null) {
            return null;
        }
        Object pageObj = chunk.getMetadata().get(TokenDocumentChunker.METADATA_PAGE_NUMBER);
        if (pageObj instanceof Number num) {
            return num.intValue();
        } else if (pageObj instanceof String str) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private String truncateExcerpt(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String clean = text.strip().replaceAll("\\s+", " ");
        if (clean.length() <= MAX_EXCERPT_LENGTH) {
            return clean;
        }
        return clean.substring(0, MAX_EXCERPT_LENGTH).stripTrailing() + "...";
    }
}
