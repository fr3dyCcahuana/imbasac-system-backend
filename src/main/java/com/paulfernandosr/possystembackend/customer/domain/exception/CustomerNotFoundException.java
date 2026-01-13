package com.paulfernandosr.possystembackend.customer.domain.exception;

import com.paulfernandosr.possystembackend.common.domain.exception.DomainException;

public class CustomerNotFoundException extends DomainException {
    public static final String ERROR_MESSAGE = "customer.not.found.message";

    public CustomerNotFoundException(String message) {
        super(message);
    }

    @Override
    public String getErrorMessage() {
        return ERROR_MESSAGE;
    }
}
