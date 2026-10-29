package com.example.docassistant.service.extraction.ocr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Service
public class TesseractCliOcrService implements OcrService {

    private static final Logger log = LoggerFactory.getLogger(TesseractCliOcrService.class);

    private final boolean ocrEnabled;
    private final String tesseractPath;

    public TesseractCliOcrService(
            @Value("${ocr.enabled:false}") boolean ocrEnabled,
            @Value("${ocr.tesseract.path:tesseract}") String tesseractPath) {
        this.ocrEnabled = ocrEnabled;
        this.tesseractPath = tesseractPath;
    }

    @Override
    public boolean isAvailable() {
        if (!ocrEnabled) {
            return false;
        }
        try {
            Process process = new ProcessBuilder(tesseractPath, "--version")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            log.debug("Tesseract executable check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String extractText(BufferedImage pageImage) {
        if (!isAvailable()) {
            throw new IllegalStateException("OCR service is not enabled or Tesseract executable is unavailable at '" + tesseractPath + "'");
        }
        Path tempIn = null;
        Path tempOutBase = null;
        try {
            tempIn = Files.createTempFile("ocr_page_", ".png");
            ImageIO.write(pageImage, "png", tempIn.toFile());

            tempOutBase = Files.createTempFile("ocr_result_", "");
            Files.deleteIfExists(tempOutBase); // Tesseract appends .txt

            Process process = new ProcessBuilder(
                    tesseractPath,
                    tempIn.toAbsolutePath().toString(),
                    tempOutBase.toAbsolutePath().toString()
            ).redirectErrorStream(true).start();

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Tesseract OCR process timed out");
            }
            if (process.exitValue() != 0) {
                throw new RuntimeException("Tesseract exited with error code: " + process.exitValue());
            }

            Path txtFile = Path.of(tempOutBase.toAbsolutePath() + ".txt");
            if (Files.exists(txtFile)) {
                String text = Files.readString(txtFile);
                Files.deleteIfExists(txtFile);
                return text;
            }
            return "";
        } catch (Exception e) {
            throw new RuntimeException("OCR extraction failed: " + e.getMessage(), e);
        } finally {
            if (tempIn != null) {
                try { Files.deleteIfExists(tempIn); } catch (Exception ignored) {}
            }
        }
    }
}

