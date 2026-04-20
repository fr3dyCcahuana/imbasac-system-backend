package com.paulfernandosr.possystembackend.customer.domain.exception;

import com.paulfernandosr.possystembackend.common.domain.exception.DomainException;

public class CustomerAddressAlreadyExistsException extends DomainException {
    public static final String ERROR_MESSAGE = "customer.address.exists.message";

    public CustomerAddressAlreadyExistsException(String message) {
        super(message);
    }

    @Override
    public String getErrorMessage() {
        return ERROR_MESSAGE;
    }
}
