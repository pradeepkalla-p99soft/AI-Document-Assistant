package com.example.docassistant.exception;

public class PdfExtractionException extends RuntimeException {

    public PdfExtractionException(String message) {
        super(message);
    }

    public PdfExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
