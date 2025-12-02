package com.paulfernandosr.possystembackend.common.domain.exception;

public abstract class DomainException extends RuntimeException {
    public DomainException(String message) {
        super(message);
    }

    public abstract String getErrorMessage();
}
