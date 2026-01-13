package com.paulfernandosr.possystembackend.sale.domain.exception;

public class InvalidSaleSessionException extends RuntimeException {
    public InvalidSaleSessionException(String message) {
        super(message);
    }
}
