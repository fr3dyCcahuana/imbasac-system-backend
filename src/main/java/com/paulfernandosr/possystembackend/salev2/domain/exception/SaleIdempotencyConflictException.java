package com.paulfernandosr.possystembackend.salev2.domain.exception;

public class SaleIdempotencyConflictException extends RuntimeException {
    public SaleIdempotencyConflictException(String message) {
        super(message);
    }
}
