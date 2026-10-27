package com.example.docassistant.exception;

public class DocumentNotReadyException extends RuntimeException {

    public DocumentNotReadyException(String message) {
        super(message);
    }
}
