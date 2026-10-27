package com.example.docassistant.dto;

import java.util.List;
import java.util.UUID;

public record AskQuestionResponse(
        UUID documentId,
        String answer,
        List<SourceReference> sources,
        boolean contextSupplied
) {
}
