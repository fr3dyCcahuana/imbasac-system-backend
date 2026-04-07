package com.paulfernandosr.possystembackend.manualpdf.domain.exception;

public class ManualPdfDocumentNotFoundException extends RuntimeException {
    public ManualPdfDocumentNotFoundException(String message) {
        super(message);
    }
}
