package com.paulfernandosr.possystembackend.sale.domain.exception;

public class InvalidSaleException extends RuntimeException {
    public InvalidSaleException(String message) {
        super(message);
    }
}
