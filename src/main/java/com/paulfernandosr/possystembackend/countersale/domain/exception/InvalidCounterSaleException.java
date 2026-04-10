package com.paulfernandosr.possystembackend.countersale.domain.exception;

public class InvalidCounterSaleException extends RuntimeException {
    public InvalidCounterSaleException(String message) {
        super(message);
    }
}
