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
        Objects.requireNonNull(documentId, "Document ID must not be null");
        Objects.requireNonNull(request, "Request must not be null");

        // 1. Verify document exists
        DocumentRecord document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found with ID: " + documentId));

        // 2. Ensure document processing is in READY status
        if (document.getStatus() != DocumentStatus.READY) {
            throw new DocumentNotReadyException(
                    String.format("Document %s is not ready for question answering. Current status: %s",
                            documentId, document.getStatus()));
        }

        // 3. Retrieve relevant chunks scoped strictly to this document
        List<Document> relevantChunks = retrievalService.retrieveRelevantChunks(documentId, request.question());

        // 4. Handle insufficient evidence without calling the model unnecessarily
        if (relevantChunks.isEmpty()) {
            log.info("No relevant chunks retrieved above threshold for documentId: {}. Returning insufficient evidence.", documentId);
            return new AskQuestionResponse(
                    documentId,
                    AnswerGenerationServiceImpl.INSUFFICIENT_EVIDENCE_ANSWER,
                    List.of(),
                    false
            );
        }

        // 5. Build trustworthy source references from retrieved chunk metadata
        List<SourceReference> sources = buildSourceReferences(documentId, document.getFilename(), relevantChunks);

        // 6. Generate grounded answer
        String rawAnswer = answerGenerationService.generateAnswer(request.question(), relevantChunks);

        // 7. Sanitize/validate any [S#] citations in the answer against available sources
        String sanitizedAnswer = sanitizeCitations(rawAnswer, sources.size());

        return new AskQuestionResponse(
                documentId,
                sanitizedAnswer,
                sources,
                true
        );
    }

    private List<SourceReference> buildSourceReferences(UUID documentId, String defaultFilename, List<Document> chunks) {
        List<SourceReference> sources = new ArrayList<>();
        Set<String> seenExcerpts = new HashSet<>();

        for (Document chunk : chunks) {
            Object pageObj = chunk.getMetadata().get(TokenDocumentChunker.METADATA_PAGE_NUMBER);
            Integer pageNumber = null;
            if (pageObj instanceof Number num) {
                pageNumber = num.intValue();
            } else if (pageObj instanceof String str) {
                try {
                    pageNumber = Integer.parseInt(str);
                } catch (NumberFormatException ignored) {
                }
            }

            Object filenameObj = chunk.getMetadata().get(TokenDocumentChunker.METADATA_FILENAME);
            String filename = filenameObj != null ? filenameObj.toString() : defaultFilename;

            String text = chunk.getText() != null ? chunk.getText().trim() : "";
            String excerpt = text.length() <= MAX_EXCERPT_LENGTH
                    ? text
                    : text.substring(0, MAX_EXCERPT_LENGTH) + "...";

            // Deduplicate exact duplicate excerpts on the same page
            String dedupeKey = pageNumber + ":" + excerpt;
            if (!seenExcerpts.add(dedupeKey)) {
                continue;
            }

            sources.add(new SourceReference(documentId, filename, pageNumber, excerpt));
        }

        return sources;
    }

    private String sanitizeCitations(String answer, int maxSourceIndex) {
        if (answer == null) {
            return "";
        }
        Matcher matcher = CITATION_PATTERN.matcher(answer);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            int sourceNum = Integer.parseInt(matcher.group(1));
            if (sourceNum < 1 || sourceNum > maxSourceIndex) {
                // Unknown/fabricated citation marker - strip it out
                matcher.appendReplacement(sb, "");
            } else {
                matcher.appendReplacement(sb, matcher.group(0));
            }
        }
        matcher.appendTail(sb);
        return sb.toString().trim();
    }
}
