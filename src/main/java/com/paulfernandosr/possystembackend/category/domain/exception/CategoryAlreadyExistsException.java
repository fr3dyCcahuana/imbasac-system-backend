package com.paulfernandosr.possystembackend.category.domain.exception;

import com.paulfernandosr.possystembackend.common.domain.exception.DomainException;
import lombok.Getter;

@Getter
public class CategoryAlreadyExistsException extends DomainException {
    public static final String ERROR_MESSAGE = "category.exists.message";

    public CategoryAlreadyExistsException(String message) {
        super(message);
    }

    @Override
    public String getErrorMessage() {
        return ERROR_MESSAGE;
    }
}
