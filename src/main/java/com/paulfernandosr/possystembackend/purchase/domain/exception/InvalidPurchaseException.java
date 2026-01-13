package com.paulfernandosr.possystembackend.purchase.domain.exception;

public class InvalidPurchaseException extends RuntimeException {
    public InvalidPurchaseException(String message) {
        super(message);
    }
}
