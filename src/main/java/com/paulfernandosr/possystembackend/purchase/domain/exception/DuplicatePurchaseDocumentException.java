package com.paulfernandosr.possystembackend.purchase.domain.exception;

public class DuplicatePurchaseDocumentException extends RuntimeException {
    public DuplicatePurchaseDocumentException(String message) {
        super(message);
    }
}
