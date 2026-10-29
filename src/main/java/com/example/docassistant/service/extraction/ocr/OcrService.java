package com.example.docassistant.service.extraction.ocr;

import java.awt.image.BufferedImage;

public interface OcrService {
    boolean isAvailable();
    String extractText(BufferedImage pageImage);
}

