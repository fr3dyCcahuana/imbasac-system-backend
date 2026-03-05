package com.paulfernandosr.possystembackend.contracts.domain.exception;

public class InvalidContractException extends RuntimeException {
    public InvalidContractException(String message) {
        super(message);
    }
}
