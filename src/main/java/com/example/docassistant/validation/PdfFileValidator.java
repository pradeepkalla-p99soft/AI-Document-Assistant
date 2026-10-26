package com.example.docassistant.validation;

import com.example.docassistant.exception.InvalidFileException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

@Component
public class PdfFileValidator {

    // Magic bytes for PDF: %PDF- (0x25, 0x50, 0x44, 0x46, 0x2D)
    private static final byte[] PDF_MAGIC_BYTES = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D};
    private static final String PDF_EXTENSION = ".pdf";

    private final long maxFileSizeBytes;

    public PdfFileValidator(@Value("${storage.upload.max-file-size:10MB}") String maxFileSize) {
        this.maxFileSizeBytes = DataSize.parse(maxFileSize).toBytes();
    }

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Upload file is missing or empty");
        }

        if (file.getSize() > maxFileSizeBytes) {
            throw new InvalidFileException(String.format("File size (%d bytes) exceeds the maximum allowed size (%d bytes)",
                    file.getSize(), maxFileSizeBytes));
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(PDF_EXTENSION)) {
            throw new InvalidFileException("File must have a .pdf extension");
        }

        // Validate PDF signature (magic bytes)
        validatePdfMagicBytes(file);
    }

    private void validatePdfMagicBytes(MultipartFile file) {
        byte[] header = new byte[PDF_MAGIC_BYTES.length];
        try (InputStream is = file.getInputStream()) {
            int bytesRead = is.readNBytes(header, 0, header.length);
            if (bytesRead < PDF_MAGIC_BYTES.length || !Arrays.equals(header, PDF_MAGIC_BYTES)) {
                throw new InvalidFileException("File content is not a valid PDF document (invalid file signature)");
            }
        } catch (IOException e) {
            throw new InvalidFileException("Unable to inspect file signature: " + e.getMessage());
        }
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }
}
