package com.example.docassistant.service.vision;

import com.example.docassistant.domain.VisualAssetRecord;
import org.springframework.core.io.Resource;

public interface ImageUnderstandingService {
    boolean isVisionModelConfigured();
    VisualAnalysisDescription analyzeImage(VisualAssetRecord asset, Resource imageResource, String surroundingContext);
}

