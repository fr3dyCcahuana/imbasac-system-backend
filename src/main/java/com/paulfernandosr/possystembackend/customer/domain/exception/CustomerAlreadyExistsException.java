package com.paulfernandosr.possystembackend.customer.domain.exception;

import com.paulfernandosr.possystembackend.common.domain.exception.DomainException;
import lombok.Getter;

@Getter
public class CustomerAlreadyExistsException extends DomainException {
    public static final String ERROR_MESSAGE = "customer.exists.message";

    public CustomerAlreadyExistsException(String message) {
        super(message);
    }

    @Override
    public String getErrorMessage() {
        return ERROR_MESSAGE;
    }
}
