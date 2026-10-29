package com.example.docassistant.service.vision;

import java.util.List;

public record VisualAnalysisDescription(
        String summary,
        List<String> keyObjects,
        List<String> relationships,
        List<String> visibleLabels,
        String chartOrTableDetails,
        String limitations
) {
    public String toSearchableText() {
        StringBuilder sb = new StringBuilder();
        if (summary != null && !summary.isBlank()) {
            sb.append("Visual Summary: ").append(summary).append("\n");
        }
        if (keyObjects != null && !keyObjects.isEmpty()) {
            sb.append("Key Elements: ").append(String.join(", ", keyObjects)).append("\n");
        }
        if (relationships != null && !relationships.isEmpty()) {
            sb.append("Flow/Relations: ").append(String.join("; ", relationships)).append("\n");
        }
        if (visibleLabels != null && !visibleLabels.isEmpty()) {
            sb.append("Visible Labels: ").append(String.join(", ", visibleLabels)).append("\n");
        }
        if (chartOrTableDetails != null && !chartOrTableDetails.isBlank()) {
            sb.append("Data/Trends: ").append(chartOrTableDetails).append("\n");
        }
        if (limitations != null && !limitations.isBlank()) {
            sb.append("Limitations: ").append(limitations).append("\n");
        }
        return sb.toString().trim();
    }
}

